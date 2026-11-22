package com.rishitgoklani.hermes_front_mobile.domain.voice

import com.rishitgoklani.hermes_front_mobile.domain.RunVoiceTurn
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.wakeCapability
import com.rishitgoklani.hermes_front_mobile.testing.FakeHermesGateway
import com.rishitgoklani.hermes_front_mobile.testing.FakeKeywordSpotter
import com.rishitgoklani.hermes_front_mobile.testing.FakeSpeechSynthesizer
import com.rishitgoklani.hermes_front_mobile.testing.FakeSpeechToText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeCapabilityTest {
    private fun a(v: String) = JsonObject(mapOf("action" to JsonPrimitive(v)))
    private fun make(): Pair<WakeController, com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability> {
        val t = FakeSpeechSynthesizer(); val loop = ConversationLoop(FakeKeywordSpotter(), RunVoiceTurn(FakeSpeechToText(), FakeHermesGateway(), t), t)
        val c = WakeController(loop, CoroutineScope(Dispatchers.Unconfined)); return c to wakeCapability(c) { true }
    }
    @Test fun onOffStatus() = runBlocking {
        val (c, cap) = make()
        assertEquals("completed", cap.executor.execute(a("on")).status); assertTrue(c.enabled.value)
        assertTrue(cap.executor.execute(a("status")).data!!["enabled"]!!.jsonPrimitive.content.toBoolean())
        cap.executor.execute(a("off")); assertFalse(c.enabled.value); assertEquals("Off", cap.executor.execute(a("status")).message)
    }
    @Test fun badActionFails() = runBlocking { assertEquals("failed", make().second.executor.execute(a("sing")).status) }
}
