package com.rishitgoklani.hermes_front_mobile.engine.vrmgl

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import com.rishitgoklani.hermes_front_mobile.engine.vrm.ByteBufferInputStream
import com.rishitgoklani.hermes_front_mobile.engine.vrm.ImageInfo
import com.rishitgoklani.hermes_front_mobile.engine.vrm.TextureSizing
import com.rishitgoklani.hermes_front_mobile.engine.vrm.VrmAsset
import java.nio.ByteBuffer

/**
 * Decodes one embedded image at the size the budget allows. Runs off the GL thread, one image at a time. The compressed bytes are read
 * straight from a slice of the memory-mapped model file (no ByteArray copy): opaque images go through BitmapFactory on a stream wrapper so
 * they can be stored as 16-bit RGB565 (ImageDecoder cannot), images with alpha use ImageDecoder on API 28+.
 */
internal object TextureLoader {
    /** Images that some material actually samples (thumbnails, matcaps and the like are never decoded). */
    fun usedImages(asset: VrmAsset): List<Int> = asset.materials.flatMap { listOf(it.baseImage, it.shadeImage, it.emissiveImage) }.filter { it >= 0 }.distinct()

    /** Pixel size of an image without decoding it, or null when it cannot be read. */
    fun bounds(slice: ByteBuffer): Pair<Int, Int>? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(ByteBufferInputStream(slice), null, o)
        return if (o.outWidth > 0 && o.outHeight > 0) o.outWidth to o.outHeight else null
    }

    fun storesAs565(withAlpha: Boolean, rgb565: Boolean) = rgb565 && !withAlpha
    fun info(size: Pair<Int, Int>, withAlpha: Boolean, rgb565: Boolean) = ImageInfo(size.first, size.second, if (storesAs565(withAlpha, rgb565)) 2 else 4)

    /** Full-quality ARGB_8888 decode at the size [cap] allows (for building the compressed texture cache). */
    fun decodeArgb(slice: ByteBuffer, size: Pair<Int, Int>, cap: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inSampleSize = TextureSizing.sampleSize(size.first, size.second, cap); inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return BitmapFactory.decodeStream(ByteBufferInputStream(slice), null, opts)
    }

    fun decode(slice: ByteBuffer, size: Pair<Int, Int>, cap: Int, withAlpha: Boolean, rgb565: Boolean): Bitmap? {
        val sample = TextureSizing.sampleSize(size.first, size.second, cap)
        if (!storesAs565(withAlpha, rgb565) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(slice.duplicate())) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE; decoder.setTargetSampleSize(sample)
                decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))   // plain 8-bit sRGB, never a wide-gamut F16 bitmap
            }
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = if (storesAs565(withAlpha, rgb565)) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeStream(ByteBufferInputStream(slice), null, opts)
    }
}
