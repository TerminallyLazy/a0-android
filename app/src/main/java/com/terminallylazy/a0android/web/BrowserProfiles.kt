package com.terminallylazy.a0android.web

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebStorageCompat
import com.terminallylazy.a0android.data.ServerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

object BrowserProfiles {
    fun hasProvider() = WebView.getCurrentWebViewPackage() != null
    // AndroidX feature discovery initializes the provider; it can throw when none is enabled.
    fun supportsMultiple() = hasProvider() && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
    fun supportsDownloads() = hasProvider() && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
        WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    fun bind(context: Context, view: WebView, profile: ServerProfile): CookieManager {
        if (supportsMultiple()) {
            WebViewCompat.setProfile(view, profile.browserId)
            return WebViewCompat.getProfile(view).cookieManager
        }
        val legacy = context.getSharedPreferences("legacy_browser_boundary", Context.MODE_PRIVATE)
        val existing = legacy.getString("origin", null)
        val existingId = legacy.getString("id", null)
        check((existing == null || existing == profile.origin.value) && (existingId == null || existingId == profile.id)) {
            "Update Android System WebView to switch servers. This provider supports only one isolated session."
        }
        legacy.edit().putString("origin", profile.origin.value).putString("id", profile.id).apply()
        return CookieManager.getInstance()
    }

    /** Call on the UI thread before binding profiles, ideally on a cold start. */
    fun pruneForgotten(context: Context) {
        if (!supportsMultiple()) return
        val preferences = context.getSharedPreferences("forgotten_browser_profiles", Context.MODE_PRIVATE)
        val remaining = preferences.getStringSet("ids", emptySet()).orEmpty().toMutableSet()
        val store = ProfileStore.getInstance()
        remaining.toList().forEach { id ->
            try {
                if (id !in store.allProfileNames || store.deleteProfile(id)) remaining.remove(id)
            } catch (_: IllegalStateException) { /* Loaded profiles can only be removed after a process restart. */ }
        }
        preferences.edit().putStringSet("ids", remaining).apply()
    }

    /** Dispose all views first. Clear data now; remove the unloaded container on a later cold start. */
    suspend fun forget(context: Context, profile: ServerProfile): Boolean = withContext(NonCancellable + Dispatchers.Main) {
        if (!supportsMultiple() || !WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) return@withContext false
        val store = ProfileStore.getInstance()
        if (profile.browserId !in store.allProfileNames) return@withContext true
        val browserProfile = store.getProfile(profile.browserId) ?: return@withContext true
        val cleared = suspendCancellableCoroutine { continuation ->
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
                WebStorageCompat.deleteBrowsingData(browserProfile.webStorage) { continuation.resume(true) }
            } else continuation.resume(false)
        }
        if (!cleared) return@withContext false
        val preferences = context.applicationContext.getSharedPreferences("forgotten_browser_profiles", Context.MODE_PRIVATE)
        val ids = preferences.getStringSet("ids", emptySet()).orEmpty() + profile.browserId
        withContext(Dispatchers.IO) { preferences.edit().putStringSet("ids", ids).commit() }
        true
    }
}
