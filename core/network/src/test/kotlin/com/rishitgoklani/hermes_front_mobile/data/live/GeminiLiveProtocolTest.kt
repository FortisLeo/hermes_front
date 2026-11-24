package com.rishitgoklani.hermes_front_mobile.data.live

import com.rishitgoklani.hermes_front_mobile.domain.live.LiveCall
import com.rishitgoklani.hermes_front_mobile.domain.live.LiveConfig
import com.rishitgoklani.hermes_front_mobile.domain.live.LiveEvent
import com.rishitgoklani.hermes_front_mobile.domain.live.LiveResult
import com.rishitgoklani.hermes_front_mobile.domain.live.LiveToolDecl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class GeminiLiveProtocolTest {
    private fun obj(s: String) = Json.parseToJsonElement(s).jsonObject
    private val cfg = LiveConfig("gemini-3.8-live", "Kore", "be brief", listOf(LiveToolDecl("torch", "Flashlight", """{"type":"object","properties":{"on":{"type":"boolean"}}}"""), LiveToolDecl("battery", "Battery", nonBlocking = true)))

    @Test fun setupNamesTheModelVoiceAndPrompt() {
        val s = obj(GeminiLiveProtocol.setup(cfg))["setup"]!!.jsonObject
        assertEquals("models/gemini-3.8-live", s["model"]!!.jsonPrimitive.content)
        assertEquals("AUDIO", s["generationConfig"]!!.jsonObject["responseModalities"]!!.jsonArray[0].jsonPrimitive.content)
        assertEquals("Kore", s["generationConfig"]!!.jsonObject["speechConfig"]!!.jsonObject["voiceConfig"]!!.jsonObject["prebuiltVoiceConfig"]!!.jsonObject["voiceName"]!!.jsonPrimitive.content)
        assertEquals("be brief", s["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
    }

    @Test fun anExplicitModelsPrefixIsKept() {
        assertEquals("models/x", obj(GeminiLiveProtocol.setup(cfg.copy(model = "models/x")))["setup"]!!.jsonObject["model"]!!.jsonPrimitive.content)
    }

    @Test fun toolsAreDeclaredAsFunctionsWithOptionalParametersAndBehavior() {
        val decls = obj(GeminiLiveProtocol.setup(cfg))["setup"]!!.jsonObject["tools"]!!.jsonArray[0].jsonObject["functionDeclarations"]!!.jsonArray
        val torch = decls[0].jsonObject; val battery = decls[1].jsonObject
        assertTrue("parameters" in torch); assertFalse("parameters" in battery)
        assertFalse("behavior" in torch); assertEquals("NON_BLOCKING", battery["behavior"]!!.jsonPrimitive.content)
    }

    @Test fun setupEnablesTranscriptionResumptionAndCompression() {
        val s = obj(GeminiLiveProtocol.setup(cfg.copy(resumeHandle = "h9")))["setup"]!!.jsonObject
        assertTrue("inputAudioTranscription" in s && "outputAudioTranscription" in s)
        assertEquals("h9", s["sessionResumption"]!!.jsonObject["handle"]!!.jsonPrimitive.content)
        assertTrue("slidingWindow" in s["contextWindowCompression"]!!.jsonObject)
    }

    @Test fun noToolsMeansNoToolsBlockAndNoBargeInMeansNoInterruption() {
        val s = obj(GeminiLiveProtocol.setup(cfg.copy(tools = emptyList(), allowBargeIn = false)))["setup"]!!.jsonObject
        assertFalse("tools" in s)
        assertEquals("NO_INTERRUPTION", s["realtimeInputConfig"]!!.jsonObject["activityHandling"]!!.jsonPrimitive.content)
    }

    @Test fun microphoneAudioIsBase64WithTheSixteenKilohertzMime() {
        val a = obj(GeminiLiveProtocol.audio(byteArrayOf(1, 2, 3)))["realtimeInput"]!!.jsonObject["audio"]!!.jsonObject
        assertEquals("audio/pcm;rate=16000", a["mimeType"]!!.jsonPrimitive.content)
        assertEquals(listOf<Byte>(1, 2, 3), Base64.getDecoder().decode(a["data"]!!.jsonPrimitive.content).toList())
    }

    @Test fun cameraFramesAreJpegVideoInput() {
        assertEquals("image/jpeg", obj(GeminiLiveProtocol.frame(byteArrayOf(7)))["realtimeInput"]!!.jsonObject["video"]!!.jsonObject["mimeType"]!!.jsonPrimitive.content)
    }

    @Test fun toolResponsesCarryTheCallId() {
        val r = obj(GeminiLiveProtocol.toolResponses(listOf(LiveResult("c1", "torch", buildJsonObject { put("status", JsonPrimitive("completed")) }))))["toolResponse"]!!.jsonObject["functionResponses"]!!.jsonArray[0].jsonObject
        assertEquals("c1", r["id"]!!.jsonPrimitive.content); assertEquals("torch", r["name"]!!.jsonPrimitive.content)
    }

    @Test fun setupCompleteIsRecognised() {
        assertTrue(GeminiLiveProtocol.parse("""{"setupComplete":{}}""").setupComplete)
    }

    @Test fun parsesAudioAndTranscriptionsInOrder() {
        val pcm = Base64.getEncoder().encodeToString(byteArrayOf(9, 8))
        val p = GeminiLiveProtocol.parse("""{"serverContent":{"inputTranscription":{"text":"hi"},"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"$pcm"}}]},"outputTranscription":{"text":"hello"}}}""")
        assertEquals(3, p.events.size)
        assertEquals(LiveEvent.InputText("hi"), p.events[0])
        assertEquals(listOf<Byte>(9, 8), (p.events[1] as LiveEvent.AudioOut).pcm.toList())
        assertEquals(LiveEvent.OutputText("hello"), p.events[2])
    }

    @Test fun parsesInterruptionAndTurnComplete() {
        assertEquals(listOf(LiveEvent.Interrupted), GeminiLiveProtocol.parse("""{"serverContent":{"interrupted":true}}""").events)
        assertEquals(listOf(LiveEvent.TurnComplete), GeminiLiveProtocol.parse("""{"serverContent":{"turnComplete":true,"generationComplete":true}}""").events)
    }

    @Test fun parsesToolCallsWithArguments() {
        val e = GeminiLiveProtocol.parse("""{"toolCall":{"functionCalls":[{"id":"f1","name":"torch","args":{"on":true}}]}}""").events.single() as LiveEvent.ToolCall
        assertEquals(LiveCall("f1", "torch", buildJsonObject { put("on", JsonPrimitive(true)) }), e.calls.single())
    }

    @Test fun parsesToolCancellation() {
        assertEquals(listOf(LiveEvent.ToolCancel(listOf("a", "b"))), GeminiLiveProtocol.parse("""{"toolCallCancellation":{"ids":["a","b"]}}""").events)
    }

    @Test fun parsesGoAwayDurations() {
        assertEquals(listOf(LiveEvent.GoAway(50_000)), GeminiLiveProtocol.parse("""{"goAway":{"timeLeft":"50s"}}""").events)
        assertEquals(1500, GeminiLiveProtocol.durationMs("1.5s")); assertEquals(0, GeminiLiveProtocol.durationMs(null))
    }

    @Test fun onlyUsableResumptionHandlesAreReported() {
        assertEquals(listOf(LiveEvent.Resumable("h")), GeminiLiveProtocol.parse("""{"sessionResumptionUpdate":{"newHandle":"h","resumable":true}}""").events)
        assertTrue(GeminiLiveProtocol.parse("""{"sessionResumptionUpdate":{"newHandle":"","resumable":false}}""").events.isEmpty())
    }

    @Test fun garbageAndUnknownMessagesAreIgnored() {
        assertTrue(GeminiLiveProtocol.parse("not json").events.isEmpty())
        assertTrue(GeminiLiveProtocol.parse("""{"usageMetadata":{"totalTokenCount":5}}""").events.isEmpty())
    }
}
