package com.rishitgoklani.hermes_front_mobile.engine.vrm

import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.Glb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class RigAndDriverTest {
    private fun rig(vrm1: Boolean = false, springs: Boolean = true) = Rig(VrmParser.parse(Glb.parse(MiniVrm.build(vrm1, springs))))
    private val v = FloatArray(3)

    @Test fun restPoseWorldPositionsFollowTheHierarchy() {
        val r = rig(); r.worldPosition(3, v)
        assertEquals(1.7f, v[1], 1e-5f)     // hips 1.0 + spine 0.3 + head 0.4
    }

    @Test fun vrm0ModelIsTurnedAroundVrm1IsNot() {
        assertEquals(-1f, rig(false).modelMatrix[0], 1e-5f); assertEquals(1f, rig(true).modelMatrix[0], 1e-5f)
    }

    @Test fun rotatingSpineBendsTheHeadAround() {
        val r = rig(); r.setBoneEuler(2, 0f, 0f, (PI / 2).toFloat()); r.update(); r.worldPosition(3, v)
        // head is 0.4 above the spine; a quarter turn about Z swings it to -X of the spine
        assertEquals(-0.4f, v[0], 1e-4f); assertEquals(1.3f, v[1], 1e-4f)
    }

    @Test fun zeroEulerKeepsRestPose() {
        val r = rig(); r.setBoneEuler(2, 0f, 0f, 0f); r.update(); r.worldPosition(3, v); assertEquals(1.7f, v[1], 1e-5f)
    }

    @Test fun nodeOffsetsAreRelativeToRest() {
        val r = rig(); r.setNodeTranslationOffset(1, 0f, 0.5f, 0f); r.update(); r.worldPosition(3, v); assertEquals(2.2f, v[1], 1e-5f)
    }

    @Test fun skinMatricesAreIdentityAtRest() {
        val r = rig(); val m = FloatArray(16); r.jointMatrices(0, m)
        for (i in 0 until 16) assertEquals(if (i % 5 == 0) 1f else 0f, m[i], 1e-5f)
    }

    @Test fun skinMatricesFollowTheJoint() {
        val r = rig(); r.setNodeTranslationOffset(1, 0f, 2f, 0f); r.update(); val m = FloatArray(16); r.jointMatrices(0, m)
        assertEquals(2f, m[13], 1e-5f)
    }

    @Test fun expressionsBecomeMorphWeightsAndMarkMeshDirty() {
        val r = rig(); r.setExpression("aa", 0.5f); r.applyExpressions()
        assertEquals(0.5f, r.meshWeights[0]!![0], 1e-5f); assertTrue(0 in r.dirtyMeshes)
        r.dirtyMeshes.clear(); r.applyExpressions(); assertTrue(r.dirtyMeshes.isEmpty())        // unchanged -> not dirty again
    }

    @Test fun customExpressionNamesWorkAndUnknownOnesAreIgnored() {
        val r = rig(); r.setExpression("Smirk", 1f); r.setExpression("nope", 1f); r.applyExpressions(); assertEquals(0.5f, r.meshWeights[0]!![0], 1e-5f)
    }

    @Test fun nodeNamesListHumanoidThenOthers() {
        val names = rig().nodeNames(); assertTrue(names.indexOf("hips") < names.indexOf("hairTip")); assertTrue("hair" in names); assertTrue("Root" !in names)
    }

    @Test fun driverHoldsArmsDownByDefault() {
        val r = rig(); val d = PoseDriver(r) { 0.5f }
        d.frame(0.033f, 0, DriverInput()); r.update()                       // mini model has no arms: must not crash
        r.worldPosition(3, v); assertEquals(1.7f, v[1], 0.02f)             // spine/head only breathe a hair
    }

    @Test fun driverBlendsTowardAHeldPose() {
        val r = rig(); val d = PoseDriver(r) { 0.5f }
        val pose = PoseSpec(bones = mapOf("spine" to floatArrayOf(0f, 0f, 1f)))
        val inp = DriverInput(pose = pose, blendMs = 300)
        d.frame(0.016f, 0, inp); r.update(); r.worldPosition(3, v); val early = v[0]
        for (i in 1..60) d.frame(0.016f, i * 16L, inp); r.update(); r.worldPosition(3, v)
        assertTrue("moves further each frame", abs(v[0]) > abs(early)); assertEquals(-0.4f * kotlin.math.sin(1f), v[0], 0.02f)
    }

    @Test fun animationSamplesWithEaseAndLoops() {
        val r = rig(); val d = PoseDriver(r) { 0.5f }
        val anim = AnimationSpec(listOf(KeyframeSpec(0, PoseSpec(bones = mapOf("spine" to floatArrayOf(0f, 0f, 0f)))), KeyframeSpec(1000, PoseSpec(bones = mapOf("spine" to floatArrayOf(0f, 0f, 1f))))), loop = true)
        val inp = DriverInput(animation = anim)
        d.frame(0.016f, 0, inp); d.frame(0.016f, 500, inp); r.update(); r.worldPosition(3, v)
        assertEquals(-0.4f * kotlin.math.sin(0.5f), v[0], 0.01f)            // halfway, ease(0.5) = 0.5
        d.frame(0.016f, 1500, inp); r.update(); r.worldPosition(3, v)       // loops: same as t=500
        assertEquals(-0.4f * kotlin.math.sin(0.5f), v[0], 0.01f)
        assertTrue(d.moving)
    }

    @Test fun mouthDrivesTheAaExpression() {
        val r = rig(); val d = PoseDriver(r) { 0.9f }
        repeat(30) { d.frame(0.033f, it * 33L, DriverInput(mouth = 0.8f)) }
        assertTrue(r.meshWeights[0]!![0] > 0.5f)
    }

    @Test fun blinkRunsOnItsOwnAndAgentBlinkWins() {
        val r = rig(); val d = PoseDriver(r) { 0f }
        var peak = 0f; for (i in 0..120) { d.frame(0.033f, i * 33L, DriverInput()); peak = maxOf(peak, r.meshWeights[0]!![0]) }
        assertEquals("the mini model has no blink shape, so nothing may move", 0f, peak, 0f)
        val animated = AnimationSpec(listOf(KeyframeSpec(0, PoseSpec(expressions = mapOf("blink" to 0f)))), true)
        d.frame(0.033f, 5000, DriverInput(animation = animated)); assertEquals(0f, r.meshWeights[0]!![0], 0f)
    }

    @Test fun movingIsFalseWhenIdle() {
        val r = rig(); val d = PoseDriver(r) { 0.5f }; val inp = DriverInput(); d.frame(0.016f, 0, inp); d.frame(0.016f, 5000, inp)
        assertTrue(!d.moving)
    }

    @Test fun hairHangsDownAndFollowsTheBody() {
        val r = rig(); val sim = SpringSimulation(r); assertTrue(!sim.isEmpty)
        repeat(60) { r.update(); sim.step(0.033f) }
        r.worldPosition(5, v); val resting = v.copyOf()
        r.setNodeTranslationOffset(1, 1f, 0f, 0f)                            // body jumps 1 m to the side
        r.update(); sim.step(0.033f); r.worldPosition(5, v)
        val lagging = v[0]
        repeat(120) { r.update(); sim.step(0.033f) }; r.worldPosition(5, v)
        assertTrue("tip never leaves bone length from the head", abs(lagging - (resting[0] + 1f)) <= 0.2f + 1e-3f)
        assertEquals("settles under the head again", resting[0] + 1f, v[0], 0.1f)
        assertTrue("hair stays attached at its bone length", abs(v[1] - (1.7f + 0.1f - 0.2f)) < 0.1f)
    }

    @Test fun springColliderKeepsHairOutOfASphere() {
        val r = rig(vrm1 = true); val sim = SpringSimulation(r)
        repeat(90) { r.update(); sim.step(0.033f) }; r.worldPosition(5, v)
        assertTrue(v[1] > 0f)
    }

    @Test fun noSpringsMeansNothingToSimulate() = assertTrue(SpringSimulation(rig(springs = false)).isEmpty)
}
