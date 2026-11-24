package com.rishitgoklani.hermes_front_mobile.domain.live

import com.rishitgoklani.hermes_front_mobile.domain.behavior.RunCapability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.CapabilityRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The real tool list of the phone (a snapshot of the bridge `list` answer) run through the declaration builder:
 * every tool must come out in the shape the Live server accepts, so a new tool with an awkward schema fails here and not on the phone.
 */
class RealToolSchemasTest {
    private val tools = Json.parseToJsonElement(javaClass.getResource("/bridge-tools.json")!!.readText()).jsonObject["tools"]!!.jsonArray.map { it.jsonObject }
    private val registry = CapabilityRegistry(tools.map { t -> Capability(t["name"]!!.jsonPrimitive.content, t["description"]!!.jsonPrimitive.content, t["inputSchema"].toString()) { ActionResult.completed("ok") } })
    private val decls = LiveToolBridge(registry, RunCapability(registry), emptySet()).declarations(all = true)

    private fun problems(n: JsonElement, path: String, out: MutableList<String>) {
        if (n !is JsonObject) return
        val type = (n["type"] as? JsonPrimitive)?.contentOrNull
        if (type == null) out += "$path: no type"
        if (type == "object" && (n["properties"] as? JsonObject).let { it == null || it.isEmpty() } && path.contains('.')) out += "$path: empty nested object"
        if (type == "array" && n["items"] == null) out += "$path: array without items"
        if (n["type"] is JsonArray) out += "$path: type list"
        if (listOf("anyOf", "oneOf", "allOf", "\$ref", "additionalProperties", "\$schema").any { it in n }) out += "$path: unsupported keyword"
        (n["properties"] as? JsonObject)?.forEach { (k, v) -> problems(v, "$path.$k", out) }
        n["items"]?.let { problems(it, "$path[]", out) }
        (n["required"] as? JsonArray)?.forEach { r -> if (r.jsonPrimitive.content !in ((n["properties"] as? JsonObject)?.keys ?: emptySet())) out += "$path: required '${r.jsonPrimitive.content}' is not a property" }
    }

    @Test fun theSnapshotCoversTheWholePhone() = assertTrue("snapshot has ${tools.size} tools", tools.size >= 150)

    @Test fun everyToolIsDeclaredExceptTheOneThatDoublesTheVoice() {
        assertEquals(tools.size - 1, decls.size)
        assertTrue(decls.none { it.name == "tts_say" })
    }

    @Test fun everyDeclaredSchemaFitsWhatTheServerAccepts() {
        val out = mutableListOf<String>()
        decls.forEach { d -> d.parametersJson?.let { problems(Json.parseToJsonElement(it), d.name, out) } }
        assertTrue(out.joinToString("\n"), out.isEmpty())
    }

    @Test fun namesAreUniqueAndSafeFunctionNames() {
        assertEquals(decls.size, decls.map { it.name }.toSet().size)
        assertTrue(decls.all { Regex("[A-Za-z_][A-Za-z0-9_]{0,63}").matches(it.name) })
    }

    @Test fun theWholeSetStaysWithinABudgetThatKeepsSetupFast() {
        val chars = decls.sumOf { it.name.length + it.description.length + (it.parametersJson?.length ?: 0) }
        assertTrue("declarations are $chars characters (~${chars / 4} tokens)", chars < 60_000)
    }
}
