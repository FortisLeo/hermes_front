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

    @Test fun declaresOnlyTheHotTools() {
        assertEquals(listOf("battery", "torch"), bridge().declarations().map { it.name })
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
}
