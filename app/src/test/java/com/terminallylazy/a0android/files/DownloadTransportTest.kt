package com.terminallylazy.a0android.files

import com.terminallylazy.a0android.web.ServerOrigin
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException

class DownloadTransportTest {
    private fun fixture(block: (MockWebServer, DownloadTransport, ServerOrigin) -> Unit) {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        MockWebServer().use { server ->
            server.useHttps(serverTls.sslSocketFactory(), false); server.start()
            val client = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
                .followRedirects(false).followSslRedirects(false).build()
            block(server, DownloadTransport(client), requireNotNull(ServerOrigin.parse(server.url("/").toString())))
        }
    }
    @Test fun authenticatedFileWritesExactBytes() = fixture { server, transport, origin ->
        server.enqueue(MockResponse().setBody("fixture-file").setHeader("Content-Disposition", "attachment; filename=a.txt"))
        val output = ByteArrayOutputStream()
        val count = runBlocking { transport.write(origin, server.url("/api/file").toString(), "session_test=x; csrf_token_test=y", "test-agent", output) {} }
        assertEquals(12L, count)
        assertEquals("fixture-file", output.toString())
        assertEquals("session_test=x; csrf_token_test=y", server.takeRequest().getHeader("Cookie"))
    }
    @Test fun crossOriginRedirectIsNeverRequested() = fixture { server, transport, origin ->
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://other.example/leak"))
        assertThrows(IOException::class.java) { runBlocking {
            transport.write(origin, server.url("/file").toString(), "secret-cookie", "test", ByteArrayOutputStream()) {}
        } }
        assertEquals(1, server.requestCount)
    }
    @Test fun loginRedirectAndLoginHtmlAreNotSuccessfulFiles() = fixture { server, transport, origin ->
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/login"))
        val output = ByteArrayOutputStream()
        assertThrows(IOException::class.java) { runBlocking { transport.write(origin, server.url("/file").toString(), "", "test", output) {} } }
        assertEquals(0, output.size())
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<form>Login</form>"))
        assertThrows(IOException::class.java) { runBlocking { transport.write(origin, server.url("/file").toString(), "", "test", output) {} } }
    }
    @Test fun followsOnlySameOriginRedirects() = fixture { server, transport, origin ->
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/final"))
        server.enqueue(MockResponse().setBody("ok"))
        val output = ByteArrayOutputStream()
        runBlocking { transport.write(origin, server.url("/first").toString(), "session=x", "test", output) {} }
        assertEquals("ok", output.toString())
        assertEquals(2, server.requestCount)
    }
}
