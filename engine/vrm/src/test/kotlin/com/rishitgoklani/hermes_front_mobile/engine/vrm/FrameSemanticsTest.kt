package com.rishitgoklani.hermes_front_mobile.engine.vrm

import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.Glb
import com.rishitgoklani.hermes_front_mobile.engine.vrm.math.Mat4
import com.rishitgoklani.hermes_front_mobile.engine.vrm.math.Quat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Pins the agent pose contract: the same agent numbers (leftUpperArm z=+1.25 is arms down) give the same picture on VRM 0.x and VRM 1.0.
 * three-vrm keeps normalized bones in the raw glTF frame (VRM 0.x faces -Z there) and flips x/z only for VRMA clips, so our agent frame is the
 * VRM 0.x frame and VRM 1.0 gets the half-turn-about-Y conjugation inside [Rig].
 */
class FrameSemanticsTest {
    private fun rig(vrm1: Boolean) = Rig(VrmParser.parse(Glb.parse(MiniVrm.build(vrm1 = vrm1, arms = true))))
    private val tmp = FloatArray(3)

    /** Position of [name] in draw space (after the model matrix). */
    private fun draw(r: Rig, name: String): FloatArray {
        val n = r.asset.humanoid.getValue(name); r.worldPosition(n, tmp); val o = FloatArray(3); Mat4.transformPoint(r.modelMatrix, 0, tmp[0], tmp[1], tmp[2], o); return o
    }
    private fun drawForward(r: Rig, name: String): FloatArray {
        val n = r.asset.humanoid.getValue(name); val q = FloatArray(4); r.worldRotation(n, q)
        val raw = FloatArray(3); Quat.rotate(q, 0f, 0f, if (r.asset.isVrm0) -1f else 1f, raw)       // the model's own forward, in the raw frame
        val o = FloatArray(3); Mat4.transformPoint(r.modelMatrix, 0, raw[0], raw[1], raw[2], o)
        return floatArrayOf(o[0] - r.modelMatrix[12], o[1] - r.modelMatrix[13], o[2] - r.modelMatrix[14])
    }
    private fun relative(r: Rig, a: String, b: String): FloatArray { val p = draw(r, a); val s = draw(r, b); return floatArrayOf(p[0] - s[0], p[1] - s[1], p[2] - s[2]) }
    private fun assertSame(a: FloatArray, b: FloatArray, msg: String) { for (i in 0..2) assertEquals("$msg [$i]", a[i], b[i], 1e-4f) }

    @Test fun restingStanceHasBothArmsDownOnBothFormats() {
        for (vrm1 in listOf(false, true)) {
            val r = rig(vrm1); val d = PoseDriver(r) { 0.5f }
            repeat(30) { d.frame(0.033f, it * 33L, DriverInput()) }; r.update()
            for (side in listOf("left", "right")) {
                val hand = draw(r, "${side}Hand")[1]; val shoulder = draw(r, "${side}UpperArm")[1]
                assertTrue("vrm1=$vrm1 $side hand y $hand must be below shoulder y $shoulder", hand < shoulder - 0.1f)
            }
        }
    }

    @Test fun theLeftArmStaysOnTheCharactersLeftInDrawSpaceOnBothFormats() {
        val a = relative(rig(false), "leftUpperArm", "hips"); val b = relative(rig(true), "leftUpperArm", "hips")
        assertTrue(a[0] > 0f && b[0] > 0f); assertSame(a, b, "left shoulder")
    }

    @Test fun theSameAgentPoseLooksTheSameOnVrm0AndVrm1() {
        val poses = listOf(
            mapOf("leftUpperArm" to floatArrayOf(0f, 0f, 1.25f), "rightUpperArm" to floatArrayOf(0f, 0f, -1.25f)),
            mapOf("leftUpperArm" to floatArrayOf(0.7f, 0.4f, 0.3f), "spine" to floatArrayOf(0.2f, 0.5f, -0.3f), "head" to floatArrayOf(0.3f, -0.6f, 0.2f)),
            mapOf("rightUpperArm" to floatArrayOf(-0.5f, 1.1f, -0.2f), "leftUpperArm" to floatArrayOf(0f, -0.9f, 0.6f)),
        )
        for ((pi, pose) in poses.withIndex()) {
            val r0 = rig(false); val r1 = rig(true)
            for (r in listOf(r0, r1)) { for ((name, e) in pose) r.setBoneEuler(r.asset.humanoid.getValue(name), e[0], e[1], e[2]); r.update() }
            for (name in listOf("leftHand", "rightHand", "head", "leftEye")) assertSame(draw(r0, name), draw(r1, name), "pose $pi $name")
        }
    }

