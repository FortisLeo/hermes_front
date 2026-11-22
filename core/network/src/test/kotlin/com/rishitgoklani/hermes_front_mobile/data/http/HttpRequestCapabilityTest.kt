package com.rishitgoklani.hermes_front_mobile.data.http

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HttpRequestCapabilityTest {
    private lateinit var server: MockWebServer
    @Before fun up() { server = MockWebServer().also { it.start() } }
    @After fun down() = server.shutdown()
    private fun args(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject
    private fun url(path: String = "/") = server.url(path).toString()

    @Test fun getReturnsStatusAndBody() = runBlocking {
        server.enqueue(MockResponse().setBody("hello"))
        val r = httpRequestCapability().executor.execute(args("""{"url":"${url()}"}"""))
        assertEquals("completed", r.status); assertTrue(r.message.startsWith("HTTP 200")); assertTrue(r.message.endsWith("hello"))
    }
    @Test fun postSendsBodyAndHeaders() = runBlocking {
        server.enqueue(MockResponse().setBody("ok"))
        httpRequestCapability().executor.execute(args("""{"url":"${url("/x")}","method":"post","body":"{\"a\":1}","headers":{"Content-Type":"application/json","X-T":"v"}}"""))
        val req = server.takeRequest(); assertEquals("POST", req.method); assertEquals("v", req.getHeader("X-T")); assertEquals("""{"a":1}""", req.body.readUtf8())
    }
    @Test fun http404IsFailedButKeepsBody() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("nope"))
        val r = httpRequestCapability().executor.execute(args("""{"url":"${url()}"}"""))
        assertEquals("failed", r.status); assertTrue(r.message.contains("404"))
    }
    @Test fun fileSchemeDenied() = runBlocking { assertEquals("denied", httpRequestCapability().executor.execute(args("""{"url":"file:///etc/passwd"}""")).status) }
    @Test fun retriesAfterConnectionDrop() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)); server.enqueue(MockResponse().setBody("second"))
        val r = httpRequestCapability().executor.execute(args("""{"url":"${url()}","retries":2}""")); assertEquals("completed", r.status); assertTrue(r.message.endsWith("second"))
    }
    @Test fun unreachableReportsFailure() = runBlocking {
        val u = url(); server.shutdown()
        assertEquals("failed", httpRequestCapability().executor.execute(args("""{"url":"$u"}""")).status)
    }
    @Test fun bodyTruncatedTo64k() = runBlocking {
        server.enqueue(MockResponse().setBody("a".repeat(100_000)))
        val r = httpRequestCapability().executor.execute(args("""{"url":"${url()}"}""")); assertTrue(r.message.length < 65_000)
    }
}
