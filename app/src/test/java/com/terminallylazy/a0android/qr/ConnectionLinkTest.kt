package com.terminallylazy.a0android.qr

import org.junit.Assert.*
import org.junit.Test
import java.net.URLEncoder

class ConnectionLinkTest {
    private fun link(address: String) = "a0android://connect?server=" + URLEncoder.encode(address, "UTF-8")

    @Test fun acceptsHttpsRootAndPorts() {
        assertEquals("https://agent.example:8443", ConnectionLink.parse(link("https://agent.example:8443/"))?.value)
        assertEquals("https://[::1]", ConnectionLink.parse(link("https://[::1]"))?.value)
    }

    @Test fun rejectsUntrustedLinkShapesAndCredentials() {
        val rejected = listOf(
            "https://connect?server=https://agent.example", "a0android://other?server=https://agent.example",
            "a0android://connect/path?server=https://agent.example", "a0android://connect?server=https://agent.example#x",
            "a0android://connect?server=https://agent.example&server=https://other.example",
            "a0android://connect?server=https://agent.example&token=secret", "a0android://user@connect?server=https://agent.example",
            link("http://agent.example"), link("https://user:secret@agent.example"), link("https://agent.example/path"),
            link("https://agent.example?token=secret"), link("https://agent.example#token"), link("https://agent.example\n"),
            link("https%3A%2F%2Fagent.example"), "a0android://connect?server=%ZZ", link("https://a" + "a".repeat(8200)),
        )
        rejected.forEach { assertNull("Accepted invalid connection link", ConnectionLink.parse(it)) }
    }
}
