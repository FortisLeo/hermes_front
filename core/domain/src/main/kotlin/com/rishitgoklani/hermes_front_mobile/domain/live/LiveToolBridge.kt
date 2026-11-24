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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** A tool that only exists for the live conversation (ending it, looking through the camera, reading a skill); it never reaches Hermes. [maxResultChars] overrides the bridge's default cap for long answers. */
class LocalTool(val decl: LiveToolDecl, val maxResultChars: Int? = null, val handler: suspend (JsonObject) -> ActionResult)

/**
 * Adapter between the phone's capability registry and the model's function calling.
 * [declarations] offers every phone tool (or only the [essential] ones), minus [exclude]: each declaration costs prompt tokens, so "essential" is the lean mode.
 * Free-form object parameters cannot be declared (the server rejects empty object schemas), so they are offered as JSON strings and parsed back before the tool runs.
 * Calls honour [denied] (kill switch), only run what was declared, and are answered with a short, speakable result.
 */
class LiveToolBridge(
    private val registry: CapabilityRegistry,
    private val run: RunCapability,
    private val essential: Set<String>,
    private val local: List<LocalTool> = emptyList(),
    private val denied: () -> String? = { null },
    private val maxResultChars: Int = 1500,
    private val exclude: Set<String> = DEFAULT_EXCLUDED,
) {
    private val localByName = local.associateBy { it.decl.name }
    @Volatile private var allowed: Set<String> = essential
    /** Per tool: the parameters that were declared as strings but are really objects/arrays. */
    @Volatile private var jsonStrings: Map<String, Set<String>> = emptyMap()

    fun declarations(all: Boolean = true): List<LiveToolDecl> {
        val caps = registry.list().filter { it.name !in localByName && it.name !in exclude && (all || it.name in essential) }
        allowed = caps.map { it.name }.toSet()
        val prepared = caps.associate { it.name to prepare(it.inputSchemaJson) }
        jsonStrings = prepared.mapValues { it.value.second }
        return local.map { it.decl } + caps.map { LiveToolDecl(it.name, it.description.take(400), prepared.getValue(it.name).first) }
    }

    suspend fun execute(call: LiveCall): LiveResult {
        denied()?.let { return reply(call, ActionResult(ActionResult.DENIED, it), maxResultChars) }
        val tool = localByName[call.name]
        val result = tool?.let {
            try { it.handler(call.args) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { ActionResult.failed(e.message ?: "failed") }
        } ?: if (call.name in allowed) run(call.name, coerce(call.args, jsonStrings[call.name].orEmpty())) else ActionResult(ActionResult.UNKNOWN_TOOL, "No tool ${call.name}")
        return reply(call, result, tool?.maxResultChars ?: maxResultChars)
    }

    private fun reply(call: LiveCall, r: ActionResult, cap: Int) = LiveResult(call.id, call.name, buildJsonObject {
        put("status", r.status)
        if (r.message.isNotBlank()) put("message", r.message.take(cap))
        r.data?.let { d -> if (d.toString().length <= cap) put("data", d) }
    })

    /** Turns the JSON strings the model sent back into the objects and arrays the tool expects. */
    private fun coerce(args: JsonObject, props: Set<String>): JsonObject {
        if (props.isEmpty()) return args
        return JsonObject(args.mapValues { (k, v) ->
            if (k in props && v is JsonPrimitive && v.isString) runCatching { json.parseToJsonElement(v.content) }.getOrNull()?.takeIf { it is JsonObject || it is JsonArray } ?: v else v
        })
    }

    companion object {
        /** `tts_say` would make the phone speak with its own voice over the model's. */
        val DEFAULT_EXCLUDED = setOf("tts_say")
        private val KEEP = setOf("type", "description", "properties", "items", "enum", "required", "format", "nullable")
        private val json = Json { ignoreUnknownKeys = true }

        /** [sanitizeSchema] plus the names of parameters that had to become JSON strings. */
        fun prepare(schemaJson: String): Pair<String?, Set<String>> {
            val root = runCatching { json.parseToJsonElement(schemaJson).jsonObject }.getOrNull() ?: return null to emptySet()
            val props = root["properties"] as? JsonObject
            if (props == null || props.isEmpty()) return null to emptySet()
            val asStrings = mutableSetOf<String>()
            val fixed = JsonObject(props.mapValues { (name, p) ->
                if (hasFreeForm(p)) {
                    asStrings += name
                    val d = ((p as? JsonObject)?.get("description") as? JsonPrimitive)?.contentOrNull.orEmpty()
                    buildJsonObject { put("type", "string"); put("description", "$d Pass this as a JSON-encoded ${((p as? JsonObject)?.get("type") as? JsonPrimitive)?.contentOrNull ?: "value"}.".trim()) }
                } else clean(p)
            })
            val req = (root["required"] as? JsonArray)?.filter { (it as? JsonPrimitive)?.contentOrNull in props.keys }
            return buildJsonObject {
                put("type", "object"); put("properties", fixed)
                if (!req.isNullOrEmpty()) put("required", JsonArray(req))
            }.toString() to asStrings
        }

        /** Reduces a JSON Schema to the OpenAPI subset the model accepts. Returns null when the tool takes no arguments (an empty object schema is rejected). */
        fun sanitizeSchema(schemaJson: String): String? = prepare(schemaJson).first

        /** An object with no declared properties (headers, extras, bones...) anywhere inside this parameter. */
        private fun hasFreeForm(n: JsonElement): Boolean = when (n) {
            is JsonObject -> {
                val type = (n["type"] as? JsonPrimitive)?.contentOrNull
                val props = n["properties"] as? JsonObject
                (type == "object" && props.isNullOrEmpty()) || props?.values?.any(::hasFreeForm) == true || n["items"]?.let(::hasFreeForm) == true
            }
            else -> false
        }

        private fun clean(e: JsonElement): JsonElement = when (e) {
            is JsonObject -> {
                val kept = e.filterKeys { it in KEEP }.mapValues { (k, v) -> if (k == "properties" && v is JsonObject) JsonObject(v.mapValues { clean(it.value) }) else clean(v) }.toMutableMap()
                val type = (kept["type"] as? JsonPrimitive)?.contentOrNull
                if (type == "array" && "items" !in kept) kept["items"] = buildJsonObject { put("type", "string") }
                if (type == null && "properties" in kept) kept["type"] = JsonPrimitive("object")
                if (type == null && "properties" !in kept && "items" !in kept) kept["type"] = JsonPrimitive("string")
                JsonObject(kept)
            }
            is JsonArray -> JsonArray(e.map(::clean))
            is JsonPrimitive -> e
        }
    }
}
