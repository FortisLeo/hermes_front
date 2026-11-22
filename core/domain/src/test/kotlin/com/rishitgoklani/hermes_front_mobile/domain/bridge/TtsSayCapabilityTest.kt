package com.rishitgoklani.hermes_front_mobile.domain.bridge

import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.ttsSayCapability
import com.rishitgoklani.hermes_front_mobile.testing.FakeSpeechSynthesizer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class TtsSayCapabilityTest {
    @Test fun speaksText() = runBlocking {
        val tts = FakeSpeechSynthesizer()
        val r = ttsSayCapability(tts).executor.execute(JsonObject(mapOf("text" to JsonPrimitive(" hi "))))
        assertEquals("completed", r.status); assertEquals(listOf("hi"), tts.spoken)
    }
    @Test fun missingTextFails() = runBlocking {
        val tts = FakeSpeechSynthesizer()
        assertEquals("failed", ttsSayCapability(tts).executor.execute(JsonObject(emptyMap())).status)
        assertEquals(emptyList<String>(), tts.spoken)
    }
}
