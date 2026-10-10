package com.terminallylazy.a0android.files

import com.terminallylazy.a0android.web.ServerOrigin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.TimeUnit

class DownloadTransport(
    private val client: OkHttpClient = OkHttpClient.Builder().followRedirects(false)
        .followSslRedirects(false).connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).build(),
) {
    suspend fun write(
        origin: ServerOrigin, initialUrl: String, cookies: String, userAgent: String,
        output: OutputStream, onProgress: (Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        var url = initialUrl.toHttpUrlOrNull() ?: throw IOException("Invalid download address")
        repeat(6) {
            ensureActive()
            if (!origin.contains(url.toString()) || url.encodedPath == "/login") {
                throw IOException("Download requires a same-server authenticated address")
            }
            val request = Request.Builder().url(url).header("User-Agent", userAgent)
                .header("Cookie", cookies).get().build()
            val call = client.newCall(request)
            val cancelWatcher = launch { try { awaitCancellation() } finally { call.cancel() } }
            try {
                call.execute().use { response ->
                    if (response.code in setOf(301, 302, 303, 307, 308)) {
                        url = url.resolve(response.header("Location") ?: throw IOException("Invalid redirect"))
                            ?: throw IOException("Invalid redirect")
                        return@repeat
                    }
                    if (!response.isSuccessful) throw IOException("Download returned HTTP ${response.code}")
                    if (response.header("Content-Disposition") == null &&
                        response.header("Content-Type").orEmpty().startsWith("text/html")) {
                        throw IOException("Server returned a page instead of a download. Sign in and retry.")
                    }
                    val body = response.body
                    var total = 0L
                    body.byteStream().use { input ->
                        val buffer = ByteArray(CHUNK_BYTES)
                        while (true) {
                            ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            total += count
                            onProgress(total)
                        }
                    }
                    if (body.contentLength() >= 0 && total != body.contentLength()) throw IOException("Incomplete download")
                    output.flush()
                    return@withContext total
                }
            } finally { cancelWatcher.cancel() }
        }
        throw IOException("Too many redirects")
    }
}
