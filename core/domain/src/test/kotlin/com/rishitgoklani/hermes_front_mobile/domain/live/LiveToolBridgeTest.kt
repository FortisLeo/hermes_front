package com.rishitgoklani.hermes_front_mobile.domain.live

import com.rishitgoklani.hermes_front_mobile.domain.behavior.RunCapability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.CapabilityRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json

class LiveToolBridgeTest {
    private val battery = Capability("battery", "Battery level", executor = { ActionResult.completed("85 percent", buildJsonObject { put("percent", JsonPrimitive(85)) }) })
    private val torch = Capability("torch", "Flashlight", """{"type":"object","properties":{"on":{"type":"boolean","description":"state"}},"required":["on"],"additionalProperties":false,"${'$'}schema":"x"}""", { ActionResult.completed("torch ${it["on"]}") })
    private val secret = Capability("shell", "Run anything", executor = { ActionResult.completed("ran") })
    private val registry = CapabilityRegistry(listOf(battery, torch, secret))
    private fun bridge(denied: () -> String? = { null }, local: List<LocalTool> = emptyList()) =
        LiveToolBridge(registry, RunCapability(registry), setOf("battery", "torch"), local, denied)

    @Test fun declaresOnlyTheEssentialToolsInLeanMode() {
        assertEquals(listOf("battery", "torch"), bridge().declarations(all = false).map { it.name })
    }

    @Test fun toolWithoutArgumentsHasNoParametersBlock() {
        assertNull(bridge().declarations().first { it.name == "battery" }.parametersJson)
    }

