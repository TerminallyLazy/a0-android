package com.terminallylazy.a0android.files

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Base64
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewFeature
import com.terminallylazy.a0android.web.ServerOrigin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStream

data class TransferStatus(val message: String, val busy: Boolean = false)

/** A stale CreateDocument result belongs to our canceled request, so remove the empty destination. */
fun discardCreatedDocument(context: Context, scope: CoroutineScope, uri: Uri, onFailure: () -> Unit) {
    val resolver = context.applicationContext.contentResolver
    scope.launch(start = CoroutineStart.UNDISPATCHED) {
        val removed = withContext(NonCancellable + Dispatchers.IO) {
            try { DocumentsContract.deleteDocument(resolver, uri) } catch (_: Exception) { false }
        }
        if (!removed) onFailure()
    }
}

class DownloadCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val origin: ServerOrigin,
    private val cookies: (String) -> String,
    private val userAgent: () -> String,
    private val saveDocument: (String, String) -> Unit,
    private val status: (TransferStatus) -> Unit,
) {
    private sealed interface Request {
        val name: String
        data class Direct(val url: String, override val name: String) : Request
        data class Blob(
            val id: String, override val name: String, val ledger: ChunkLedger,
            val reply: JavaScriptReplyProxy, val begin: JSONObject,
        ) : Request
    }
    private var pending: Request? = null
    private class Sink(val uri: Uri) { @Volatile var stream: OutputStream? = null }
    private var sink: Sink? = null
    private var work: Job? = null
    private var idleTimeout: Job? = null
    private var lastEnvelope: JSONObject? = null
    private var cleaning = false
    private var epoch = 0L
    private var documentId: String? = null
    private var committed = false
    private var chunkBusy = false
    private var pickerOutstanding = false

    fun pageStarted() {
        cancel(silent = true)
        epoch++
        committed = false
        documentId = null
    }
    fun pageCommitted() { committed = true }

    fun direct(url: String, suggestedName: String, mime: String) {
        if (!origin.contains(url)) { status(TransferStatus("Only downloads from this server are allowed.")); return }
        if (pending != null || pickerOutstanding || cleaning) { status(TransferStatus("Finish or cancel the current download first.", true)); return }
        pending = Request.Direct(url, safeFilename(suggestedName))
        pickerOutstanding = true
        status(TransferStatus("Choose where to save the file.", true))
        saveDocument(safeFilename(suggestedName), safeMime(mime))
    }

    fun receive(raw: String, source: Uri, mainFrame: Boolean, reply: JavaScriptReplyProxy) {
        if (!mainFrame || !origin.contains(source.toString()) || raw.length > 92000) return
        val message = try { JSONObject(raw) } catch (_: Exception) { return }
        if (message.optInt("v") != 1) return
        if (message.optString("kind") == "hello") {
            val id = message.optString("documentId")
            if (!committed || id.length !in 1..80) return
            if (documentId != null && documentId != id) return
            documentId = id
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                reply.postMessage(JSONObject().put("kind", "hello").put("epoch", epoch).put("documentId", id).toString())
            }
            return
        }
        if (message.optLong("epoch", -1) != epoch || message.optString("documentId") != documentId || !committed) return
        if ((pending as? Request.Blob)?.id == message.optString("id")) lastEnvelope = message
        when (message.optString("kind")) {
            "begin" -> begin(message, reply)
            "chunk" -> chunk(message, reply)
            "finish" -> finish(message, reply)
            "cancel" -> if ((pending as? Request.Blob)?.id == message.optString("id")) cancel()
            else -> respond(reply, message, false)
        }
    }

    private fun begin(message: JSONObject, reply: JavaScriptReplyProxy) {
        if (pending != null || pickerOutstanding || cleaning || message.optString("id").length !in 1..80) {
            respond(reply, message, false); return
        }
        val ledger = try { ChunkLedger(message.getLong("size")) } catch (_: Exception) {
            status(TransferStatus("This browser download exceeds the 100 MiB limit."))
            respond(reply, message, false); return
        }
        val name = safeFilename(message.optString("name", "download"))
        pending = Request.Blob(message.getString("id"), name, ledger, reply, message)
        lastEnvelope = message
        resetIdleTimeout(180000)
        pickerOutstanding = true
        status(TransferStatus("Choose where to save $name.", true))
        saveDocument(name, safeMime(message.optString("mime")))
    }

    fun destinationChosen(uri: Uri?) {
        pickerOutstanding = false
        val request = pending
        if (request == null) {
            if (uri != null) discardCreatedDocument(context, scope, uri) {
                status(TransferStatus("Download canceled. An empty file may remain in the selected folder."))
            }
            return
        }
        if (uri == null) { cancel(); return }
        val target = Sink(uri)
        sink = target
        val cookiesForRequest = if (request is Request.Direct) cookies(request.url) else ""
        val agent = userAgent()
        work = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    target.stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("Destination is unavailable")
                }
                val stream = target.stream ?: error("Destination is unavailable")
                when (request) {
                    is Request.Blob -> { resetIdleTimeout(); respond(request.reply, request.begin, true) }
                    is Request.Direct -> {
                        var lastUpdate = 0L
                        val count = DownloadTransport().write(origin, request.url, cookiesForRequest, agent, stream) { count ->
                            if (count - lastUpdate >= 256 * 1024 || lastUpdate == 0L) {
                                lastUpdate = count
                                scope.launch { if (pending === request) status(TransferStatus("Saving ${count / 1024} KiB…", true)) }
                            }
                        }
                        complete(request.name, count)
                    }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { fail("Could not save the file. Check storage and sign in, then retry.") }
        }
    }

    private fun chunk(message: JSONObject, reply: JavaScriptReplyProxy) {
        val request = pending as? Request.Blob ?: return
        if (request.id != message.optString("id") || chunkBusy || sink?.stream == null) { respond(reply, message, false); return }
        chunkBusy = true
        resetIdleTimeout()
        work = scope.launch {
            try {
                val encoded = message.getString("data")
                require(encoded.length <= 87384 && encoded.length % 4 == 0 && encoded.matches(Regex("[A-Za-z0-9+/]*={0,2}")))
                val bytes = Base64.decode(encoded, Base64.NO_WRAP)
                request.ledger.accept(message.getInt("sequence"), bytes.size)
                val stream = sink?.stream ?: error("Transfer canceled")
                withContext(Dispatchers.IO) { stream.write(bytes) }
                status(TransferStatus("Saving ${request.ledger.received / 1024} KiB…", true))
                respond(reply, message, true)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { respond(reply, message, false); fail("The download was interrupted or contained invalid data.") }
            finally { chunkBusy = false }
        }
    }

    private fun finish(message: JSONObject, reply: JavaScriptReplyProxy) {
        val request = pending as? Request.Blob ?: return
        if (request.id != message.optString("id") || chunkBusy || sink?.stream == null) { respond(reply, message, false); return }
        work = scope.launch {
            try {
                request.ledger.finish()
                complete(request.name, request.ledger.received)
                respond(reply, message, true)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { respond(reply, message, false); fail("The downloaded file was incomplete.") }
        }
    }

    private suspend fun complete(name: String, count: Long) {
        val stream = sink?.stream ?: error("Missing destination")
        withContext(Dispatchers.IO) { stream.flush(); stream.close() }
        sink = null; pending = null; lastEnvelope = null
        idleTimeout?.cancel(); idleTimeout = null
        status(TransferStatus("Saved $name (${count / 1024} KiB)."))
    }

    fun cancel(silent: Boolean = false) {
        val request = pending
        if (request is Request.Blob) respond(request.reply, lastEnvelope ?: request.begin, false)
        idleTimeout?.cancel(); idleTimeout = null
        val running = work
        running?.cancel(); work = null
        val target = sink
        sink = null; pending = null; chunkBusy = false; lastEnvelope = null
        if (target != null) {
            cleaning = true
            val resolver = context.applicationContext.contentResolver
            // Begin cleanup before Activity teardown; wait for writes/open to finish before closing or deleting.
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                val removed = withContext(NonCancellable + Dispatchers.IO) {
                    running?.join()
                    try { target.stream?.close() } catch (_: Exception) { }
                    try { DocumentsContract.deleteDocument(resolver, target.uri) } catch (_: Exception) { false }
                }
                cleaning = false
                if (!removed) status(TransferStatus("Download canceled. A partial file may remain in the selected folder."))
            }
        }
        if (!silent && request != null) status(TransferStatus("Download canceled."))
    }

    private fun fail(message: String) { cancel(silent = true); status(TransferStatus(message)) }
    private fun resetIdleTimeout(milliseconds: Long = 30000) {
        idleTimeout?.cancel()
        idleTimeout = scope.launch { delay(milliseconds); fail("Download timed out. Please try again.") }
    }
    private fun respond(reply: JavaScriptReplyProxy, message: JSONObject, ok: Boolean) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        try { reply.postMessage(JSONObject().put("id", message.optString("id"))
            .put("epoch", message.optLong("epoch")).put("replyTo", message.optString("replyTo"))
            .put("ok", ok).toString()) } catch (_: IllegalStateException) { /* Frame already gone. */ }
    }
    private fun safeMime(value: String) = value.takeIf { it.matches(Regex("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+")) }
        ?: "application/octet-stream"
}
