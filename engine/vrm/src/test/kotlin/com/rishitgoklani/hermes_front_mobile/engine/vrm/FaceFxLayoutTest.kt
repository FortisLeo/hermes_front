package com.rishitgoklani.hermes_front_mobile.engine.vrm

import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.Glb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceFxLayoutTest {
    private fun rig(vrm1: Boolean, arms: Boolean = true) = Rig(VrmParser.parse(Glb.parse(MiniVrm.build(vrm1 = vrm1, arms = arms))))
    private val all = DriverInput(effects = setOf("blush", "tears", "sweatdrop", "angermark"))

    private fun settled(r: Rig): Pair<FaceMixer, FaceFxLayout> {
        val m = FaceMixer(r); val l = FaceFxLayout(r)
        for (i in 0 until 120) m.update(1f / 30f, all)
        r.update(); l.update(m); return m to l
    }

    @Test fun cheeksSitBelowAndInFrontOfEachEyeForBothVrmVersions() {
        for (vrm1 in listOf(true, false)) {
            val r = rig(vrm1); val (_, l) = settled(r)
            assertEquals("eyes are 6 cm apart", 0.06f, l.eyeDistance, 1e-4f)
            val left = l.decal(FaceMixer.FX_BLUSH, -1f)!!; val right = l.decal(FaceMixer.FX_BLUSH, 1f)!!
            assertTrue("viewer-left cheek is left of the right cheek (vrm1=$vrm1)", left.matrix[12] < right.matrix[12])
            assertEquals(-0.03f, left.matrix[12], 1e-3f); assertEquals(0.03f, right.matrix[12], 1e-3f)
            assertTrue("below the eyes (y=1.75): ${left.matrix[13]}", left.matrix[13] < 1.75f - 0.02f)
            assertTrue("in front of the eyes (z=0.08): ${left.matrix[14]}", left.matrix[14] > 0.08f)
            assertEquals(1f, left.weight, 0.02f)
        }
    }

    @Test fun sizesScaleWithTheEyeDistance() {
        val r = rig(true); val (_, l) = settled(r); val d = l.decal(FaceMixer.FX_BLUSH, 1f)!!
        assertEquals(0.44f * 0.06f, d.matrix[0], 1e-4f); assertEquals(0.27f * 0.06f, d.matrix[5], 1e-4f)
    }

    @Test fun symbolsFloatAtTheViewersRightAboveTheEyes() {
        val r = rig(true); val (_, l) = settled(r)
        for (kind in listOf(FaceMixer.FX_SWEAT, FaceMixer.FX_ANGER)) { val d = l.decal(kind, 1f)!!
            assertTrue("kind $kind right of centre", d.matrix[12] > 0.03f); assertTrue("above the eyes", d.matrix[13] > 1.75f); assertTrue("floats in front", d.matrix[14] > 0.1f) }
    }

    @Test fun decalsFollowTheHeadWhenItTilts() {
        val r = rig(true); val m = FaceMixer(r); val l = FaceFxLayout(r)
        for (i in 0 until 120) m.update(1f / 30f, all)
        r.update(); l.update(m); val straight = l.decal(FaceMixer.FX_BLUSH, 1f)!!.matrix[13]
        val head = r.asset.humanoid["head"]!!; r.setBoneEuler(head, 0f, 0f, 0.5f); r.update(); l.update(m)
        assertTrue("the right cheek moves when the head rolls", Math.abs(l.decal(FaceMixer.FX_BLUSH, 1f)!!.matrix[13] - straight) > 0.005f)
        assertEquals("the quad's up axis rolls with the head", Math.sin(0.5).toFloat(), Math.abs(l.up[0]), 0.05f)
    }

    @Test fun effectsHideWhenTheHeadTurnsAway() {
        for (vrm1 in listOf(true, false)) {
            val r = rig(vrm1); val m = FaceMixer(r); val l = FaceFxLayout(r)
            for (i in 0 until 120) m.update(1f / 30f, all)
            r.setBoneEuler(r.asset.humanoid["head"]!!, 0f, (Math.PI / 2).toFloat(), 0f); r.update(); l.update(m)
            assertTrue("vrm1=$vrm1 facing ${l.facing}", l.facing < 0.05f); assertEquals(0f, l.decal(FaceMixer.FX_TEARS, 1f)!!.weight, 0.05f)
        }
    }

    @Test fun modelsWithoutEyeBonesFallBackToTheHeadBone() {
        val r = rig(true, arms = false); val (_, l) = settled(r)
        assertTrue(l.eyeDistance > 0f); assertTrue(l.decal(FaceMixer.FX_BLUSH, -1f)!!.weight > 0.9f)
        assertTrue(l.eyeViewerLeft[0] < l.eyeViewerRight[0])
    }

    @Test fun skinDecalsSitInFrontOfTheSkinMeasuredFromTheModelsOwnVertices() {
        // Eyes at x = +-0.03, y = 1.75, z = 0.08 (6 cm apart). Put skin below each eye 3 cm in front of the eye plane (half an eye distance).
        val skin = floatArrayOf(0.03f, 1.713f, 0.11f, -0.03f, 1.713f, 0.10f, 0f, 0f, 0f)
        val r = Rig(VrmParser.parse(Glb.parse(MiniVrm.build(vrm1 = true, arms = true, positions = skin))))
        val l = FaceFxLayout(r)
        assertEquals("0.5 measured + margin", 0.5f + FaceFxLayout.SKIN_MARGIN, l.skinForwardForTests, 0.03f)
        val m = FaceMixer(r); for (i in 0 until 120) m.update(1f / 30f, all); r.update(); l.update(m)
        assertTrue("blush floats just in front of that skin: z=${l.decal(FaceMixer.FX_BLUSH, 1f)!!.matrix[14]}", l.decal(FaceMixer.FX_BLUSH, 1f)!!.matrix[14] > 0.11f)
        assertTrue("symbols stay clear of the skin", l.decal(FaceMixer.FX_ANGER, 1f)!!.matrix[14] > 0.11f + 0.06f * 0.4f)
    }

    @Test fun withoutVerticesNearTheCheeksTheDefaultOffsetIsUsed() {
        val l = FaceFxLayout(rig(true)); assertEquals(FaceFxLayout.SKIN_FORWARD, l.skinForwardForTests, 0f)
    }

    @Test fun eyeBonesImplausiblyCloseTogetherAreReplacedByTheHeadsProportions() {
        // Eye bones 6 cm apart, but the head is 40 cm tall (neck-top bone at y=1.7, model top at 2.1): 6 cm < 20% of 40 cm, so the drawn eyes are wider.
        val tall = floatArrayOf(0f, 0f, 0f, 0f, 2.1f, 0f, 0.1f, 0f, 0f)
        val r = Rig(VrmParser.parse(Glb.parse(MiniVrm.build(vrm1 = true, arms = true, positions = tall))))
        val m = FaceMixer(r); val l = FaceFxLayout(r); for (i in 0 until 120) m.update(1f / 30f, all); r.update(); l.update(m)
        assertEquals(FaceFxLayout.STYLISED_SPACING * 0.4f, l.eyeDistance, 1e-3f)
        val left = l.decal(FaceMixer.FX_BLUSH, -1f)!!; val right = l.decal(FaceMixer.FX_BLUSH, 1f)!!
        assertEquals("cheeks move out to the wider spacing", l.eyeDistance, right.matrix[12] - left.matrix[12], 2e-3f)
    }

    @Test fun plausibleEyeBonesAreLeftAlone() {
        val r = rig(true); val (_, l) = settled(r); assertEquals(0.06f, l.eyeDistance, 1e-4f)
    }
}
