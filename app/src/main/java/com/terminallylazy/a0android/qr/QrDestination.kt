package com.terminallylazy.a0android.qr

import com.terminallylazy.a0android.web.ServerOrigin

/** Agent Zero tunnel codes contain a plain URL. Import supplies a draft, never trust or credentials. */
object QrDestination {
    const val invalidMessage = "Use a QR code containing only an HTTPS server address, without credentials, a path, query, or fragment. International domains must use their ASCII (punycode) address."

    fun parse(payload: String): ServerOrigin? {
        val text = payload.trim()
        if (text.isEmpty() || text.length > 2048) return null
        if (text.any { it.code !in 33..126 || it == '%' || it == '\\' }) return null
        return ServerOrigin.parse(text)
    }
}
