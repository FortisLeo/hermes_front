package com.rishitgoklani.hermes_front_mobile.engine.vrm

import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.Glb
import com.rishitgoklani.hermes_front_mobile.engine.vrm.math.Mat4
import com.rishitgoklani.hermes_front_mobile.engine.vrm.math.Quat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class VrmaTest {
    private val expr = """"expressions":{"preset":{"aa":{"morphTargetBinds":[{"node":6,"index":0,"weight":1.0}]}}}"""
    private fun rig(vrm1: Boolean, expressions: Boolean = false) =
        Rig(VrmParser.parse(Glb.parse(MiniVrm.build(vrm1 = vrm1, withSprings = false, expressions1 = if (expressions) expr else null))))
    private val v = FloatArray(3)

    @Test fun parsesBonesHipsAndExpressionTracks() {
        val c = VrmaParser.parse(VrmaBuilder.build())
        assertEquals(1f, c.duration, 0f); assertEquals(listOf("spine"), c.rotations.map { it.name }); assertNotNull(c.hips)
        assertEquals(listOf("aa"), c.expressions.map { it.name }); assertEquals(1f, c.restHipsHeight, 1e-6f)
    }

    @Test fun rejectsFilesThatAreNotVrma() {
        try { VrmaParser.parse(MiniVrm.build()); fail() } catch (e: VrmFormatException) { assertTrue(e.message!!.contains("VRMA")) }
    }

    @Test fun samplingSlerpsBetweenKeys() {
        val t = VrmaParser.parse(VrmaBuilder.build()).rotations.single(); val out = FloatArray(4); val tmp = FloatArray(4)
        t.sample(0.5f, out, tmp)
        assertEquals(sin(PI / 8).toFloat(), out[2], 1e-4f); assertEquals(cos(PI / 8).toFloat(), out[3], 1e-4f)
        t.sample(-1f, out, tmp); assertEquals(1f, out[3], 1e-6f); t.sample(5f, out, tmp); assertEquals(sin(PI / 4).toFloat(), out[2], 1e-4f)
    }

    @Test fun clipRotationAppliesToAVrm1ModelInPlace() {
        val r = rig(true); val p = VrmaPlayer(VrmaParser.parse(VrmaBuilder.build()))
        p.advance(0.5f, r); r.update(); r.worldPosition(3, v)             // spine turned 45 degrees about Z: head (0,0.4) swings to -X; hips also rose 0.25
        assertEquals(-0.4f * sin(PI / 4).toFloat(), v[0], 1e-4f); assertEquals(1.3f + 0.4f * cos(PI / 4).toFloat() + 0.25f, v[1], 1e-3f)
    }

    @Test fun clipRotationIsMirroredOnVrm0LikeThreeVrmAnimation() {
        val r = rig(false); val p = VrmaPlayer(VrmaParser.parse(VrmaBuilder.build()))
        p.advance(0.5f, r); r.update(); r.worldPosition(3, v)
        assertEquals("x and z are negated for VRM 0.x, so the head swings the other way in the raw frame", +0.4f * sin(PI / 4).toFloat(), v[0], 1e-4f)
    }

    @Test fun clipAppearsTheSameInDrawSpaceOnBothFormats() {
        val a = rig(false); val b = rig(true); val clip = VrmaParser.parse(VrmaBuilder.build())
        for (r in listOf(a, b)) { VrmaPlayer(clip).advance(0.75f, r); r.update() }
        val pa = FloatArray(3); val pb = FloatArray(3); val o = FloatArray(3)
        a.worldPosition(3, pa); Mat4.transformPoint(a.modelMatrix, 0, pa[0], pa[1], pa[2], o)
        b.worldPosition(3, pb); assertEquals(o[0], pb[0], 1e-4f); assertEquals(o[1], pb[1], 1e-4f); assertEquals(o[2], pb[2], 1e-4f)
    }

    @Test fun hipsMotionIsScaledByTheModelToClipHipsHeightRatio() {
        val r = rig(true); val p = VrmaPlayer(VrmaParser.parse(VrmaBuilder.build(clipHipsY = 2f, hipsRise = 1f)))    // model hips 1.0, clip hips 2.0 -> scale 0.5
        p.advance(0f, r); r.update(); r.worldPosition(1, v); assertEquals(1f, v[1], 1e-5f)
        p.advance(1f - 1e-4f, r); r.update(); r.worldPosition(1, v); assertEquals(1.5f, v[1], 1e-3f)
    }

    @Test fun playbackLoops() {
        val r = rig(true); val p = VrmaPlayer(VrmaParser.parse(VrmaBuilder.build()))
        p.advance(0.5f, r); r.update(); r.worldPosition(3, v); val half = v.copyOf()
        p.advance(1f, r); r.update(); r.worldPosition(3, v); assertEquals(half[0], v[0], 1e-3f)
    }

    // ---- through the driver ----
    private fun source(bytes: ByteArray, loads: IntArray = IntArray(1)) = ClipSource { name -> if (name == "wave") { loads[0]++; bytes } else null }

    @Test fun driverPlaysAClipLoopedAndStopsOnEmptyName() {
        val r = rig(true); val d = PoseDriver(r) { 0.5f }; val loads = IntArray(1)
        d.clipSource = source(VrmaBuilder.build(withExpression = false), loads)
        for (i in 0..30) d.frame(0.016f, i * 16L, DriverInput(clip = "wave")); r.update(); r.worldPosition(3, v)
        assertTrue("clip moved the head to the side", v[0] < -0.05f); assertTrue(d.moving)
        for (i in 31..60) d.frame(0.016f, i * 16L, DriverInput(clip = "")); r.update(); r.worldPosition(3, v)
        assertEquals("clip stopped, head back over the hips", 0f, v[0], 0.01f); r.worldPosition(1, v); assertEquals(1f, v[1], 1e-3f)
        assertEquals(1, loads[0])
        for (i in 61..70) d.frame(0.016f, i * 16L, DriverInput(clip = "wave")); assertEquals("parsed once, cached by name", 1, loads[0])
        d.reset(); for (i in 0..3) d.frame(0.016f, i * 16L, DriverInput(clip = "wave")); assertEquals("reset drops the cache", 2, loads[0])
    }

    @Test fun unknownOrBrokenClipsAreIgnored() {
        val r = rig(true); val d = PoseDriver(r) { 0.5f }
        d.clipSource = ClipSource { name -> if (name == "bad") byteArrayOf(1, 2, 3) else null }
        d.frame(0.016f, 0, DriverInput(clip = "missing")); d.frame(0.016f, 16, DriverInput(clip = "bad")); d.frame(0.016f, 32, DriverInput(clip = "bad"))
        assertFalse(d.clip?.active == true)
    }

    @Test fun clipExpressionsReachTheMorphWeights() {
        val r = rig(true, expressions = true); val d = PoseDriver(r) { 0.5f }; d.clipSource = source(VrmaBuilder.build())
        for (i in 0..60) d.frame(0.016f, i * 16L, DriverInput(clip = "wave"))                      // ~1s: aa weight rides 0 -> 1 over the loop
        assertTrue(r.meshWeights.getValue(0)[0] > 0.5f)
    }

    @Test fun lipSyncAndBlinkStillRunWhileAClipPlays() {
        val r = rig(true, expressions = true); val d = PoseDriver(r) { 0.5f }; d.clipSource = source(VrmaBuilder.build(withExpression = false))
        repeat(30) { d.frame(0.033f, it * 33L, DriverInput(clip = "wave", mouth = 0.8f)) }
        assertTrue("mouth drives aa during the clip", r.meshWeights.getValue(0)[0] > 0.5f)
    }

    @Test fun gazeStillTurnsEyesWhileAClipPlays() {
        val r = Rig(VrmParser.parse(Glb.parse(MiniVrm.build(vrm1 = true, arms = true, withSprings = false)))); val d = PoseDriver(r) { 0.5f }
        d.clipSource = source(VrmaBuilder.build(withExpression = false))
        repeat(120) { d.frame(0.033f, it * 33L, DriverInput(clip = "wave", gazeX = 1f)) }; r.update()
        val q = FloatArray(4); r.worldRotation(r.asset.humanoid.getValue("leftEye"), q); val f = FloatArray(3); Quat.rotate(q, 0f, 0f, 1f, f)
        assertTrue("eye turned on top of the clip", abs(f[0]) + abs(f[1]) > 0.05f)
    }
}
