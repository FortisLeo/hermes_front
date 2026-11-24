package com.rishitgoklani.hermes_front_mobile.engine.vrmgl

import com.rishitgoklani.hermes_front_mobile.engine.vrm.AlphaMode
import com.rishitgoklani.hermes_front_mobile.engine.vrm.MorphTarget
import com.rishitgoklani.hermes_front_mobile.engine.vrm.VrmMaterial
import kotlin.math.pow

/** Material values converted once at load: colours in the display's gamma space, toon edge half-width, static flags. */
internal class GlMaterial(
    val base: FloatArray, val shade: FloatArray, val emissive: FloatArray, val outlineColor: FloatArray,
    val shift: Float, val edge: Float, val cutoff: Float, val outlineWidth: Float, val doubleSided: Boolean, val staticFlags: Int, val blend: Boolean,
    val baseImage: Int, val shadeImage: Int, val emissiveImage: Int,
)

internal object MaterialMath {
    /** glTF colours are linear; the shader works in gamma space. */
    fun gamma(c: Float) = c.coerceIn(0f, 1f).pow(1f / 2.2f)

    const val MIN_EDGE = 0.002f
    /** Half-width of the lit/shade transition around dot(n,L)+shift: (1 - toony), floored so toony = 1 stays a near-hard step without a zero-width smoothstep. */
    fun toonEdge(toony: Float) = maxOf(1f - toony.coerceIn(0f, 1f), MIN_EDGE)

    fun compile(m: VrmMaterial): GlMaterial {
        var f = 0
        if (m.unlit) f = f or ShaderSources.FLAG_UNLIT
        if (m.alphaMode == com.rishitgoklani.hermes_front_mobile.engine.vrm.AlphaMode.MASK) f = f or ShaderSources.FLAG_MASK
        return GlMaterial(
            floatArrayOf(gamma(m.baseColor[0]), gamma(m.baseColor[1]), gamma(m.baseColor[2]), m.baseColor[3]),
            floatArrayOf(gamma(m.shadeColor[0]), gamma(m.shadeColor[1]), gamma(m.shadeColor[2])),
            floatArrayOf(gamma(m.emissive[0]), gamma(m.emissive[1]), gamma(m.emissive[2])),
            floatArrayOf(gamma(m.outlineColor[0]), gamma(m.outlineColor[1]), gamma(m.outlineColor[2])),
            m.shadeShift, toonEdge(m.shadeToony), m.cutoff, m.outlineWidth, m.doubleSided, f, m.alphaMode == AlphaMode.BLEND,
            m.baseImage, m.shadeImage, m.emissiveImage,
        )
    }
}

internal object MorphMath {
    /** Inclusive [first, last] vertex range touched by any target, or null when no target moves a vertex. Computed once per primitive. */
    fun touchedRange(targets: List<MorphTarget>): IntArray? {
        var lo = Int.MAX_VALUE; var hi = -1
        for (t in targets) for (v in t.vertices) { if (v < lo) lo = v; if (v > hi) hi = v }
        return if (hi < 0) null else intArrayOf(lo, hi)
    }
}

internal object SkinMath {
    /**
     * Writes the 3x4 skinning rows (row-major, 12 floats per joint) of world * inverseBind directly, no intermediate 4x4.
     * [world] and [inverseBind] are column-major 4x4s; [joints] are node indices into [world].
     */
    fun packRows(world: FloatArray, joints: IntArray, inverseBind: FloatArray, out: FloatArray) {
        for (j in joints.indices) {
            val w = joints[j] * 16; val ib = j * 16; val o = j * 12
            for (r in 0 until 3) {
                val w0 = world[w + r]; val w1 = world[w + 4 + r]; val w2 = world[w + 8 + r]; val w3 = world[w + 12 + r]
                for (c in 0 until 4) { val b = ib + c * 4
                    out[o + r * 4 + c] = w0 * inverseBind[b] + w1 * inverseBind[b + 1] + w2 * inverseBind[b + 2] + w3 * inverseBind[b + 3] }
            }
        }
    }
}

internal object TextureMath {
    /** Number of mip levels of a full chain. */
    fun levels(w: Int, h: Int): Int { var m = maxOf(w, h, 1); var n = 1; while (m > 1) { m = m shr 1; n++ }; return n }
}

/** Counters of the last draw, for debugging. */
data class DrawStats(val drawCalls: Int = 0, val uniformCalls: Int = 0, val bytesUploaded: Int = 0)