    @Test fun agentHipsOffsetsAreInTheSameFrameOnBothFormats() {
        val r0 = rig(false); val r1 = rig(true)
        for (r in listOf(r0, r1)) { r.setBoneOffset(r.asset.humanoid.getValue("hips"), 0.1f, 0.2f, 0.3f); r.update() }
        assertSame(draw(r0, "hips"), draw(r1, "hips"), "hips")
        assertEquals(0.2f, draw(r0, "hips")[1] - 1f, 1e-5f)
    }

    @Test fun gazeTurnsHeadAndEyesTheSameWayOnBothFormats() {
        val rigs = listOf(false, true).map { vrm1 ->
            val r = rig(vrm1); val d = PoseDriver(r) { 0.5f }
            repeat(120) { d.frame(0.033f, it * 33L, DriverInput(gazeX = 1f, gazeY = 0.5f)) }; r.update(); r
        }
        val head0 = drawForward(rigs[0], "head"); val head1 = drawForward(rigs[1], "head")
        assertSame(head0, head1, "head forward")
        assertTrue("the head actually turned", kotlin.math.abs(head0[0]) > 0.1f)
        val eye0 = drawForward(rigs[0], "leftEye"); val eye1 = drawForward(rigs[1], "leftEye")
        assertSame(eye0, eye1, "eye forward")
        assertTrue("the eye turns further than the head alone", kotlin.math.abs(eye0[0]) > kotlin.math.abs(head0[0]) + 0.02f)
        assertTrue("same direction as the head", eye0[0] * head0[0] > 0f)
    }

    @Test fun gazeMovesNoEyesWhenTheModelHasNone() {
        val r = Rig(VrmParser.parse(Glb.parse(MiniVrm.build()))); val d = PoseDriver(r) { 0.5f }
        repeat(10) { d.frame(0.033f, it * 33L, DriverInput(gazeX = 1f)) }      // must not throw
    }

    // ---- real models (skipped when the file is not on this machine; never committed) ----
    private fun realModels(): List<File> {
        val dir = File(System.getProperty("user.home"), "Downloads")
        return (dir.listFiles { f -> f.extension.equals("vrm", true) } ?: emptyArray()).sortedBy { it.name }
    }

    @Test fun realModelsHoldBothArmsDownAtRest() {
        val files = realModels(); assumeTrue("no .vrm models in ~/Downloads", files.isNotEmpty())
        for (f in files) {
            val r = Rig(VrmParser.parse(Glb.open(f))); val d = PoseDriver(r) { 0.5f }
            repeat(30) { d.frame(0.033f, it * 33L, DriverInput()) }; r.update()
            for (side in listOf("left", "right")) {
                val hand = r.asset.humanoid["${side}Hand"] ?: continue; val upper = r.asset.humanoid["${side}UpperArm"] ?: continue
                r.worldPosition(hand, tmp); val hy = tmp[1]; r.worldPosition(upper, tmp); val sy = tmp[1]
                assertTrue("${f.name} (vrm0=${r.asset.isVrm0}) $side hand y $hy must be below shoulder y $sy", hy < sy - 0.05f)
            }
        }
    }

    @Test fun realModelsTurnTheirHeadSidewaysAndStayFacingTheViewerForGaze() {
        val files = realModels(); assumeTrue("no .vrm models in ~/Downloads", files.isNotEmpty())
        for (f in files) {
            val r = Rig(VrmParser.parse(Glb.open(f))); val d = PoseDriver(r) { 0.5f }
            repeat(120) { d.frame(0.033f, it * 33L, DriverInput(gazeX = 1f)) }; r.update()
            val fwd = drawForward(r, "head")
            assertTrue("${f.name} head still faces the viewer side (z>0): ${fwd.toList()}", fwd[2] > 0.5f)
            assertTrue("${f.name} head turned sideways", kotlin.math.abs(fwd[0]) > 0.1f)
        }
    }
}
