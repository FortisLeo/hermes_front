package com.rishitgoklani.hermes_front_mobile.data.bridge

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.CapabilityRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeProtocolTest {
    private val echo = Capability("echo", "Echo text", """{"type":"object","properties":{"text":{"type":"string"}}}""") { args ->
        ActionResult.completed(args["text"]?.jsonPrimitive?.content.orEmpty())
    }
    private val boom = Capability("boom", "Always throws") { error("kaput") }
    private val protocol = BridgeProtocol(CapabilityRegistry(listOf(echo, boom))) { "tok" }

    private fun call(line: String) = Json.parseToJsonElement(runBlocking { protocol.handle(line) }).jsonObject
    private fun status(line: String) = call(line)["status"]?.jsonPrimitive?.content

    @Test fun listReturnsToolsWithSchema() {
        val tools = call("""{"method":"list","token":"tok"}""")["tools"]!!.jsonArray
        assertEquals(listOf("echo", "boom"), tools.map { it.jsonObject["name"]!!.jsonPrimitive.content })
        assertEquals("object", tools[0].jsonObject["inputSchema"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test fun callRunsExecutor() {
        val r = call("""{"method":"call","name":"echo","arguments":{"text":"hi"},"token":"tok"}""")
        assertEquals("completed", r["status"]!!.jsonPrimitive.content)
        assertEquals("hi", r["message"]!!.jsonPrimitive.content)
    }

    @Test fun wrongTokenDenied() = assertEquals("denied", status("""{"method":"list","token":"nope"}"""))
    @Test fun missingTokenDenied() = assertEquals("denied", status("""{"method":"list"}"""))
    @Test fun emptyConfiguredTokenDeniesEverything() {
        val p = BridgeProtocol(CapabilityRegistry()) { "" }
        assertEquals("denied", Json.parseToJsonElement(runBlocking { p.handle("""{"method":"list","token":""}""") })
            .jsonObject["status"]!!.jsonPrimitive.content)
    }
    @Test fun unknownToolReported() = assertEquals("unknown_tool", status("""{"method":"call","name":"x","token":"tok"}"""))
    @Test fun executorExceptionBecomesFailed() {
        val r = call("""{"method":"call","name":"boom","token":"tok"}""")
        assertEquals("failed", r["status"]!!.jsonPrimitive.content)
        assertEquals("kaput", r["message"]!!.jsonPrimitive.content)
    }
    @Test fun garbageIsBadRequest() = assertEquals("bad_request", status("not json"))
    @Test fun unknownMethodIsBadRequest() = assertEquals("bad_request", status("""{"method":"zap","token":"tok"}"""))
}

class BridgeKillGuardTest {
    private val reg = CapabilityRegistry(listOf(Capability("echo", "e") { ActionResult.completed("hi") }))
    @Test fun callsDeniedWhileKilledButListStillWorks() {
        var killed = false; val p = BridgeProtocol(reg, { killed }) { "tok" }
        fun run(line: String) = Json.parseToJsonElement(runBlocking { p.handle(line) }).jsonObject
        assertEquals("completed", run("""{"method":"call","name":"echo","token":"tok"}""")["status"]!!.jsonPrimitive.content)
        killed = true
        assertEquals("denied", run("""{"method":"call","name":"echo","token":"tok"}""")["status"]!!.jsonPrimitive.content)
        assertTrue(run("""{"method":"list","token":"tok"}""").containsKey("tools"))
        killed = false; assertEquals("completed", run("""{"method":"call","name":"echo","token":"tok"}""")["status"]!!.jsonPrimitive.content)
    }
}