    @Test fun schemaIsReducedToTheSubsetTheServerAccepts() {
        val params = Json.parseToJsonElement(bridge().declarations().first { it.name == "torch" }.parametersJson!!).jsonObject
        assertTrue("additionalProperties" !in params && "\$schema" !in params)
        assertEquals("boolean", params["properties"]!!.jsonObject["on"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertTrue("required" in params)
    }

    @Test fun executesAHotToolAndReturnsItsMessageAndData() = runTest {
        val r = bridge().execute(LiveCall("1", "battery", JsonObject(emptyMap())))
        assertEquals("completed", r.response["status"]!!.jsonPrimitive.content)
        assertEquals("85 percent", r.response["message"]!!.jsonPrimitive.content)
        assertEquals("1", r.id)
        assertTrue(r.response["data"] != null)
    }

    @Test fun refusesToolsOutsideTheHotSet() = runTest {
        val r = bridge().execute(LiveCall("2", "shell", JsonObject(emptyMap())))
        assertEquals(ActionResult.UNKNOWN_TOOL, r.response["status"]!!.jsonPrimitive.content)
    }

    @Test fun killSwitchDeniesEveryCall() = runTest {
        val r = bridge(denied = { "Kill switch active" }).execute(LiveCall("3", "battery", JsonObject(emptyMap())))
        assertEquals(ActionResult.DENIED, r.response["status"]!!.jsonPrimitive.content)
    }

    @Test fun localToolsAreDeclaredFirstAndRunWithoutTheRegistry() = runTest {
        val local = LocalTool(LiveToolDecl("ping", "p")) { ActionResult.completed("pong") }
        val b = bridge(local = listOf(local))
        assertEquals("ping", b.declarations().first().name)
        assertEquals("pong", b.execute(LiveCall("4", "ping", JsonObject(emptyMap()))).response["message"]!!.jsonPrimitive.content)
    }

    @Test fun aThrowingToolBecomesAFailedResultNotACrash() = runTest {
        val boom = LocalTool(LiveToolDecl("boom", "b")) { error("no") }
        assertEquals(ActionResult.FAILED, bridge(local = listOf(boom)).execute(LiveCall("5", "boom", JsonObject(emptyMap()))).response["status"]!!.jsonPrimitive.content)
    }

    @Test fun declaresEveryToolByDefaultExceptTheOnesThatWouldDoubleUp() {
        val reg = CapabilityRegistry(listOf(battery, torch, secret, Capability("tts_say", "Speak with the phone voice", executor = { ActionResult.completed("") })))
        val names = LiveToolBridge(reg, RunCapability(reg), setOf("battery")).declarations().map { it.name }
        assertEquals(listOf("battery", "torch", "shell"), names)
    }

    @Test fun everyToolThatWasDeclaredCanRunAndNothingElse() = runTest {
        val b = bridge(); b.declarations(all = true)
        assertEquals("completed", b.execute(LiveCall("1", "shell", JsonObject(emptyMap()))).response["status"]!!.jsonPrimitive.content)
        assertEquals(ActionResult.UNKNOWN_TOOL, b.execute(LiveCall("2", "tts_say", JsonObject(emptyMap()))).response["status"]!!.jsonPrimitive.content)
    }

    @Test fun leanModeRefusesToolsItDidNotDeclare() = runTest {
        val b = bridge(); b.declarations(all = false)
        assertEquals(ActionResult.UNKNOWN_TOOL, b.execute(LiveCall("1", "shell", JsonObject(emptyMap()))).response["status"]!!.jsonPrimitive.content)
    }

    private val freeForm = Capability("http_request", "HTTP", """{"type":"object","properties":{"url":{"type":"string"},"headers":{"type":"object","description":"extra headers"},"shapes":{"type":"array","items":{"type":"object"}}},"required":["url","headers"]}""") { a ->
        ActionResult.completed("headers=${a["headers"]!!::class.simpleName} shapes=${a["shapes"]!!::class.simpleName} url=${a["url"]!!::class.simpleName}")
    }
    private fun freeBridge(): LiveToolBridge { val reg = CapabilityRegistry(listOf(freeForm)); return LiveToolBridge(reg, RunCapability(reg), emptySet()) }

    @Test fun freeFormObjectsAreDeclaredAsJsonStringsBecauseEmptyObjectSchemasAreRejected() {
        val p = Json.parseToJsonElement(freeBridge().declarations().single().parametersJson!!).jsonObject["properties"]!!.jsonObject
        assertEquals("string", p["headers"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("string", p["shapes"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("string", p["url"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertTrue("JSON-encoded object" in p["headers"]!!.jsonObject["description"]!!.jsonPrimitive.content)
    }

    @Test fun theJsonStringsAreParsedBackIntoObjectsAndArraysBeforeTheToolRuns() = runTest {
        val b = freeBridge(); b.declarations()
        val args = buildJsonObject { put("url", JsonPrimitive("http://x")); put("headers", JsonPrimitive("{\"A\":\"b\"}")); put("shapes", JsonPrimitive("[{\"k\":1}]")) }
        val msg = b.execute(LiveCall("1", "http_request", args)).response["message"]!!.jsonPrimitive.content
        assertEquals("headers=JsonObject shapes=JsonArray url=JsonLiteral", msg)
    }

    @Test fun anInvalidJsonStringIsPassedThroughForTheToolToReject() = runTest {
        val b = freeBridge(); b.declarations()
        val args = buildJsonObject { put("url", JsonPrimitive("u")); put("headers", JsonPrimitive("not json")); put("shapes", JsonPrimitive("[]")) }
        assertTrue("headers=JsonLiteral" in b.execute(LiveCall("1", "http_request", args)).response["message"]!!.jsonPrimitive.content)
    }

    @Test fun requiredNamesThatDoNotExistAreDropped() {
        val s = Json.parseToJsonElement(LiveToolBridge.sanitizeSchema("""{"type":"object","properties":{"a":{"type":"string"}},"required":["a","ghost"]}""")!!).jsonObject
        assertEquals(listOf("a"), s["required"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test fun anArrayWithoutItemsAndATypelessPropertyAreRepaired() {
        val p = Json.parseToJsonElement(LiveToolBridge.sanitizeSchema("""{"type":"object","properties":{"list":{"type":"array"},"x":{"description":"anything"}}}""")!!).jsonObject["properties"]!!.jsonObject
        assertEquals("string", p["list"]!!.jsonObject["items"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("string", p["x"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test fun aLocalToolCanReturnALongerAnswerThanTheDefaultCap() = runTest {
        val long = LocalTool(LiveToolDecl("guide", "g"), maxResultChars = 5_000) { ActionResult.completed("x".repeat(4_000)) }
        val msg = bridge(local = listOf(long)).execute(LiveCall("1", "guide", JsonObject(emptyMap()))).response["message"]!!.jsonPrimitive.content
        assertEquals(4_000, msg.length)
        val short = LocalTool(LiveToolDecl("small", "s")) { ActionResult.completed("x".repeat(4_000)) }
        assertEquals(1_500, bridge(local = listOf(short)).execute(LiveCall("2", "small", JsonObject(emptyMap()))).response["message"]!!.jsonPrimitive.content.length)
    }
}
