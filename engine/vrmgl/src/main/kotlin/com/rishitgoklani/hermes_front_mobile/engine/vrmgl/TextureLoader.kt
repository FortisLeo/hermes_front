package com.rishitgoklani.hermes_front_mobile.engine.vrmgl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.rishitgoklani.hermes_front_mobile.engine.vrm.TextureSizing
import com.rishitgoklani.hermes_front_mobile.engine.vrm.VrmAsset

/** Decodes one embedded image at the size the budget allows. Runs off the GL thread; opaque images are decoded as 16-bit RGB565. */
internal object TextureLoader {
    /** Images that some material actually samples (thumbnails, matcaps and the like are never decoded). */
    fun usedImages(asset: VrmAsset): List<Int> = asset.materials.flatMap { listOf(it.baseImage, it.shadeImage, it.emissiveImage) }.filter { it >= 0 }.distinct()

    fun decode(asset: VrmAsset, image: Int, cap: Int, withAlpha: Boolean, rgb565: Boolean): Bitmap? {
        val bytes = asset.imageBytes(image); if (bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = TextureSizing.sampleSize(bounds.outWidth, bounds.outHeight, cap)
            inPreferredConfig = if (rgb565 && !withAlpha) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }
}
