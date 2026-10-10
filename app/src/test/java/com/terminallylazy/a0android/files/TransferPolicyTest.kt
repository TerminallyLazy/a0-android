package com.terminallylazy.a0android.files

import org.junit.Assert.*
import org.junit.Test

class TransferPolicyTest {
    @Test fun validatesChunksAndExactFinalLength() {
        val ledger = ChunkLedger(70000)
        ledger.accept(0, CHUNK_BYTES)
        ledger.accept(1, 70000 - CHUNK_BYTES)
        ledger.finish()
        assertEquals(70000, ledger.received)
    }
    @Test fun rejectsDuplicateMissingOversizedAndUnexpectedChunks() {
        assertThrows(IllegalArgumentException::class.java) { ChunkLedger(MAX_BLOB_BYTES + 1) }
        val ledger = ChunkLedger(10)
        assertThrows(IllegalArgumentException::class.java) { ledger.accept(1, 1) }
        assertThrows(IllegalArgumentException::class.java) { ledger.accept(0, 11) }
        assertThrows(IllegalArgumentException::class.java) { ledger.finish() }
        ledger.accept(0, 10)
        assertThrows(IllegalArgumentException::class.java) { ledger.accept(0, 1) }
        ledger.finish()
        ChunkLedger(0).finish()
    }
    @Test fun stripsPathAndControlCharacters() {
        assertEquals("secret.txt", safeFilename("../../secret.txt"))
        assertEquals("evil_.txt", safeFilename("C:\\folder\\evil\n.txt"))
        assertEquals("download", safeFilename("..."))
        assertEquals("download_CON.txt", safeFilename("CON.txt"))
        assertEquals(120, safeFilename("a".repeat(500)).length)
    }
}
