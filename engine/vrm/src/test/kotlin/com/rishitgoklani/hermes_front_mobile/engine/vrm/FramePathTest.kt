package com.rishitgoklani.hermes_front_mobile.engine.vrm

import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.Glb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.lang.management.ManagementFactory

class FramePathTest {
    private fun rig(vrm1: Boolean = true, springs: Boolean = false, arms: Boolean = true) = Rig(VrmParser.parse(Glb.parse(MiniVrm.build(vrm1 = vrm1, withSprings = springs, arms = arms, targets = 2))))

    // ---- dirty tracking ----
    @Test fun dirtyIsSetOnceThenClearedByConsume() {
        val r = rig(); assertTrue(r.consumeDirty()); assertFalse(r.consumeDirty())
    }

    @Test fun changingALocalTransformMarksDirtyButRewritingTheSameValueDoesNot() {
        val r = rig(); r.consumeDirty(); val head = r.asset.humanoid.getValue("head")
        r.setBoneEuler(head, 0.2f, 0f, 0f); assertTrue(r.consumeDirty())
        r.setBoneEuler(head, 0.2f, 0f, 0f); assertFalse(r.consumeDirty())
        r.setBoneOffset(r.asset.humanoid.getValue("hips"), 0f, 0.1f, 0f); assertTrue(r.consumeDirty())
        r.setBoneOffset(r.asset.humanoid.getValue("hips"), 0f, 0.1f, 0f); assertFalse(r.consumeDirty())
    }

    @Test fun changingAMorphWeightMarksDirty() {
        val r = rig(); r.consumeDirty(); r.setExpression("happy", 0.5f); r.applyExpressions()
        assertTrue(r.consumeDirty()); r.applyExpressions(); assertFalse(r.consumeDirty())
    }

    @Test fun resetPoseMarksDirty() { val r = rig(); r.consumeDirty(); r.resetPose(); assertTrue(r.consumeDirty()) }

    @Test fun anIdleDriverWithoutBreathingLeavesTheRigClean() {
        val r = rig(); val d = PoseDriver(r) { 0.5f }; d.breathing = false; val inp = DriverInput()
        for (i in 0..20) d.frame(0.016f, i * 16L, inp)
        r.consumeDirty(); for (i in 21..60) d.frame(0.016f, i * 16L, inp)
        assertFalse("nothing changed so the renderer may skip drawing", r.consumeDirty())
    }

    @Test fun aMovingPoseDirtiesTheRigAndMovingStaysAccurate() {
        val r = rig(); val d = PoseDriver(r) { 0.5f }; d.breathing = false
        val pose = PoseSpec(bones = mapOf("head" to floatArrayOf(0.5f, 0f, 0f))); val inp = DriverInput(pose = pose, blendMs = 300)
        for (i in 0..5) d.frame(0.016f, i * 16L, inp); assertTrue(d.moving); assertTrue(r.consumeDirty())
        for (i in 6..200) d.frame(0.016f, i * 16L, inp); r.consumeDirty()
        for (i in 201..220) d.frame(0.016f, i * 16L, inp); assertFalse(d.moving); assertFalse("pose settled", r.consumeDirty())
    }

    // ---- spring settling ----
    @Test fun springsSettleAfterAboutASecondOfStillnessAndWakeWhenTheBodyMoves() {
        val r = rig(springs = true, arms = false); val sim = SpringSimulation(r)
        assertFalse(sim.settled)
        var steps = 0; while (!sim.settled && steps < 2000) { r.update(); sim.step(0.033f); steps++ }
        assertTrue("settled within the run", sim.settled); assertTrue(steps * 0.033f >= 1f)
        r.setNodeTranslationOffset(1, 1f, 0f, 0f); r.update(); sim.step(0.033f); assertFalse("body motion wakes the hair", sim.settled)
        sim.reset(); assertFalse(sim.settled)
    }

