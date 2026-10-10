package com.terminallylazy.a0android.web

import com.terminallylazy.a0android.qr.QrDestination
import org.junit.Assert.*
import org.junit.Test

/** The same fixture is also checked against the Swift source by scripts/check-ios-connection-contract.py. */
class ConnectionContractTest {
    @Test fun manualOriginsMatchSharedContract() = verify("manual", ServerOrigin::parse)

    @Test fun qrOriginsMatchSharedContract() = verify("qr", QrDestination::parse)

    private fun verify(flow: String, parse: (String) -> ServerOrigin?) {
        val fixture = requireNotNull(javaClass.getResourceAsStream("/connection-origins.tsv"))
        val cases = fixture.bufferedReader().use { it.readLines() }
            .filter { it.isNotBlank() && !it.startsWith('#') }
            .map { it.split('\t') }.filter { it[0] == flow }
        assertTrue("Contract fixture must contain cases", cases.isNotEmpty())
        cases.forEach { (_, expected, input) ->
            val actual = parse(input)
            if (expected == "reject") {
                assertNull("Rejected contract input: $input", actual)
            } else {
                // Swift preserves an explicit :443; compare canonical authority, not serialized spelling.
                assertEquals(input, requireNotNull(ServerOrigin.parse(expected)), actual)
            }
        }
    }
}
