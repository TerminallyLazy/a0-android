package com.terminallylazy.a0android.qr

import org.junit.Assert.*
import org.junit.Test

class QrDestinationTest {
    @Test fun acceptsTunnelRootAndExplicitPorts() {
        assertEquals("https://agent.example:8443", QrDestination.parse("  HTTPS://Agent.Example:8443/\n")?.value)
        assertEquals("https://agent.example", QrDestination.parse("https://agent.example")?.value)
        assertEquals("https://[2001:db8::1]", QrDestination.parse("https://[2001:db8::1]/")?.value)
        assertNotNull(QrDestination.parse("https://xn--bcher-kva.example"))
    }

    @Test fun rejectsSecretsSchemesAndAmbiguousPayloads() {
        listOf("http://localhost:49805", "http://agent.example", "https://user:secret@agent.example",
            "https://agent.example/login", "https://agent.example?token=secret", "https://agent.example/#secret",
            "javascript:alert(1)", "{\"url\":\"https://agent.example\"}", "https://agent.example\\@other.example",
            "https://ag\nent.example", "https://аgent.example", "https://agent.example%2f.evil",
            "https://agent.example:99999", "https:agent.example", "https:///agent.example", "", "x".repeat(2049)
        ).forEach { assertNull("Unexpected accepted payload", QrDestination.parse(it)) }
        assertFalse(QrDestination.invalidMessage.contains("secret"))
    }

    @Test fun rejectsPathsBeforeHttpUrlNormalizationCanEraseThem() {
        listOf("/..", "/./", "/a/..", "//", "/%2e", "/%2F", "/?", "/#").forEach {
            assertNull(QrDestination.parse("https://agent.example$it"))
        }
    }
}
