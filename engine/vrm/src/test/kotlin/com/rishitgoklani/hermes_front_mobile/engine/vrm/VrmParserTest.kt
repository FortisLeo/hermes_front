package com.rishitgoklani.hermes_front_mobile.engine.vrm

import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.Glb
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.Half
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.ByteOrder

class VrmParserTest {
    private fun load(vrm1: Boolean = false) = VrmParser.parse(Glb.parse(MiniVrm.build(vrm1)))

    @Test fun readsNodesHumanoidAndPrimitive() {
        val a = load()
        assertEquals(7, a.nodes.size); assertEquals(1, a.humanoid["hips"]); assertEquals(3, a.humanoid["head"]); assertTrue(a.isVrm0)
        val p = a.primitives.single()
        assertEquals(3, p.vertexCount); assertTrue(p.skinned); assertEquals(16, p.attributeStride); assertEquals(3, p.indexCount)
    }

    @Test fun packsAttributesCompactly() {
        val p = load().primitives.single(); val at = p.attributes.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(127, at.get(2).toInt())                                 // normal z = 1 as snorm8
        assertEquals(1f, Half.toFloat(at.getShort(16 + 4)), 0f)              // second vertex u = 1 as half
        assertEquals(255, at.get(12).toInt() and 0xFF)                       // weight 1 as unorm8
        assertEquals(3 * 12, p.positions.remaining())                        // positions stay full float
    }

    @Test fun morphTargetsAreSparse() {
        val p = load().primitives.single()
        assertEquals(1, p.morphTargets.size); assertArrayEquals(intArrayOf(1), p.morphTargets[0].vertices); assertArrayEquals(floatArrayOf(0f, 1f, 0f), p.morphTargets[0].deltas, 0f)
        assertNotNull(p.morphBase)
    }

    @Test fun vrm0ExpressionsAreRenamedToVrm1PresetsAndKeepCustomNames() {
        val e = load().expressions
        assertEquals(1f, e["aa"]!!.single().weight, 0f); assertTrue(e.containsKey("Smirk")); assertTrue(e.containsKey("A"))
    }

    @Test fun vrm0MaterialPropertiesBecomeToonMaterial() {
        val m = load().materials.single()
        assertArrayEquals(floatArrayOf(1f, 0f, 0f), m.baseColor.copyOf(3), 1e-6f); assertArrayEquals(floatArrayOf(0.2176f, 0f, 0f), m.shadeColor, 1e-4f)
        assertEquals(0.002f, m.outlineWidth, 1e-6f); assertEquals(-0.145f, m.shadeShift, 1e-6f); assertEquals(0.955f, m.shadeToony, 1e-6f); assertEquals(AlphaMode.OPAQUE, m.alphaMode)
    }

    @Test fun vrm1ParsesExpressionsMaterialsAndSprings() {
        val a = load(vrm1 = true)
        assertFalse(a.isVrm0); assertEquals(0.5f, a.expressions["wink"]!!.single().weight, 0f); assertEquals(0, a.expressions["happy"]!!.single().mesh)
        val m = a.materials.single(); assertArrayEquals(floatArrayOf(0.1f, 0.2f, 0.3f), m.shadeColor, 1e-6f); assertEquals(0.003f, m.outlineWidth, 1e-6f)
        assertEquals(1, a.springs.size); assertEquals(2, a.springs[0].joints.size); assertArrayEquals(intArrayOf(0), a.springs[0].colliders)
    }

    @Test fun vrm0SpringChainWalksTheWholeSubtree() {
        val s = load().springs.single(); assertEquals(listOf(4, 5), s.joints.map { it.node })
        assertEquals(-0.2f, s.joints[0].tailLocal[1], 1e-6f)    // tip is the child's rest position
    }

    @Test fun boundsCoverTheMesh() {
        val a = load(); assertEquals(0f, a.boundsMin[0], 0f); assertEquals(1f, a.boundsMax[1], 0f)
    }

    @Test fun rejectsFilesThatAreNotVrm() {
        val plain = MiniVrm.glb("""{"asset":{"version":"2.0"}}""", ByteArray(0))
        try { VrmParser.parse(Glb.parse(plain)); fail() } catch (e: VrmFormatException) { assertTrue(e.message!!.contains("VRM")) }
    }

    @Test fun rejectsGarbage() {
        try { Glb.parse(ByteArray(40)); fail() } catch (e: IllegalArgumentException) {}
    }

    @Test fun halfFloatRoundTrips() {
        for (v in listOf(0f, 1f, -2.5f, 0.333f, 100f, 1e-3f)) assertEquals(v, Half.toFloat(Half.fromFloat(v)), kotlin.math.abs(v) * 1e-3f + 1e-6f)
    }
}
