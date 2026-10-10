package com.terminallylazy.a0android.files

import java.util.Locale

const val MAX_BLOB_BYTES = 100L * 1024 * 1024
const val CHUNK_BYTES = 64 * 1024

fun safeFilename(value: String): String {
    val cleaned = value.substringAfterLast('/').substringAfterLast('\\')
        .map { if (it.isISOControl() || it in ":*?\"<>|") '_' else it }.joinToString("")
        .trim().trim('.').take(120)
    val base = cleaned.substringBefore('.').uppercase(Locale.ROOT)
    return when {
        cleaned.isEmpty() -> "download"
        base in setOf("CON", "PRN", "AUX", "NUL") || base.matches(Regex("(?:COM|LPT)[1-9]")) -> "download_$cleaned"
        else -> cleaned
    }
}

/** Validates the receiver's byte accounting independently of untrusted page metadata. */
class ChunkLedger(val expectedBytes: Long) {
    var received: Long = 0; private set
    var nextSequence: Int = 0; private set
    init { require(expectedBytes in 0..MAX_BLOB_BYTES) }
    fun accept(sequence: Int, byteCount: Int) {
        require(sequence == nextSequence)
        require(byteCount in 1..CHUNK_BYTES)
        require(received + byteCount <= expectedBytes)
        received += byteCount
        nextSequence++
    }
    fun finish() { require(received == expectedBytes) }
}
