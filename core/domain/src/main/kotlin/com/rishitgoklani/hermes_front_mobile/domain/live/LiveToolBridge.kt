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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** A tool that only exists for the live conversation (ending it, looking through the camera); it never reaches Hermes. */
class LocalTool(val decl: LiveToolDecl, val handler: suspend (JsonObject) -> ActionResult)

/**
 * Adapter between the phone's capability registry and the model's function calling.
 * Only a small [hot] set is declared: every declaration costs prompt tokens on every turn, and the model needs to see the few it should actually use.
 * Calls honour [denied] (kill switch) and are answered with a short, speakable result.
 */
class LiveToolBridge(
    private val registry: CapabilityRegistry,
    private val run: RunCapability,
    private val hot: Set<String>,
    private val local: List<LocalTool> = emptyList(),
    private val denied: () -> String? = { null },
    private val maxResultChars: Int = 1500,
) {
    private val localByName = local.associateBy { it.decl.name }

    fun declarations(): List<LiveToolDecl> =
        local.map { it.decl } + registry.list().filter { it.name in hot && it.name !in localByName }.map(::declare)

    suspend fun execute(call: LiveCall): LiveResult {
        denied()?.let { return reply(call, ActionResult(ActionResult.DENIED, it)) }
        val result = localByName[call.name]?.let { tool ->
            try { tool.handler(call.args) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { ActionResult.failed(e.message ?: "failed") }
        } ?: if (call.name in hot) run(call.name, call.args) else ActionResult(ActionResult.UNKNOWN_TOOL, "No tool ${call.name}")
        return reply(call, result)
    }

    private fun reply(call: LiveCall, r: ActionResult) = LiveResult(call.id, call.name, buildJsonObject {
        put("status", r.status)
        if (r.message.isNotBlank()) put("message", r.message.take(maxResultChars))
        r.data?.let { d -> if (d.toString().length <= maxResultChars) put("data", d) }
    })

    private fun declare(c: Capability) = LiveToolDecl(c.name, c.description.take(400), sanitizeSchema(c.inputSchemaJson))

    companion object {
        private val KEEP = setOf("type", "description", "properties", "items", "enum", "required", "format", "nullable")
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Reduces a JSON Schema to the OpenAPI subset the model accepts (drops $schema, additionalProperties, defaults, ...).
         * Returns null when the tool takes no arguments: a declaration with an empty object schema is rejected by the server.
         */
        fun sanitizeSchema(schemaJson: String): String? {
            val root = runCatching { json.parseToJsonElement(schemaJson).jsonObject }.getOrNull() ?: return null
            val props = root["properties"] as? JsonObject
            if (props == null || props.isEmpty()) return null
            return clean(root).toString()
        }

        private fun clean(e: JsonElement): JsonElement = when (e) {
            is JsonObject -> JsonObject(e.filterKeys { it in KEEP }.mapValues { (k, v) -> if (k == "properties" && v is JsonObject) JsonObject(v.mapValues { clean(it.value) }) else clean(v) })
            is JsonArray -> JsonArray(e.map(::clean))
            is JsonPrimitive -> e
        }
    }
}