    @Test fun aModelWithoutSpringsIsAlwaysSettled() { assertTrue(SpringSimulation(rig(vrm1 = false, springs = false, arms = false)).settled) }

    // ---- allocation ----
    private fun hasAllocCounter() = ManagementFactory.getThreadMXBean() is com.sun.management.ThreadMXBean
    private fun allocated(): Long = (ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean).getThreadAllocatedBytes(Thread.currentThread().id)

    private inline fun measure(frames: Int, run: (Int) -> Unit): Long {
        for (i in 0 until 400) run(i)                      // warm up (JIT, lazy tables)
        val c0 = allocated(); val overhead = allocated() - c0          // cost of the measurement itself
        val before = allocated(); for (i in 400 until 400 + frames) run(i); return allocated() - before - overhead
    }

    @Test fun framesAllocateNothingWithAHeldPoseMoodAndGaze() {
        assumeTrue(hasAllocCounter())
        val r = rig(); val d = PoseDriver(r) { 0.5f }
        val pose = PoseSpec(bones = mapOf("head" to floatArrayOf(0.2f, 0.1f, 0f), "leftUpperArm" to floatArrayOf(0f, 0f, 0.4f)), expressions = mapOf("happy" to 0.5f), hips = floatArrayOf(0f, 0.05f, 0f))
        val inp = DriverInput(pose = pose, mood = "happy", mouth = 0.4f, gazeX = 0.5f, gazeY = 0.2f)
        val bytes = measure(300) { d.frame(0.016f, it * 16L, inp); r.update(); r.consumeDirty() }
        assertTrue("allocated $bytes bytes over 300 frames", bytes < 1024)
    }

    @Test fun framesAllocateNothingWhileAnAnimationPlays() {
        assumeTrue(hasAllocCounter())
        val r = rig(); val d = PoseDriver(r) { 0.5f }
        val anim = AnimationSpec(listOf(
            KeyframeSpec(0, PoseSpec(bones = mapOf("head" to floatArrayOf(0f, 0f, 0f)), expressions = mapOf("happy" to 0f))),
            KeyframeSpec(800, PoseSpec(bones = mapOf("head" to floatArrayOf(0.5f, 0.3f, 0f)), expressions = mapOf("happy" to 1f), hips = floatArrayOf(0f, 0.1f, 0f))),
        ), loop = true)
        val inp = DriverInput(animation = anim)
        val bytes = measure(300) { d.frame(0.016f, it * 16L, inp); r.update() }
        assertTrue("allocated $bytes bytes over 300 frames", bytes < 1024)
    }

    @Test fun framesAllocateNothingWhileAClipPlays() {
        assumeTrue(hasAllocCounter())
        val r = rig(); val d = PoseDriver(r) { 0.5f }; d.clipSource = ClipSource { VrmaBuilder.build() }
        val inp = DriverInput(clip = "wave", mouth = 0.3f)
        val bytes = measure(300) { d.frame(0.016f, it * 16L, inp); r.update() }
        assertTrue("allocated $bytes bytes over 300 frames", bytes < 1024)
    }

    @Test fun springStepsAllocateNothing() {
        assumeTrue(hasAllocCounter())
        val r = rig(springs = true, arms = false); val sim = SpringSimulation(r)
        val bytes = measure(300) { r.setNodeTranslationOffset(1, 0f, 0f, if (it % 2 == 0) 0.01f else 0f); r.update(); sim.step(0.016f) }
        assertTrue("allocated $bytes bytes over 300 steps", bytes < 1024)
    }

    @Test fun expressionTableMatchesTheNamedLookup() {
        val r = rig(); assertEquals(-1, r.expressionIndex("nope")); assertTrue(r.expressionIndex("happy") >= 0)
        r.setExpression(r.expressionIndex("happy"), 1f); r.applyExpressions(); assertEquals(1f, r.meshWeights.getValue(0)[0], 1e-5f)
    }
}
