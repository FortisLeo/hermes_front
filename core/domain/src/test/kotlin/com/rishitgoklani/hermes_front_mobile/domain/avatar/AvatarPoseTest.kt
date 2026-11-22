package com.rishitgoklani.hermes_front_mobile.domain.avatar

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarPoseTest {
    private fun obj(s: String) = Json.parseToJsonElement(s).jsonObject
    private fun run(cap: com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability, s: String) = runBlocking { cap.executor.execute(obj(s)) }

    @Test fun allHumanoidBonesAreKnown() { assertEquals(55, HumanoidBones.names.size); assertTrue("leftIndexDistal" in HumanoidBones.names && "rightToes" in HumanoidBones.names) }
    @Test fun parsesBonesExpressionsAndHips() {
        val (p, e) = PoseParser.pose(obj("""{"bones":{"head":[0.1,0,0.2]},"expressions":{"aa":0.7,"blink":3},"hips_offset":[0,-0.1,0]}"""))
        assertNull(e); assertEquals(Vec3(0.1f, 0f, 0.2f), p!!.bones["head"]); assertEquals(1f, p.expressions["blink"]); assertEquals(Vec3(0f, -0.1f, 0f), p.hips)
    }
    @Test fun acceptsAnyNodeNameAndOffsets() {
        val (p, e) = PoseParser.pose(obj("""{"bones":{"Banana_Tail.01":[0,0.3,0]},"offsets":{"Hair_Root":[0,0.02,0]}}"""))
        assertNull(e); assertEquals(Vec3(0f, 0.3f, 0f), p!!.bones["Banana_Tail.01"]); assertEquals(Vec3(0f, 0.02f, 0f), p.offsets["Hair_Root"])
    }
    @Test fun toolChecksNamesAgainstTheLoadedModelAndListsThem() {
        val c = AvatarController(); assertEquals("failed", run(avatarBonesCapability(c), "{}").status)
        c.setNodes(listOf("hips", "Tail")); assertTrue(run(avatarBonesCapability(c), "{}").message.contains("Tail"))
        assertEquals("completed", run(avatarPoseCapability(c), """{"bones":{"Tail":[0,0.2,0]}}""").status)
        assertTrue(run(avatarPoseCapability(c), """{"bones":{"Wing":[0,0.2,0]}}""").message.contains("avatar_bones"))
        assertEquals("failed", run(avatarAnimateCapability(c), """{"keyframes":[{"t_ms":0,"offsets":{"Ghost":[0,0,0]}}]}""").status)
    }
    @Test fun rejectsBadNamesBadShapeAndHugeAngles() {
        assertNotNull(PoseParser.pose(obj("""{"bones":{"a/b;rm":[0,0,0]}}""")).second)
        assertNotNull(PoseParser.pose(obj("""{"bones":{"head":[0,0]}}""")).second)
        assertNotNull(PoseParser.pose(obj("""{"bones":{"head":[9,0,0]}}""")).second)
        assertNotNull(PoseParser.pose(obj("""{"hips_offset":[0,5,0]}""")).second)
    }
    @Test fun animationSortsKeyframesAndValidates() {
        val (a, e) = PoseParser.animation(obj("""{"loop":true,"keyframes":[{"t_ms":500,"bones":{"hips":[0,0,0.2]}},{"t_ms":0,"bones":{"hips":[0,0,-0.2]}}]}"""))
        assertNull(e); assertEquals(listOf(0, 500), a!!.keyframes.map { it.tMs }); assertTrue(a.loop)
        assertNotNull(PoseParser.animation(obj("""{"keyframes":[]}""")).second); assertNotNull(PoseParser.animation(obj("""{"keyframes":[{"bones":{}}]}""")).second)
        assertNotNull(PoseParser.animation(obj("""{"keyframes":[{"t_ms":99999,"bones":{}}]}""")).second)
    }
    @Test fun toolsSetResetAndRejectEmpty() {
        val c = AvatarController(); val pose = avatarPoseCapability(c); val anim = avatarAnimateCapability(c)
        assertEquals("completed", run(pose, """{"bones":{"rightUpperArm":[0,0,-2]},"blend_ms":100}""").status); assertEquals(100, c.state.value.blendMs); assertEquals(1, c.state.value.pose.bones.size)
        assertEquals("completed", run(anim, """{"keyframes":[{"t_ms":0,"bones":{"hips":[0,0,0]}}]}""").status); assertNotNull(c.state.value.animation)
        assertEquals("completed", run(pose, """{"reset":true}""").status); assertTrue(c.state.value.pose.isEmpty && c.state.value.animation == null)
        assertEquals("failed", run(pose, "{}").status); assertEquals("failed", run(pose, """{"bones":{"wi;ng":[0,0,0]}}""").status)
    }
    @Test fun modeToolSwitchesFace() { val c = AvatarController(); val m = avatarModeCapability(c); run(m, """{"mode":"avatar"}"""); assertTrue(c.state.value.enabled); run(m, """{"mode":"eyes"}"""); assertTrue(!c.state.value.enabled); assertEquals("failed", run(m, """{"mode":"x"}""").status) }
    @Test fun viewerJsonIsValidAndClamped() {
        val c = AvatarController(); c.setMouth(4f); c.look(-9f, null); c.setPose(AvatarPose(mapOf("head" to Vec3(1f, 0f, 0f))), 300)
        val o = obj(PoseParser.toJson(c.state.value)); assertEquals("1.0", o["mouth"]!!.jsonPrimitive.content); assertEquals("-1.0", o["gx"]!!.jsonPrimitive.content); assertEquals(300, o["blend"]!!.jsonPrimitive.content.toInt())
        assertTrue(o["pose"]!!.jsonObject["bones"]!!.jsonObject.containsKey("head"))
    }
    @Test fun resetAfterClipAndClipClearsAnimation() { val c = AvatarController(); c.animate(AvatarAnimation(listOf(AvatarKeyframe(0, AvatarPose())), true)); c.playClip("x"); assertNull(c.state.value.animation); c.reset(); assertEquals("", c.state.value.clip) }
}
