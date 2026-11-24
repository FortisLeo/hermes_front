package com.rishitgoklani.hermes_front_mobile.engine.vrm

import com.rishitgoklani.hermes_front_mobile.engine.vrm.math.Mat4
import kotlin.math.max
import kotlin.math.tan

enum class Pass { OPAQUE, MASK, BLEND, OUTLINE }

/** One draw call: which primitive, in which pass. */
class DrawItem(val primitive: Int, val pass: Pass)

/** Decides draw order once per model: opaque, then alpha-tested, then outlines, then blended (no depth write). */
object RenderPlan {
    fun build(asset: VrmAsset, outlines: Boolean): List<DrawItem> {
        val out = ArrayList<DrawItem>()
        fun mat(i: Int) = asset.materials.getOrNull(asset.primitives[i].material)
        val idx = asset.primitives.indices
        idx.filter { (mat(it)?.alphaMode ?: AlphaMode.OPAQUE) == AlphaMode.OPAQUE }.forEach { out += DrawItem(it, Pass.OPAQUE) }
        idx.filter { mat(it)?.alphaMode == AlphaMode.MASK }.forEach { out += DrawItem(it, Pass.MASK) }
        if (outlines) idx.filter { (mat(it)?.outlineWidth ?: 0f) >= 0.0003f && mat(it)?.alphaMode != AlphaMode.BLEND }.forEach { out += DrawItem(it, Pass.OUTLINE) }
        idx.filter { mat(it)?.alphaMode == AlphaMode.BLEND }.forEach { out += DrawItem(it, Pass.BLEND) }
        return out
    }
}

/** Where to put the camera so the whole model fits, like a portrait photo. */
class Framing(val eyeX: Float, val eyeY: Float, val eyeZ: Float, val targetX: Float, val targetY: Float, val targetZ: Float, val fovY: Float) {
    companion object {
        const val FOV_DEG = 30f
        fun of(asset: VrmAsset, modelMatrix: FloatArray): Framing {
            val c = FloatArray(3); val lo = asset.boundsMin; val hi = asset.boundsMax
            Mat4.transformPoint(modelMatrix, 0, (lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2, c)
            val height = max(hi[1] - lo[1], 0.1f)
            val dist = (height / 2) / tan(Math.toRadians(FOV_DEG / 2.0)).toFloat() * 1.08f
            return Framing(c[0], c[1], c[2] + dist, c[0], c[1], c[2], Math.toRadians(FOV_DEG.toDouble()).toFloat())
        }
    }
}

/** How big a decoded texture should be: never above [cap], halving (power-of-two sample size) until the longest side fits. */
object TextureSizing {
    fun sampleSize(w: Int, h: Int, cap: Int): Int { var s = 1; var m = max(w, h); while (m / s > cap) s *= 2; return s }
    fun decodedBytes(w: Int, h: Int, cap: Int, bytesPerPixel: Int, mips: Boolean = true): Long {
        val s = sampleSize(w, h, cap); val base = (w / s).toLong() * (h / s) * bytesPerPixel
        return if (mips) base * 4 / 3 else base
    }
}

/** Which images need an alpha channel: only those a non-opaque material samples as colour. Everything else can be stored as 16-bit RGB565, half the memory. */
object ImageFormats {
    fun needsAlpha(asset: VrmAsset): BooleanArray {
        val need = BooleanArray(asset.images.size)
        for (m in asset.materials) if (m.alphaMode != AlphaMode.OPAQUE) { if (m.baseImage in need.indices && m.baseImage >= 0) need[m.baseImage] = true }
        return need
    }
}
