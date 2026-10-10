package com.terminallylazy.a0android.qr

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.nio.ByteBuffer

object QrDecoder {
    /** QR only; each invocation owns its reader so camera and image import cannot share mutable state. */
    fun decode(source: LuminanceSource): String? {
        for (candidate in listOf(source, source.invert())) {
            try {
                return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(candidate)), mapOf(DecodeHintType.TRY_HARDER to true)).text
            } catch (_: ReaderException) { /* A frame without a readable QR is expected. */ }
        }
        return null
    }

    /** The camera's Y plane may contain padded rows and non-unit pixel strides. */
    fun luminance(buffer: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int): LuminanceSource {
        require(width > 0 && height > 0 && width.toLong() * height <= 4_000_000)
        require(rowStride > 0 && pixelStride > 0)
        val start = buffer.position()
        val end = start.toLong() + (height - 1L) * rowStride + (width - 1L) * pixelStride
        require(end < buffer.limit())
        val pixels = ByteArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            pixels[y * width + x] = buffer.get(start + y * rowStride + x * pixelStride)
        }
        // QR detection is rotation invariant; no full-color frame or retained camera image is needed.
        return PlanarYUVLuminanceSource(pixels, width, height, 0, 0, width, height, false)
    }
}
