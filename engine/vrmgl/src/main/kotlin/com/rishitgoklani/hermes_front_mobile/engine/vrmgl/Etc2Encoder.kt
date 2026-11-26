package com.rishitgoklani.hermes_front_mobile.engine.vrmgl

import android.graphics.Bitmap
import java.nio.ByteBuffer

/** ETC2 compression through libvrmtex (vendored etcpak, see src/main/cpp/etcpak/README.md). [available] is false where the library cannot load (JVM tests). */
internal object Etc2Encoder {
    val available: Boolean = runCatching { System.loadLibrary("vrmtex"); true }.getOrDefault(false)

    /** Bytes of one compressed level: 8 per 4x4 block (RGB8), 16 with an EAC alpha block (RGBA8). */
    fun levelBytes(width: Int, height: Int, alpha: Boolean) = ((width + 3) / 4) * ((height + 3) / 4) * (if (alpha) 16 else 8)

    /** Compresses an ARGB_8888 bitmap (other configs are converted first) into GL-ready ETC2 RGB8 or RGBA8 EAC blocks. */
    fun encode(bitmap: Bitmap, alpha: Boolean): ByteArray {
        val bmp = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        try {
            val src = ByteBuffer.allocateDirect(bmp.rowBytes * bmp.height); bmp.copyPixelsToBuffer(src)
            val size = levelBytes(bmp.width, bmp.height, alpha); val dst = ByteBuffer.allocateDirect(size)
            val n = nativeEncode(src, bmp.width, bmp.height, bmp.rowBytes, alpha, dst)
            check(n == size) { "ETC2 encode failed for ${bmp.width}x${bmp.height}" }
            return ByteArray(size).also { dst.get(it) }
        } finally { if (bmp !== bitmap) bmp.recycle() }
    }

    @JvmStatic private external fun nativeEncode(src: ByteBuffer, width: Int, height: Int, stride: Int, alpha: Boolean, dst: ByteBuffer): Int
}
