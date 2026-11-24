package com.rishitgoklani.hermes_front_mobile.data.live

import com.rishitgoklani.hermes_front_mobile.domain.live.LiveConfig
import com.rishitgoklani.hermes_front_mobile.domain.live.LiveEvent
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Response
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
import java.io.IOException
import java.util.Base64
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class GeminiLiveClientTest {
    private lateinit var server: MockWebServer
    private val received = LinkedBlockingQueue<String>()
    private val cfg = LiveConfig("gemini-3.8-live", "Kore", "hi")

    @Before fun up() { server = MockWebServer(); server.start() }
    @After fun down() { server.shutdown() }

    /** A server that answers the setup with setupComplete (as a binary frame, like the real one) and records every client message. */
    private fun answering(onMessage: (WebSocket, String) -> Unit = { _, _ -> }) = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            received.add(text)
            if ("\"setup\"" in text) webSocket.send("""{"setupComplete":{}}""".encodeUtf8()) else onMessage(webSocket, text)
        }
    }
    private fun client(key: String = "SECRET-KEY", timeoutMs: Long = 3_000) = GeminiLiveClient(apiKey = { key }, endpoint = server.url("/ws").toString(), setupTimeoutMs = timeoutMs)

    @Test fun opensSendsSetupAndPutsTheKeyInTheUrl() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(answering()))
        client().open(cfg)
        assertTrue("\"model\":\"models/gemini-3.8-live\"" in received.poll(2, TimeUnit.SECONDS)!!)
        assertEquals("key=SECRET-KEY", server.takeRequest().requestUrl!!.query)
    }

    @Test fun serverEventsReachTheFlow() = runBlocking {
        val pcm = Base64.getEncoder().encodeToString(byteArrayOf(4, 4))
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                webSocket.send("""{"setupComplete":{}}""".encodeUtf8())
                webSocket.send("""{"serverContent":{"modelTurn":{"parts":[{"inlineData":{"data":"$pcm"}}]},"turnComplete":true}}""")
            }
        }))
        val c = client()
        val first = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(3_000) { c.events.first { it is LiveEvent.AudioOut } } }
        c.open(cfg)
        assertEquals(listOf<Byte>(4, 4), (first.await() as LiveEvent.AudioOut).pcm.toList())
    }

    @Test fun microphoneAndToolMessagesReachTheServer() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(answering()))
        val c = client(); c.open(cfg); received.poll(2, TimeUnit.SECONDS)
        c.sendAudio(byteArrayOf(1)); c.sendText("yo")
        assertTrue("\"audio\"" in received.poll(2, TimeUnit.SECONDS)!!)
        assertTrue("\"text\":\"yo\"" in received.poll(2, TimeUnit.SECONDS)!!)
    }

    @Test fun aRejectedKeyFailsOpenWithTheServersReasonAndNeverTheKey() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) { webSocket.close(1008, "API key not valid SECRET-KEY") }
        }))
        val e = runCatching { client().open(cfg) }.exceptionOrNull()
        assertTrue(e is IOException)
        assertTrue(e!!.message!!.contains("API key not valid"))
    }

    @Test fun redactHidesTheKeyInErrorText() {
        assertEquals("bad *** here", GeminiLiveClient.redact("bad SECRET-KEY here", "SECRET-KEY"))
    }

    @Test fun aServerThatNeverAnswersTimesOut() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() { override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) } }))
        val e = runCatching { client(timeoutMs = 300).open(cfg) }.exceptionOrNull()
        assertTrue(e is IOException && e.message!!.contains("did not answer"))
    }

    @Test fun anEmptyKeyFailsBeforeAnyNetworkCall() = runBlocking {
        val e = runCatching { client(key = " ").open(cfg) }.exceptionOrNull()
        assertTrue(e is IOException)
        assertEquals(0, server.requestCount)
    }

    @Test fun closingFromTheServerIsReportedAsClosed() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(answering { ws, _ -> ws.close(1000, "done") }))
        val c = client()
        val closed = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(3_000) { c.events.first { it is LiveEvent.Closed } } }
        c.open(cfg); c.sendText("x")
        val e = closed.await() as LiveEvent.Closed
        assertFalse(e.error)
    }
}
