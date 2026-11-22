package com.rishitgoklani.hermes_front_mobile.domain.avatar

import com.rishitgoklani.hermes_front_mobile.domain.face.Mood
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarTest {
    @Test fun sadSlouchesAndHappyRaisesArms() { assertTrue(AvatarMapper.pose(Mood.Sad).slouch > 0.5f); assertTrue(AvatarMapper.pose(Mood.Happy).armRaise > AvatarMapper.pose(Mood.Sad).armRaise) }
    @Test fun jsonIsValidAndClamped() {
        val o = Json.parseToJsonElement(AvatarMapper.toJson(AvatarState(mood = Mood.Happy, mouth = 3f, gazeX = -9f, action = AvatarAction.Dance))).jsonObject
        assertEquals("happy", o["mood"]!!.jsonPrimitive.content); assertEquals("1.000", o["mouth"]!!.jsonPrimitive.content); assertEquals("-1.000", o["gx"]!!.jsonPrimitive.content); assertEquals("dance", o["action"]!!.jsonPrimitive.content)
    }
    @Test fun controllerTracksState() { val c = AvatarController(); c.setMood(Mood.Love); c.setMouth(0.5f); c.act(AvatarAction.Wave); assertEquals(AvatarState(false, Mood.Love, 0.5f, 0f, 0f, AvatarAction.Wave), c.state.value); c.look(0.3f, null); assertEquals(0f, c.state.value.gazeY) }
    @Test fun toolSetsActionAndRejectsUnknown() = runBlocking {
        val c = AvatarController(); val cap = avatarCapability(c)
        assertEquals("completed", cap.executor.execute(buildJsonObject { put("action", "dance") }).status); assertEquals(AvatarAction.Dance, c.state.value.action)
        assertEquals("failed", cap.executor.execute(buildJsonObject { put("action", "twirl") }).status)
        assertEquals("failed", cap.executor.execute(JsonObject(emptyMap())).status)
        assertEquals("completed", cap.executor.execute(buildJsonObject { put("mode", "avatar") }).status); assertTrue(c.state.value.enabled)
        cap.executor.execute(buildJsonObject { put("mode", "eyes") }); assertTrue(!c.state.value.enabled)
        assertEquals("failed", cap.executor.execute(buildJsonObject { put("mode", "3d") }).status)
    }
}
