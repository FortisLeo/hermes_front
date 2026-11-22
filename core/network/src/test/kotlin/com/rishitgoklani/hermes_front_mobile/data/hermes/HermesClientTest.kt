package com.rishitgoklani.hermes_front_mobile.data.hermes

import com.rishitgoklani.hermes_front_mobile.domain.HermesConfig
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class HermesClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: HermesClient

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        client = HermesClient({ HermesConfig(baseUrl = server.url("/").toString(), apiKey = "k") })
    }
    @After fun tearDown() = server.shutdown()

    private fun sse(vararg lines: String) = MockResponse().setBody(lines.joinToString("\n\n", postfix = "\n\n"))
        .setHeader("Content-Type", "text/event-stream")

    @Test fun streamsDeltasUntilDone() = runBlocking {
        server.enqueue(sse(
            """data: {"choices":[{"delta":{"role":"assistant"}}]}""",
            """data: {"choices":[{"delta":{"content":"Hi"}}]}""",
            """data: {"choices":[{"delta":{"content":" there"}}]}""",
            "data: [DONE]",
        ))
        assertEquals(listOf("Hi", " there"), client.send("yo").toList())
    }

    @Test fun sendsAuthSessionAndPrompt() = runBlocking {
        server.enqueue(sse("data: [DONE]"))
        client.send("yo").toList()
        val req = server.takeRequest()
        assertEquals("/v1/chat/completions", req.path)
        assertEquals("Bearer k", req.getHeader("Authorization"))
        assertEquals("deskbot-main", req.getHeader("X-Hermes-Session-Id"))
        assertTrue(req.body.readUtf8().contains("\"yo\""))
    }

    @Test fun httpErrorThrowsIOException() {
        server.enqueue(MockResponse().setResponseCode(401))
        assertThrows(IOException::class.java) { runBlocking { client.send("yo").toList() } }
    }

    @Test fun imageBodyUsesDataUriPart() {
        val body = client.buildBody("what", "QUJD", HermesConfig())
        val content = body["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray
        assertEquals("data:image/jpeg;base64,QUJD",
            content[1].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content)
    }
}

class HermesClientContextTest {
    @Test fun systemContextIsSentFirstWhenProvided() {
        val c = HermesClient({ HermesConfig() }, { "Monday 23 November 2026, 00:42" }); val m = c.buildBody("hi", null, HermesConfig())["messages"]!!.jsonArray
        assertEquals(2, m.size); assertEquals("system", m[0].jsonObject["role"]!!.jsonPrimitive.content); assertEquals("user", m[1].jsonObject["role"]!!.jsonPrimitive.content)
    }
    @Test fun noContextMeansOnlyUserMessage() = assertEquals(1, HermesClient({ HermesConfig() }).buildBody("hi", null, HermesConfig())["messages"]!!.jsonArray.size)
}
