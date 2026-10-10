package com.terminallylazy.a0android.web

import org.junit.Assert.*
import org.junit.Test

class ServerOriginTest {
    @Test fun normalizesOnlyRootHttpsOrigins() {
        val origin = requireNotNull(ServerOrigin.parse(" HTTPS://Example.COM:443/ "))
        assertEquals("https://example.com", origin.value)
        assertTrue(origin.contains("https://example.com/api/file?path=%2Ftmp%2Fa"))
        assertNotNull(ServerOrigin.parse("https://[::1]:8443"))
        assertEquals("xn--bcher-kva.example", ServerOrigin.parse("https://bücher.example")?.url?.host)
    }
    @Test fun rejectsCredentialsSubpathsAndNonHttps() {
        listOf("http://example.com", "file:///tmp/test", "https://x.com/path", "https://x.com?q=secret", "https://x.com#token",
            "https://user:pass@x.com", "https://@x.com", "https://x.com:0", "https://x.com:65536", "https://x.com\\evil", "https://x.com\n.evil").forEach {
            assertNull(it, ServerOrigin.parse(it))
        }
    }
    @Test fun comparesEffectivePortAndEntireHost() {
        val origin = requireNotNull(ServerOrigin.parse("https://example.com:8443"))
        assertFalse(origin.contains("https://example.com/"))
        assertFalse(origin.contains("https://example.com.evil.test:8443"))
        assertFalse(origin.contains("https://example.com:8443@evil.test"))
        assertFalse(origin.contains("http://example.com:8443"))
        assertTrue(origin.contains("https://example.com:8443/path"))
    }
    @Test fun externalNavigationRequiresGestureAndSafeScheme() {
        val origin = requireNotNull(ServerOrigin.parse("https://a.test"))
        assertEquals(NavigationDecision.Blocked, origin.navigation("https://b.test", false))
        assertTrue(origin.navigation("https://b.test", true) is NavigationDecision.External)
        listOf("intent:foo", "javascript:alert(1)", "data:text/html,hi", "content://a/x", "http://b.test").forEach {
            assertEquals(NavigationDecision.Blocked, origin.navigation(it, true))
        }
        assertEquals(NavigationDecision.Internal, origin.navigation("https://a.test/login", false))
    }
}
