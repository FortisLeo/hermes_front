package com.rishitgoklani.hermes_front_mobile.data.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.GenericMultipleBarcodeReader
import com.rishitgoklani.hermes_front_mobile.domain.vision.CodeScanner
import com.rishitgoklani.hermes_front_mobile.domain.vision.ScannedCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Pure ZXing decoding over ARGB pixels (JVM-testable). Tries normal, then inverted (light-on-dark) codes, then a global-histogram binarizer. */
object ZxingDecoder {
    fun decode(width: Int, height: Int, argb: IntArray): List<ScannedCode> {
        val source = RGBLuminanceSource(width, height, argb)
        val hints = mapOf(DecodeHintType.TRY_HARDER to true)
        val attempts = listOf(
            { BinaryBitmap(HybridBinarizer(source)) },
            { BinaryBitmap(HybridBinarizer(source.invert())) },
            { BinaryBitmap(GlobalHistogramBinarizer(source)) },
        )
        for (bitmap in attempts) {
            val results: List<Result> = try {
                GenericMultipleBarcodeReader(MultiFormatReader().apply { setHints(hints) }).decodeMultiple(bitmap(), hints).toList()
            } catch (e: NotFoundException) {
                try { listOf(MultiFormatReader().decode(bitmap(), hints)) } catch (e2: NotFoundException) { emptyList() }
            }
            if (results.isNotEmpty()) return results.map { ScannedCode(it.barcodeFormat.name, it.text) }.distinctBy { it.format to it.text }
        }
        return emptyList()
    }
}

/** [CodeScanner] adapter: JPEG to pixels to ZXing (Apache-2.0). No network, frame discarded. */
class ZxingCodeScanner(private val maxSide: Int = 1600) : CodeScanner {
    override suspend fun scan(jpeg: ByteArray): List<ScannedCode> = withContext(Dispatchers.Default) {
        val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return@withContext emptyList()
        val big = maxOf(bmp.width, bmp.height)
        val scaled = if (big > maxSide) { val k = maxSide.toFloat() / big; Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true) } else bmp
        val px = IntArray(scaled.width * scaled.height); scaled.getPixels(px, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        ZxingDecoder.decode(scaled.width, scaled.height, px)
    }
}
