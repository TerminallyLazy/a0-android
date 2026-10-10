package com.terminallylazy.a0android.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.RGBLuminanceSource
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class QrDecoderTest {
    private val address = "https://agent.example:8443"

    @Test fun decodesPaddedCameraRowsWithPixelStrideAndBufferOffset() {
        val matrix = MultiFormatWriter().encode(address, BarcodeFormat.QR_CODE, 320, 320)
        val stride = matrix.width * 2 + 16
        val buffer = ByteBuffer.allocate(stride * matrix.height + 7)
        buffer.position(7)
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            buffer.put(7 + y * stride + x * 2, if (matrix[x, y]) 0 else 255.toByte())
        }
        assertEquals(address, QrDecoder.decode(QrDecoder.luminance(buffer, matrix.width, matrix.height, stride, 2)))
        assertEquals(7, buffer.position())
    }

    @Test fun decodesRotatedAndInvertedQrImages() {
        val matrix = MultiFormatWriter().encode(address, BarcodeFormat.QR_CODE, 320, 320)
        repeat(4) {
            val pixels = IntArray(matrix.width * matrix.height) { i -> if (matrix[i % matrix.width, i / matrix.width]) -1 else -0x1000000 }
            assertEquals(address, QrDecoder.decode(RGBLuminanceSource(matrix.width, matrix.height, pixels)))
            matrix.rotate90()
        }
    }

    @Test fun ordinaryBarcodesAndBlankFramesDoNotBecomeDestinations() {
        val barcode = MultiFormatWriter().encode("123456789", BarcodeFormat.CODE_128, 320, 100)
        val pixels = IntArray(barcode.width * barcode.height) { i -> if (barcode[i % barcode.width, i / barcode.width]) -0x1000000 else -1 }
        assertNull(QrDecoder.decode(RGBLuminanceSource(barcode.width, barcode.height, pixels)))
        assertNull(QrDecoder.decode(RGBLuminanceSource(320, 320, IntArray(320 * 320) { -1 })))
    }

    @Test fun cameraBoundsAreValidatedBeforeAllocationOrRead() {
        listOf({ QrDecoder.luminance(ByteBuffer.allocate(10), 20, 20, 20, 1) },
            { QrDecoder.luminance(ByteBuffer.allocate(10), Int.MAX_VALUE, 2, 1, 1) }).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { invalid() }
        }
    }
}
