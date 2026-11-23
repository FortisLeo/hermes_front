package com.rishitgoklani.hermes_front_mobile.data.avatar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.ImageTranscoder
import java.io.ByteArrayOutputStream

/**
 * Shrinks one embedded image with BitmapFactory. The decoder subsamples while reading (inSampleSize), so a 4096 px texture is never
 * fully decoded; PNG stays PNG (keeps alpha), JPEG stays JPEG.
 */
class BitmapImageTranscoder(private val jpegQuality: Int = 85) : ImageTranscoder {
    override fun shrink(data: ByteArray, mime: String, maxSide: Int): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        val w = bounds.outWidth; val h = bounds.outHeight
        if (w <= 0 || h <= 0 || maxOf(w, h) <= maxSide) return null
        var sample = 1; while (maxOf(w, h) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val decoded = BitmapFactory.decodeByteArray(data, 0, data.size, opts) ?: return null
        val k = maxSide.toDouble() / maxOf(w, h)
        val scaled = if (maxOf(decoded.width, decoded.height) > maxSide) Bitmap.createScaledBitmap(decoded, maxOf(1, (w * k).toInt()), maxOf(1, (h * k).toInt()), true) else decoded
        if (scaled !== decoded) decoded.recycle()
        return try {
            ByteArrayOutputStream().use { out ->
                val png = mime.contains("png", true)
                if (!scaled.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, if (png) 100 else jpegQuality, out)) null else out.toByteArray()
            }
        } finally { scaled.recycle() }
    }
}
