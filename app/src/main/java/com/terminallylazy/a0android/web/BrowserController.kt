package com.terminallylazy.a0android.web

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Message
import android.view.ViewGroup
import android.webkit.*
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.terminallylazy.a0android.BuildConfig
import com.terminallylazy.a0android.data.ServerProfile
import com.terminallylazy.a0android.files.DownloadCoordinator
import com.terminallylazy.a0android.files.TransferStatus
import kotlinx.coroutines.CoroutineScope
import java.io.ByteArrayInputStream

sealed interface PageProblem {
    data class Http(val status: Int) : PageProblem
    data object Tls : PageProblem
    data object Network : PageProblem
    data object Renderer : PageProblem
    data object Navigation : PageProblem
    data class Provider(val detail: String) : PageProblem
}

fun PageProblem.message(): String = when (this) {
    is PageProblem.Http -> "The server returned HTTP $status. Retry or check the server address."
    PageProblem.Tls -> "The server certificate could not be verified. Use a trusted HTTPS certificate."
    PageProblem.Network -> "Could not reach the server. Check your connection, server address, and local network access."
    PageProblem.Renderer -> "The web page stopped. Reload to reconnect."
    PageProblem.Navigation -> "An unexpected navigation was blocked. Open external links directly from the page."
    is PageProblem.Provider -> detail
}

data class BrowserState(val progress: Int = 0, val canGoBack: Boolean = false, val problem: PageProblem? = null)

