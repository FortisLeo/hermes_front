package com.rishitgoklani.hermes_front_mobile.engine.vrm

import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.Glb
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.InputStream
import java.nio.ByteBuffer

/** An image the loader is about to decode: its pixel size and the bytes per pixel it will be stored with (565 = 2, RGBA = 4). */
class ImageInfo(val w: Int, val h: Int, val bytesPerPixel: Int)

/** Texture memory policy, a pure function of the device and the model, so it can be tested without a GPU. */
object TextureBudgeting {
    private const val MB = 1024L * 1024
    val CAPS = listOf(4096, 2048, 1024, 512, 256)

    /** Total texture memory (active + staged) this device may spend: a 80th of RAM, between 24 and 96 MB, halved on low-RAM devices. */
    fun deviceBudgetBytes(totalMemBytes: Long, lowRamDevice: Boolean): Long {
        val b = (totalMemBytes / 80).coerceIn(24 * MB, 96 * MB)
        return if (lowRamDevice) b / 2 else b
    }

    /** The largest texture size cap, not above [requestedCap], at which [images] (mipmaps included) fit [budgetBytes]; the smallest cap otherwise. */
    fun capFor(images: List<ImageInfo>, requestedCap: Int, budgetBytes: Long): Int {
        val options = CAPS.filter { it <= requestedCap }.ifEmpty { listOf(CAPS.last()) }
        return options.firstOrNull { cap -> images.sumOf { TextureSizing.decodedBytes(it.w, it.h, cap, it.bytesPerPixel) } <= budgetBytes } ?: options.last()
    }

    /** Budget left for a model that is about to decode, given what the other resident models already hold. Never below half the budget. */
    fun availableBytes(budgetBytes: Long, residentOthers: Long): Long = (budgetBytes - residentOthers).coerceAtLeast(budgetBytes / 2)
}

/** Reads a [ByteBuffer] slice as a stream without copying it (decoders then read straight from the memory-mapped model file). */
class ByteBufferInputStream(buffer: ByteBuffer) : InputStream() {
    private val buf = buffer.duplicate(); private var marked = buf.position()
    override fun read(): Int = if (buf.hasRemaining()) buf.get().toInt() and 0xFF else -1
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (!buf.hasRemaining()) return -1
        val n = minOf(len, buf.remaining()); buf.get(b, off, n); return n
    }
    override fun available(): Int = buf.remaining()
    override fun skip(n: Long): Long { val k = minOf(n.coerceAtLeast(0), buf.remaining().toLong()).toInt(); buf.position(buf.position() + k); return k.toLong() }
    override fun markSupported() = true
    override fun mark(readlimit: Int) { marked = buf.position() }
    override fun reset() { buf.position(marked) }
}

/** Locates embedded images inside the (memory-mapped) BIN chunk without copying them. */
object GlbImageSlices {
    /** A read-only view of image [image]'s bytes, or null when the image has no usable bufferView. */
    fun slice(glb: Glb, asset: VrmAsset, image: Int): ByteBuffer? {
        val view = asset.images.getOrNull(image)?.view ?: return null
        val v = (glb.json["bufferViews"] as? JsonArray)?.getOrNull(view) as? JsonObject ?: return null
        fun int(k: String) = (v[k] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()
        val off = int("byteOffset") ?: 0; val len = int("byteLength") ?: return null
        if (off < 0 || len <= 0 || off.toLong() + len > glb.bin.limit()) return null
        return glb.bin.duplicate().also { it.position(off); it.limit(off + len) }.slice().asReadOnlyBuffer()
    }
}
