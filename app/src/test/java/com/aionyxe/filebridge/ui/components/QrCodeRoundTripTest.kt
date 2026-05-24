package com.aionyxe.filebridge.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Encodes a URL as a QR code and decodes it back, confirming the round-trip is lossless.
 * Uses ZXing directly (no Android Bitmap needed in unit tests — operates on raw pixel arrays).
 */
class QrCodeRoundTripTest {

    @Test
    fun qrCode_roundTrip_ftpUrl() {
        val url = "ftp://192.168.1.42:2121"
        val decoded = encodeAndDecode(url)
        assertEquals(url, decoded)
    }

    @Test
    fun qrCode_roundTrip_ftpsUrl() {
        val url = "ftps://10.0.0.5:2121"
        val decoded = encodeAndDecode(url)
        assertEquals(url, decoded)
    }

    private fun encodeAndDecode(content: String): String {
        val size = 256
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.Q,
            EncodeHintType.MARGIN to 1,
        )
        val writer = QRCodeWriter()
        val matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)

        // Build a raw int array (grayscale: black = 0xFF000000, white = 0xFFFFFFFF).
        val pixels = IntArray(size * size) { i ->
            if (matrix[i % size, i / size]) AndroidColor.BLACK else AndroidColor.WHITE
        }

        val source: LuminanceSource = RGBLuminanceSource(size, size, pixels)
        val binary = BinaryBitmap(HybridBinarizer(source))

        val reader = QRCodeReader()
        val result = reader.decode(binary, mapOf(DecodeHintType.TRY_HARDER to true))
        return result.text
    }
}