// Both clients below handle renderer loss; AGP 9.4 lint misidentifies the anonymous Kotlin overrides.
@SuppressLint("SetJavaScriptEnabled", "MissingOnRenderProcessGone")
class BrowserController(
    context: Context,
    val profile: ServerProfile,
    scope: CoroutineScope,
    private val onState: (BrowserState) -> Unit,
    onTransfer: (TransferStatus) -> Unit,
    saveDocument: (String, String) -> Unit,
    private val selectFiles: (Array<String>, Boolean) -> Unit,
    private val requestAudio: () -> Unit,
    savedState: Bundle? = null,
) {
    val view = WebView(context).apply {
        // Compose's default wrap-content parameters make Chromium resolve vh/dvh to zero.
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
    private var state = BrowserState()
    private var destroyed = false
    private var foreground = true
    private var documentVisible = false
    private var permission: PermissionRequest? = null
    private var chooser: ValueCallback<Array<Uri>>? = null
    private var chooserOutstanding = false
    private val popups = mutableSetOf<WebView>()
    private val cookieManager: CookieManager
    val downloads: DownloadCoordinator

    init {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        cookieManager = try { BrowserProfiles.bind(context, view, profile) }
        catch (failure: IllegalStateException) { view.destroy(); throw failure }
        downloads = DownloadCoordinator(context, scope, profile.origin,
            { cookieManager.getCookie(it).orEmpty() }, { view.settings.userAgentString }, saveDocument, onTransfer)
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(view, false)
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            safeBrowsingEnabled = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
            userAgentString = "$userAgentString A0Android/${BuildConfig.VERSION_NAME}"
        }
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return !profile.origin.contains(request.url.toString())
                return navigate(request.url.toString(), request.hasGesture())
            }
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                documentVisible = false
                cancelInteractions()
                downloads.pageStarted()
                if (!profile.origin.contains(url)) { view.stopLoading(); update(state.copy(problem = PageProblem.Navigation)); return }
                update(state.copy(progress = 0, problem = null))
            }
            override fun onPageCommitVisible(view: WebView, url: String) {
                if (profile.origin.contains(url)) {
                    downloads.pageCommitted()
                    documentVisible = true
                    update(state.copy(progress = 100))
                }
            }
            override fun onPageFinished(view: WebView, url: String) {
                update(state.copy(progress = 100, canGoBack = view.canGoBack()))
            }
            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                update(state.copy(canGoBack = view.canGoBack()))
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) update(state.copy(problem = PageProblem.Network))
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame) update(state.copy(problem = PageProblem.Http(response.statusCode)))
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                if (error.url == view.url || profile.origin.contains(error.url)) update(state.copy(problem = PageProblem.Tls))
            }
            override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) {
                handler.cancel() // Native HTTP-basic/proxy credentials are outside v1.
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                update(state.copy(problem = PageProblem.Renderer))
                dispose()
                return true
            }
        }
        view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, progress: Int) { if (!documentVisible) update(state.copy(progress = progress)) }
            override fun onConsoleMessage(message: ConsoleMessage): Boolean = true
            override fun onPermissionRequest(request: PermissionRequest) {
                permission?.deny(); permission = null
                if (destroyed || !foreground || !profile.origin.contains(request.origin.toString()) ||
                    !profile.origin.contains(view.url.orEmpty()) ||
                    request.resources.any { it != PermissionRequest.RESOURCE_AUDIO_CAPTURE }) {
                    request.deny(); return
                }
                permission = request
                requestAudio()
            }
            override fun onPermissionRequestCanceled(request: PermissionRequest) {
                if (permission === request) permission = null
            }
            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                if (!foreground || !profile.origin.contains(view.url.orEmpty())) { callback.onReceiveValue(null); return true }
                if (chooserOutstanding) { callback.onReceiveValue(null); return true }
                chooser?.onReceiveValue(null)
                chooser = callback; chooserOutstanding = true
                val types = params.acceptTypes.flatMap { it.split(',') }.map { it.trim() }
                    .filter { it.contains('/') }.toTypedArray().ifEmpty { arrayOf("*/*") }
                selectFiles(types, params.mode == FileChooserParams.MODE_OPEN_MULTIPLE)
                return true
            }
            override fun onCreateWindow(view: WebView, dialog: Boolean, userGesture: Boolean, result: Message): Boolean {
                if (!userGesture) return false
                val popup = WebView(context)
                popups.add(popup)
                popup.settings.allowFileAccess = false
                popup.settings.allowContentAccess = false
                popup.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                        val target = request.url.toString()
                        if (!navigate(target, true)) this@BrowserController.view.loadUrl(target)
                        v.post { closePopup(v) }
                        return true
                    }
                    override fun onPageFinished(v: WebView, url: String) { closePopup(v) }
                    override fun onRenderProcessGone(v: WebView, detail: RenderProcessGoneDetail): Boolean {
                        closePopup(v)
                        return true
                    }
                    override fun shouldInterceptRequest(v: WebView, request: WebResourceRequest) =
                        WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                }
                val transport = result.obj as? WebView.WebViewTransport ?: run { closePopup(popup); return false }
                transport.webView = popup; result.sendToTarget()
                return true
            }
        }
        view.setDownloadListener { url, _, disposition, mime, _ ->
            val name = URLUtil.guessFileName(url, disposition, mime)
            downloads.direct(url, name, mime.orEmpty())
        }
        if (BrowserProfiles.supportsDownloads()) {
            WebViewCompat.addWebMessageListener(view, "A0Downloads", setOf(profile.origin.value)) { _, message, source, main, reply ->
                message.data?.let { downloads.receive(it, source, main, reply) }
            }
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(view, context.assets.open("download-adapter.js").bufferedReader().use { it.readText() }, setOf(profile.origin.value))
                WebViewCompat.addDocumentStartJavaScript(view, context.assets.open("mobile-layout.js").bufferedReader().use { it.readText() }, setOf(profile.origin.value))
                WebViewCompat.addDocumentStartJavaScript(view, context.assets.open("sync-recovery.js").bufferedReader().use { it.readText() }, setOf(profile.origin.value))
            }
            val restored = savedState?.takeIf { it.getString("a0_origin") == profile.origin.value && it.getString("a0_profile") == profile.id }
                ?.getBundle("web")?.let { view.restoreState(it) }
            if (restored == null || !profile.origin.contains(view.url.orEmpty())) view.loadUrl(profile.origin.root)
        } else {
            update(state.copy(problem = PageProblem.Provider("Update Android System WebView to enable secure file downloads.")))
        }
    }

    private fun navigate(url: String, gesture: Boolean): Boolean = when (val decision = profile.origin.navigation(url, gesture)) {
        NavigationDecision.Internal -> false
        is NavigationDecision.External -> {
            try { view.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(decision.url))) }
            catch (_: android.content.ActivityNotFoundException) { update(state.copy(problem = PageProblem.Navigation)) }
            true
        }
        NavigationDecision.Blocked -> { update(state.copy(problem = PageProblem.Navigation)); true }
    }

    fun audioPermissionResult(granted: Boolean) {
        val request = permission ?: return
        permission = null
        if (!destroyed && granted && ContextCompat.checkSelfPermission(view.context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
            profile.origin.contains(request.origin.toString()) && profile.origin.contains(view.url.orEmpty())) {
            request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
        } else request.deny()
    }
    fun filesSelected(uris: List<Uri>) {
        chooserOutstanding = false
        val callback = chooser ?: return
        chooser = null
        val valid = uris.filter { uri -> uri.scheme == "content" && try {
            view.context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (_: Exception) { false } }
        callback.onReceiveValue(valid.takeIf { it.isNotEmpty() }?.toTypedArray())
    }
    fun cancelInteractions() { permission?.deny(); permission = null; chooser?.onReceiveValue(null); chooser = null }
    fun pause(preservePending: Boolean = false) {
        if (destroyed) return
        foreground = false
        if (!preservePending) cancelInteractions()
        // Stop capture explicitly; WebView.onPause alone does not promise to stop audio capture.
        view.evaluateJavascript("window.dispatchEvent(new Event('a0-app-background'));", null)
        view.onPause(); cookieManager.flush()
    }
    fun resume() {
        foreground = true
        if (!destroyed) {
            view.onResume()
            view.evaluateJavascript("window.dispatchEvent(new Event('a0-app-foreground'));", null)
        }
    }
    fun goBack() { if (!destroyed && view.canGoBack()) view.goBack() }
    fun handleBack(onUnhandled: () -> Unit) {
        if (destroyed) { onUnhandled(); return }
        view.evaluateJavascript("window.__a0MobileBack?.() === true") { handled ->
            if (!destroyed && handled != "true") onUnhandled()
        }
    }
    fun saveState(): Bundle = Bundle().apply {
        putString("a0_origin", profile.origin.value)
        putString("a0_profile", profile.id)
        if (!destroyed) putBundle("web", Bundle().also { view.saveState(it) })
    }
    fun dispose() {
        if (destroyed) return
        cancelInteractions(); downloads.cancel(silent = true); cookieManager.flush()
        destroyed = true
        popups.toList().forEach(::closePopup)
        (view.parent as? ViewGroup)?.removeView(view)
        view.stopLoading(); view.destroy()
    }
    private fun closePopup(popup: WebView) { if (popups.remove(popup)) popup.destroy() }
    private fun update(next: BrowserState) { state = next; onState(next) }
}
