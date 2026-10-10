package com.terminallylazy.a0android.qr

import com.terminallylazy.a0android.web.ServerOrigin
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Public links contain an address only. They never authorize a network request or carry login data. */
object ConnectionLink {
    fun parse(raw: String): ServerOrigin? {
        if (raw.length > 8192 || raw.any { it.code !in 33..126 }) return null
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (uri.scheme != "a0android" || uri.rawAuthority != "connect" ||
            !uri.rawPath.isNullOrEmpty() || uri.rawFragment != null) return null
        val query = uri.rawQuery ?: return null
        if (!query.startsWith("server=") || '&' in query) return null
        val address = runCatching {
            URLDecoder.decode(query.removePrefix("server="), StandardCharsets.UTF_8.name())
        }.getOrNull() ?: return null
        if (address.any { it.code !in 33..126 }) return null
        return QrDestination.parse(address)
    }
}
