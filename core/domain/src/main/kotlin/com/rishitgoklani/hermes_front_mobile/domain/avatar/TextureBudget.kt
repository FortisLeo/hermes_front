package com.rishitgoklani.hermes_front_mobile.domain.avatar

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.max
import kotlin.math.roundToInt

data class ImageDim(val w: Int, val h: Int) {
    /** The same image scaled so its longer side is at most [cap] (what the viewer decodes it at). */
    fun fit(cap: Int): ImageDim { val longest = max(w, h); return if (longest <= cap) this else ImageDim(max(1, (w * cap.toDouble() / longest).roundToInt()), max(1, (h * cap.toDouble() / longest).roundToInt())) }
}

/** Reads the pixel size of an embedded PNG or JPEG from its header (mirrors texture-budget.js in the viewer). */
object ImageDims {
    fun of(b: ByteArray): ImageDim? {
        fun u8(i: Int) = b[i].toInt() and 255
        if (b.size > 24 && u8(0) == 0x89 && u8(1) == 0x50 && u8(2) == 0x4e && u8(3) == 0x47) {
            fun u32(o: Int) = (u8(o).toLong() shl 24) or (u8(o + 1).toLong() shl 16) or (u8(o + 2).toLong() shl 8) or u8(o + 3).toLong()
            return ImageDim(u32(16).toInt(), u32(20).toInt()).takeIf { it.w > 0 && it.h > 0 }
        }
        if (b.size > 4 && u8(0) == 0xff && u8(1) == 0xd8) {
            var i = 2
            while (i + 9 < b.size) {
                if (u8(i) != 0xff) { i++; continue }
                val m = u8(i + 1)
                if (m == 0xff) { i++; continue }
                if (m == 0xd8 || m == 0x01 || m in 0xd0..0xd7) { i += 2; continue }
                if (m in 0xc0..0xcf && m != 0xc4 && m != 0xc8 && m != 0xcc) return ImageDim((u8(i + 7) shl 8) or u8(i + 8), (u8(i + 5) shl 8) or u8(i + 6)).takeIf { it.w > 0 && it.h > 0 }
                i += 2 + ((u8(i + 2) shl 8) or u8(i + 3))
            }
        }
        return null
    }
}

/** Pixel sizes of all images embedded in a GLB/VRM, read from the JSON chunk and the binary chunk. Images it cannot measure are returned as [UNKNOWN]. */
object GlbImages {
    val UNKNOWN = ImageDim(2048, 2048)

    fun read(glb: ByteArray): List<ImageDim> {
        if (glb.size < 28 || String(glb, 0, 4, Charsets.US_ASCII) != "glTF") return emptyList()
        fun le(o: Int) = (glb[o].toInt() and 255) or (glb[o + 1].toInt() and 255 shl 8) or (glb[o + 2].toInt() and 255 shl 16) or (glb[o + 3].toInt() and 255 shl 24)
        val jsonLen = le(12); if (jsonLen <= 0 || 20 + jsonLen > glb.size) return emptyList()
        val root = runCatching { Json.parseToJsonElement(String(glb, 20, jsonLen, Charsets.UTF_8)).jsonObject }.getOrNull() ?: return emptyList()
        val binStart = 20 + jsonLen + 8   // chunk length + type precede the data
        val views = root["bufferViews"] as? JsonArray ?: JsonArray(emptyList())
        return (root["images"] as? JsonArray).orEmpty().map { img ->
            val view = (img as? JsonObject)?.get("bufferView")?.jsonPrimitive?.intOrNull?.let { views.getOrNull(it) as? JsonObject }
            val off = view?.get("byteOffset")?.jsonPrimitive?.intOrNull ?: 0
            val len = view?.get("byteLength")?.jsonPrimitive?.intOrNull
            if (len == null || binStart + off + len > glb.size) UNKNOWN
            else ImageDims.of(glb.copyOfRange(binStart + off, binStart + off + minOf(len, 262144))) ?: UNKNOWN
        }
    }
    private fun JsonArray?.orEmpty() = this ?: JsonArray(emptyList())
}

/** Strategy: how small must textures be so the model fits the memory budget? */
fun interface TexturePolicy { fun cap(images: List<ImageDim>): Int }

/** GPU memory of the images at [cap]: RGBA (4 bytes per pixel) plus about a third more for mipmaps. */
object TextureMemory {
    fun estimate(images: List<ImageDim>, cap: Int): Long = images.sumOf { val f = it.fit(cap); f.w.toLong() * f.h * 4 * 4 / 3 }
}

/** Picks the largest cap from [caps] (descending) whose estimated texture memory fits [budgetBytes]; falls back to the smallest. */
class MemoryBudgetPolicy(private val budgetBytes: Long = 48L * 1024 * 1024, private val caps: List<Int> = listOf(2048, 1024, 512, 256)) : TexturePolicy {
    override fun cap(images: List<ImageDim>) = caps.firstOrNull { TextureMemory.estimate(images, it) <= budgetBytes } ?: caps.last()
}

data class TexturePlan(val cap: Int, val images: Int, val estimatedBytes: Long, val originalBytes: Long) {
    /** One line for the user, or null when nothing was shrunk. */
    fun note(): String? = if (estimatedBytes < originalBytes) "Textures shrunk to $cap px: about ${(originalBytes - estimatedBytes) / 1048576} MB less memory than the original." else null
}

/** Plans texture sizes for a model and refuses models that cannot fit even at the smallest size. */
class TextureBudget(private val policy: TexturePolicy = MemoryBudgetPolicy(), private val hardLimitBytes: Long = 160L * 1024 * 1024, private val floorCap: Int = 256) {
    fun plan(glb: ByteArray): TexturePlan { val imgs = GlbImages.read(glb); val cap = policy.cap(imgs); return TexturePlan(cap, imgs.size, TextureMemory.estimate(imgs, cap), TextureMemory.estimate(imgs, Int.MAX_VALUE)) }
    fun problem(glb: ByteArray): String? {
        val imgs = GlbImages.read(glb); val min = TextureMemory.estimate(imgs, floorCap)
        return if (min > hardLimitBytes) "model has ${imgs.size} textures, too many for this phone's memory even at $floorCap px" else null
    }
}
