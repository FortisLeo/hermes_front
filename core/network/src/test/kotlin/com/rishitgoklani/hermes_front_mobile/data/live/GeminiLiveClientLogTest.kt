package com.rishitgoklani.hermes_front_mobile.data.live

import com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiStatus
import com.rishitgoklani.hermes_front_mobile.domain.diagnostics.InMemoryApiLog
import com.rishitgoklani.hermes_front_mobile.domain.live.LiveConfig
import com.rishitgoklani.hermes_front_mobile.domain.live.LiveEvent
import com.rishitgoklani.hermes_front_mobile.domain.live.LiveResult
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString.Companion.encodeUtf8
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Base64

class GeminiLiveClientLogTest {
    private lateinit var server: MockWebServer
    private var now = 0L
    private val log = InMemoryApiLog(clock = { now }, secrets = { listOf("SECRET-KEY") })
    private val cfg = LiveConfig("gemini-3.8-live", "Kore", "hi")
    @Before fun up() { server = MockWebServer().also { it.start() } }
    @After fun down() = server.shutdown()
    private fun client() = GeminiLiveClient(apiKey = { "SECRET-KEY" }, endpoint = server.url("/ws").toString(), setupTimeoutMs = 2_000, log = log, clock = { now })

    private fun listener(script: (WebSocket) -> Unit = {}) = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            if ("\"setup\"" in text) { webSocket.send("""{"setupComplete":{}}""".encodeUtf8()); script(webSocket) }
        }
    }

    @Test fun openingIsLoggedWithTheSetupAndConfirmedOnSetupComplete() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(listener())); client().open(cfg)
        val e = log.entries.value.last { it.label.startsWith("open") }
        assertEquals("open gemini-3.8-live", e.label); assertTrue("models/gemini-3.8-live" in e.request)
        assertEquals(ApiStatus.Ok, e.status); assertEquals("setupComplete", e.response)
    }

    @Test fun aRejectedSetupIsAnErrorEntryWithoutTheKey() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) { webSocket.close(1008, "API key not valid SECRET-KEY") }
        }))
        runCatching { client().open(cfg) }
        val e = log.entries.value.last { it.label.startsWith("open") }
        assertEquals(ApiStatus.Error, e.status); assertTrue("API key not valid" in e.response)
        assertTrue(log.entries.value.none { it.request.contains("SECRET-KEY") || it.response.contains("SECRET-KEY") })
    }

    @Test fun aToolCallAndItsResultShareOneEntry() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(listener { it.send("""{"toolCall":{"functionCalls":[{"id":"c1","name":"battery","args":{"x":1}}]}}""") }))
        val c = client()
        val seen = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(3_000) { c.events.first { it is LiveEvent.ToolCall } } }
        c.open(cfg); seen.await()
        val pending = log.entries.value.first { it.label == "toolCall battery" }
        assertEquals(ApiStatus.Pending, pending.status); assertTrue("\"x\":1" in pending.request)
        c.sendToolResponses(listOf(LiveResult("c1", "battery", buildJsonObject { put("status", JsonPrimitive("completed")); put("message", JsonPrimitive("85%")) })))
        val done = log.entries.value.first { it.label == "toolCall battery" }
        assertEquals(ApiStatus.Ok, done.status); assertTrue("85%" in done.response)
    }

    @Test fun aFailedToolResultIsMarkedAsAnError() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(listener { it.send("""{"toolCall":{"functionCalls":[{"id":"c2","name":"torch","args":{}}]}}""") }))
        val c = client(); val seen = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(3_000) { c.events.first { it is LiveEvent.ToolCall } } }
        c.open(cfg); seen.await()
        c.sendToolResponses(listOf(LiveResult("c2", "torch", buildJsonObject { put("status", JsonPrimitive("failed")) })))
        assertEquals(ApiStatus.Error, log.entries.value.first { it.label == "toolCall torch" }.status)
    }

    @Test fun audioIsSummarisedInOneEntryNotLoggedPerChunk() = runBlocking {
        val pcm = Base64.getEncoder().encodeToString(ByteArray(4800))
        server.enqueue(MockResponse().withWebSocketUpgrade(listener { ws -> repeat(5) { ws.send("""{"serverContent":{"modelTurn":{"parts":[{"inlineData":{"data":"$pcm"}}]}}}""") } }))
        val c = client(); val got = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(3_000) { var n = 0; c.events.first { it is LiveEvent.AudioOut && ++n == 5 } } }
        c.open(cfg); got.await()
        repeat(3) { c.sendAudio(ByteArray(1280)) }; now += 1_500; c.sendAudio(ByteArray(1280))
        val audio = log.entries.value.filter { it.label == "audio stream" }
        assertEquals(1, audio.size)
        assertTrue("4 chunks" in audio.single().request); assertTrue("5 chunks" in audio.single().response)
    }

    @Test fun closingIsLogged() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(listener { it.close(1000, "done") }))
        val c = client(); val closed = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(3_000) { c.events.first { it is LiveEvent.Closed } } }
        c.open(cfg); closed.await()
        assertTrue(log.entries.value.any { it.label == "closed" && "done" in it.response })
        assertFalse(log.entries.value.any { it.label.startsWith("open") && it.status == ApiStatus.Error })
    }
}
