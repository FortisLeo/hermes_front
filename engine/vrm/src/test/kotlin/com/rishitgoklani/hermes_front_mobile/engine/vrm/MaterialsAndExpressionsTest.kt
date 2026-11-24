package com.rishitgoklani.hermes_front_mobile.engine.vrm

import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.Glb
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class MaterialsAndExpressionsTest {
    private fun asset(vrm1: Boolean = false, shade: Pair<Float, Float>? = null, expressions1: String? = null, v0Groups: String? = null, targets: Int = 1) =
        VrmParser.parse(Glb.parse(MiniVrm.build(vrm1 = vrm1, shade = shade, expressions1 = expressions1, v0Groups = v0Groups, targets = targets)))

    // ---- colours ----
    @Test fun vrm0ColoursAreConvertedFromSrgbToLinearWithPow22() {
        val m = asset().materials.single()
        assertArrayEquals(floatArrayOf(1f, 0f, 0f), m.baseColor.copyOf(3), 1e-6f)
        assertEquals(0.5f.toDouble().pow(2.2).toFloat(), m.shadeColor[0], 1e-6f); assertEquals(0f, m.shadeColor[1], 0f)
        assertEquals(0.2176f, m.shadeColor[0], 1e-4f)
    }

    @Test fun vrm1ColoursAreAlreadyLinearAndStayUntouched() {
        val m = asset(vrm1 = true).materials.single(); assertArrayEquals(floatArrayOf(0.1f, 0.2f, 0.3f), m.shadeColor, 1e-6f)
    }

    @Test fun vrm0AlphaIsNotColourConverted() { assertEquals(1f, asset().materials.single().baseColor[3], 0f) }

    // ---- shade shift / toony ----
    @Test fun shadeMigrationMatchesThreeVrmForPinnedInputs() {
        // toony' = lerp(toony, 1, 0.5 + 0.5 * shift); shift' = -shift - (1 - toony')
        VrmParser.migrateV0Shade(0f, 0.9f).let { assertEquals(-0.05f, it[0], 1e-6f); assertEquals(0.95f, it[1], 1e-6f) }
        VrmParser.migrateV0Shade(-0.05f, 0.9f).let { assertEquals(-0.0025f, it[0], 1e-6f); assertEquals(0.9475f, it[1], 1e-6f) }
        VrmParser.migrateV0Shade(0f, 1.0f).let { assertEquals(0f, it[0], 1e-6f); assertEquals(1f, it[1], 1e-6f) }
    }

    @Test fun vrm0MaterialsCarryTheMigratedShadeValues() {
        val m = asset(shade = -0.05f to 0.9f).materials.single(); assertEquals(-0.0025f, m.shadeShift, 1e-6f); assertEquals(0.9475f, m.shadeToony, 1e-6f)
        val z = asset(shade = 0f to 1f).materials.single(); assertEquals(0f, z.shadeShift, 1e-6f); assertEquals(1f, z.shadeToony, 1e-6f)
    }

    @Test fun vrm1ShadeValuesAreUsedAsIs() { assertEquals(0.2f, asset(vrm1 = true).materials.single().shadeShift, 1e-6f) }

    @Test fun vrm0OutlineWidthStaysInMetres() { assertEquals(0.002f, asset().materials.single().outlineWidth, 1e-6f) }

    // ---- expression overrides ----
    private val overrides = """"expressions":{"preset":{
        "blink":{"morphTargetBinds":[{"node":6,"index":0,"weight":1.0}]},
        "aa":{"morphTargetBinds":[{"node":6,"index":1,"weight":1.0}]},
        "happy":{"morphTargetBinds":[{"node":6,"index":2,"weight":1.0}],"overrideBlink":"block","overrideMouth":"blend"}},
        "custom":{"wink":{"isBinary":true,"morphTargetBinds":[{"node":6,"index":3,"weight":1.0}]}}}"""
    private fun rig() = Rig(asset(vrm1 = true, expressions1 = overrides, targets = 4))
    private fun Rig.w(i: Int) = meshWeights.getValue(0)[i]

    @Test fun overrideFieldsAndIsBinaryAreParsed() {
        val a = asset(vrm1 = true, expressions1 = overrides, targets = 4)
        assertEquals("block", a.expressionMeta["happy"]!!.overrideBlink); assertEquals("blend", a.expressionMeta["happy"]!!.overrideMouth)
        assertEquals("none", a.expressionMeta["happy"]!!.overrideLookAt); assertTrue(a.expressionMeta["wink"]!!.isBinary); assertFalse(a.expressionMeta["blink"]!!.isBinary)
    }

    @Test fun vrm0IsBinaryIsParsedFromTheBlendShapeGroup() {
        val a = asset(v0Groups = """{"name":"Wink","presetName":"unknown","isBinary":true,"binds":[{"mesh":0,"index":0,"weight":100}]}""")
        assertTrue(a.expressionMeta["Wink"]!!.isBinary)
    }

    @Test fun blinkAndMouthPassThroughWithoutAnOverrider() {
        val r = rig(); r.setExpression("blink", 1f); r.setExpression("aa", 0.6f); r.applyExpressions()
        assertEquals(1f, r.w(0), 1e-5f); assertEquals(0.6f, r.w(1), 1e-5f)
    }

    @Test fun blockingOverrideSuppressesBlinkAndBlendOverrideScalesMouth() {
        val r = rig(); r.setExpression("happy", 0.5f); r.setExpression("blink", 1f); r.setExpression("aa", 1f); r.applyExpressions()
        assertEquals("blink blocked by any active happy", 0f, r.w(0), 1e-5f)
        assertEquals("mouth scaled by 1 - 0.5", 0.5f, r.w(1), 1e-5f)
        assertEquals(0.5f, r.w(2), 1e-5f)
    }

    @Test fun fullBlendOverrideSilencesTheMouth() {
        val r = rig(); r.setExpression("happy", 1f); r.setExpression("aa", 1f); r.applyExpressions(); assertEquals(0f, r.w(1), 1e-5f)
    }

    @Test fun binaryExpressionsSnapToZeroOrOne() {
        val r = rig(); r.setExpression("wink", 0.4f); r.applyExpressions(); assertEquals(0f, r.w(3), 0f)
        r.setExpression("wink", 0.6f); r.applyExpressions(); assertEquals(1f, r.w(3), 0f)
    }

    @Test fun driverNeverBlinksWhileAMoodThatBlocksBlinkIsActive() {
        val r = rig(); val d = PoseDriver(r) { 0f }
        var peak = 0f; var peakAa = 0f
        for (i in 0..300) { d.frame(0.033f, i * 33L, DriverInput(mood = "happy", mouth = 0.5f)); peak = maxOf(peak, r.w(0)); peakAa = maxOf(peakAa, r.w(1)) }
        assertEquals("blink suppressed", 0f, peak, 1e-5f); assertTrue("lip sync is damped by the blend", peakAa < 0.2f)
        var blinked = 0f; for (i in 301..600) { d.frame(0.033f, i * 33L, DriverInput(mood = "neutral")); blinked = maxOf(blinked, r.w(0)) }
        assertTrue("blink returns once the mood is gone", blinked > 0.5f)
    }

    @Test fun morphWeightsIgnoreChangesBelowTheEpsilon() {
        val r = rig(); r.setExpression("aa", 0.5f); r.applyExpressions(); r.dirtyMeshes.clear()
        r.setExpression("aa", 0.502f); r.applyExpressions(); assertTrue(r.dirtyMeshes.isEmpty()); assertEquals(0.5f, r.w(1), 0f)
        r.setExpression("aa", 0.51f); r.applyExpressions(); assertTrue(0 in r.dirtyMeshes)
        r.setExpression("aa", 0f); r.applyExpressions(); assertEquals("returns exactly to zero", 0f, r.w(1), 0f)
    }
}
