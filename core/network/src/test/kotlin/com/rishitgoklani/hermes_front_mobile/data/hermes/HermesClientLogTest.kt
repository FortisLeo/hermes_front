package com.rishitgoklani.hermes_front_mobile.data.hermes

import com.rishitgoklani.hermes_front_mobile.domain.HermesConfig
import com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiSource
import com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiStatus
import com.rishitgoklani.hermes_front_mobile.domain.diagnostics.InMemoryApiLog
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HermesClientLogTest {
    private lateinit var server: MockWebServer
    private val log = InMemoryApiLog(secrets = { listOf("hermes-secret-key") })
    private lateinit var client: HermesClient

    @Before fun up() { server = MockWebServer().also { it.start() }; client = HermesClient({ HermesConfig(baseUrl = server.url("/").toString(), apiKey = "hermes-secret-key") }, log = log) }
    @After fun down() = server.shutdown()

    private fun sse(vararg lines: String) = MockResponse().setBody(lines.joinToString("\n\n", postfix = "\n\n")).setHeader("Content-Type", "text/event-stream")

    @Test fun recordsTheRequestBodyAndTheWholeStreamedReply() = runBlocking {
        server.enqueue(sse("""data: {"choices":[{"delta":{"content":"Hi"}}]}""", """data: {"choices":[{"delta":{"content":" there"}}]}""", "data: [DONE]"))
        client.send("what is up").toList()
        val e = log.entries.value.single()
        assertEquals(ApiSource.Hermes, e.source); assertEquals("POST /v1/chat/completions", e.label)
        assertTrue("what is up" in e.request); assertEquals("Hi there", e.response); assertEquals(ApiStatus.Ok, e.status); assertTrue(e.durationMs != null)
    }

    @Test fun cameraImagesInTheRequestAreCollapsedNotStored() = runBlocking {
        server.enqueue(sse("data: [DONE]"))
        client.send("look", "A".repeat(50_000)).toList()
        val req = log.entries.value.single().request
        assertTrue("<data uri" in req); assertTrue(req.length < 1_000)
    }

    @Test fun theApiKeyNeverAppearsInTheLog() = runBlocking {
        server.enqueue(sse("data: [DONE]")); client.send("hello hermes-secret-key").toList()
        assertFalse(log.entries.value.single().request.contains("hermes-secret-key"))
    }

    @Test fun anHttpErrorIsRecordedAsAFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        runCatching { client.send("x").toList() }
        val e = log.entries.value.single(); assertEquals(ApiStatus.Error, e.status); assertTrue("500" in e.response)
    }
}
