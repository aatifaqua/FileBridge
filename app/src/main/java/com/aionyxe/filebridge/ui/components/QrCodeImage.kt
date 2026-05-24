package com.aionyxe.filebridge.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders a QR code for [content] off the main thread and displays it as a square [size]×[size].
 *
 * - ECC level Q (≈ 25 % recovery).
 * - 1-module margin.
 * - Colors follow the active Material 3 color scheme.
 */
@Composable
fun QrCodeImage(
    content: String,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
) {
    val foreground = MaterialTheme.colorScheme.onSurface.toArgb()
    val background = MaterialTheme.colorScheme.surface.toArgb()

    val bitmap by produceState<Bitmap?>(initialValue = null, content, foreground, background) {
        value = withContext(Dispatchers.Default) {
            runCatching { generateQrBitmap(content, foreground, background) }.getOrNull()
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = content,
                modifier = Modifier.size(size),
            )
        }
    }
}

private fun generateQrBitmap(content: String, fgColor: Int, bgColor: Int): Bitmap {
    val writer = QRCodeWriter()
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.Q,
        EncodeHintType.MARGIN to 1,
        EncodeHintType.CHARACTER_SET to "UTF-8",
    )
    val size = 512
    val matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val pixels = IntArray(size * size) { index ->
        if (matrix[index % size, index / size]) fgColor else bgColor
    }
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
        it.setPixels(pixels, 0, size, 0, 0, size, size)
    }
}
