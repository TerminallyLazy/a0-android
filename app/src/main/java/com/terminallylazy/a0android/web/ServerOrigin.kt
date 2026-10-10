package com.terminallylazy.a0android.web

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@ConsistentCopyVisibility
data class ServerOrigin private constructor(val url: HttpUrl) {
    val value: String get() = url.toString().removeSuffix("/")
    val root: String get() = url.toString()

    fun contains(raw: String): Boolean {
        val other = raw.toHttpUrlOrNull() ?: return false
        return other.scheme == "https" && other.host == url.host && other.port == url.port &&
            other.username.isEmpty() && other.password.isEmpty()
    }

    companion object {
        fun parse(raw: String): ServerOrigin? {
            val text = raw.trim()
            if (text.any { it.isISOControl() || it == '\\' }) return null
            // Check the supplied shape before HttpUrl can erase dot paths or repair a missing authority.
            if (!text.startsWith("https://", ignoreCase = true)) return null
            if (text.substringAfter("://").substringAfter('/', "").isNotEmpty()) return null
            if ('@' in text.substringAfter("://").substringBefore('/')) return null
            val url = text.toHttpUrlOrNull() ?: return null
            if (url.scheme != "https" || url.username.isNotEmpty() || url.password.isNotEmpty()) return null
            if (url.encodedPath != "/" || url.query != null || url.fragment != null) return null
            return ServerOrigin(url)
        }
    }
}

sealed interface NavigationDecision {
    data object Internal : NavigationDecision
    data class External(val url: String) : NavigationDecision
    data object Blocked : NavigationDecision
}

fun ServerOrigin.navigation(raw: String, userGesture: Boolean): NavigationDecision {
    val target = raw.toHttpUrlOrNull() ?: return NavigationDecision.Blocked
    if (target.scheme != "https" || target.username.isNotEmpty() || target.password.isNotEmpty()) {
        return NavigationDecision.Blocked
    }
    return when {
        contains(raw) -> NavigationDecision.Internal
        userGesture -> NavigationDecision.External(target.toString())
        else -> NavigationDecision.Blocked
    }
}
