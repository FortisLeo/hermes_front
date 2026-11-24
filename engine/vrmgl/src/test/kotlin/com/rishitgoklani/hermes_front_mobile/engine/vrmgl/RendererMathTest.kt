package com.rishitgoklani.hermes_front_mobile.engine.vrmgl

import com.rishitgoklani.hermes_front_mobile.engine.vrm.MorphTarget
import com.rishitgoklani.hermes_front_mobile.engine.vrm.math.Mat4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RendererMathTest {
    private fun randomMat(seed: Int): FloatArray { val r = java.util.Random(seed.toLong()); return FloatArray(16) { r.nextFloat() * 2 - 1 }.also { it[3] = 0f; it[7] = 0f; it[11] = 0f; it[15] = 1f } }

    @Test fun packRowsMatchesFullMatrixMultiply() {
        val world = FloatArray(32).also { System.arraycopy(randomMat(1), 0, it, 0, 16); System.arraycopy(randomMat(2), 0, it, 16, 16) }
        val ib = FloatArray(32).also { System.arraycopy(randomMat(3), 0, it, 0, 16); System.arraycopy(randomMat(4), 0, it, 16, 16) }
        val joints = intArrayOf(1, 0); val out = FloatArray(24); SkinMath.packRows(world, joints, ib, out)
        for (j in joints.indices) { val m = FloatArray(16); Mat4.mul(world, joints[j] * 16, ib, j * 16, m, 0)
            for (r in 0 until 3) for (c in 0 until 4) assertEquals(m[c * 4 + r], out[j * 12 + r * 4 + c], 1e-5f) }
    }

    @Test fun morphRangeCoversAllTargets() {
        val r = MorphMath.touchedRange(listOf(MorphTarget(intArrayOf(7, 9), FloatArray(6)), MorphTarget(intArrayOf(3), FloatArray(3))))
        assertArrayEquals(intArrayOf(3, 9), r)
        assertNull(MorphMath.touchedRange(listOf(MorphTarget(IntArray(0), FloatArray(0)))))
    }

    @Test fun mipLevelsOfFullChain() { assertEquals(1, TextureMath.levels(1, 1)); assertEquals(10, TextureMath.levels(512, 512)); assertEquals(11, TextureMath.levels(1024, 300)) }

    @Test fun toonEdgeNeverZero() {
        assertEquals(0.002f, MaterialMath.toonEdge(1f), 0f); assertEquals(1f, MaterialMath.toonEdge(0f), 0f); assertEquals(0.4f, MaterialMath.toonEdge(0.6f), 1e-6f)
        assertTrue(MaterialMath.toonEdge(5f) > 0f)
    }

    @Test fun gammaConvertsOnce() { assertEquals(0f, MaterialMath.gamma(0f), 0f); assertEquals(1f, MaterialMath.gamma(1f), 1e-6f); assertTrue(MaterialMath.gamma(0.5f) > 0.7f) }

    @Test fun outlineVariantSamplesOnlyAlphaTextures() {
        val all = ShaderSources.FLAG_BASE_TEX or ShaderSources.FLAG_SHADE_TEX or ShaderSources.FLAG_EMISSIVE_TEX or ShaderSources.FLAG_OUTLINE
        val frag = ShaderSources.fragment(all)
        assertFalse(frag.contains("#define SHADE_TEX")); assertFalse(frag.contains("#define EM_TEX")); assertFalse(frag.contains("#define BASE_TEX"))
        assertEquals(ShaderSources.fragment(ShaderSources.FLAG_OUTLINE), frag)
        assertTrue(ShaderSources.fragment(all or ShaderSources.FLAG_MASK).contains("#define BASE_TEX"))
    }

    @Test fun variantsShareKeysWhenOutputIsIdentical() {
        val a = ShaderSources.variantKey(ShaderSources.FLAG_UNLIT or ShaderSources.FLAG_SHADE_TEX, false, 0)
        val b = ShaderSources.variantKey(ShaderSources.FLAG_UNLIT, false, 0)
        assertEquals(a, b)
        assertTrue(ShaderSources.variantKey(0, true, 50) != ShaderSources.variantKey(0, true, 60))
        assertTrue(ShaderSources.variantKey(0, true, 50) != ShaderSources.variantKey(0, false, 50))
    }

    @Test fun shadersUseHighpUvAndNoUniformBranching() {
        val v = ShaderSources.vertex(ShaderSources.FLAG_BASE_TEX, true, 40); val f = ShaderSources.fragment(ShaderSources.FLAG_BASE_TEX)
        assertTrue(v.contains("out highp vec2 vUv")); assertTrue(f.contains("in highp vec2 vUv"))
        assertFalse(f.contains("uFlags")); assertFalse(v.contains("continue")); assertTrue(v.contains("#define MAXJ 40"))
        assertTrue(f.contains("smoothstep(-uToony, uToony, d)"))
    }

    @Test fun uniformTablesAgree() { assertEquals(ShaderSources.UNIFORMS.size, ShaderSources.SHADOW_SIZES.size); assertEquals("uEmTex", ShaderSources.UNIFORMS[ShaderSources.U_EM_TEX]) }
}
