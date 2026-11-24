package com.rishitgoklani.hermes_front_mobile.data.bridge

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.CapabilityRegistry
import com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiLog
import com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiSource
import com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Newline-delimited JSON protocol spoken to the Hermes `deskbot-phone` plugin.
 *   {"method":"list","token":T}                          -> {"tools":[{name,description,inputSchema}]}
 *   {"method":"call","name":N,"arguments":{},"token":T}  -> {"status":...,"message":...,"data":...}
 */
class BridgeProtocol(
    private val registry: CapabilityRegistry,
    private val isKilled: () -> Boolean = { false },
    /** Records every request Hermes makes to the phone, with its result, for the Debug screen. */
    private val log: ApiLog? = null,
    private val token: () -> String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun handle(line: String): String {
        val request = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull()
            ?: return error("bad_request", "Invalid JSON").also { log?.record(ApiSource.Bridge, "bad request", "", "Invalid JSON", ApiStatus.Error) }
        val expected = token()
        if (expected.isEmpty() || request["token"]?.jsonPrimitive?.contentOrNull != expected) {
            return error(ActionResult.DENIED, "Unauthorized").also { log?.record(ApiSource.Bridge, "unauthorized ${request["method"]?.jsonPrimitive?.contentOrNull.orEmpty()}", "", "Unauthorized", ApiStatus.Error) }
        }
        return when (request["method"]?.jsonPrimitive?.contentOrNull) {
            "list" -> list().also { log?.record(ApiSource.Bridge, "list", "", "${registry.list().size} tools", ApiStatus.Ok) }
            "call" -> if (isKilled()) error(ActionResult.DENIED, "Kill switch active: reset it on the phone").also { log?.record(ApiSource.Bridge, "call ${request["name"]?.jsonPrimitive?.contentOrNull.orEmpty()}", request["arguments"]?.toString().orEmpty(), "Kill switch active", ApiStatus.Error) } else call(request)
            else -> error("bad_request", "Unknown method")
        }
    }

    private fun list(): String = buildJsonObject {
        put("tools", buildJsonArray {
            registry.list().forEach { c ->
                add(buildJsonObject {
                    put("name", c.name)
                    put("description", c.description)
                    put("inputSchema", json.parseToJsonElement(c.inputSchemaJson))
                })
            }
        })
    }.toString()

    private suspend fun call(request: JsonObject): String {
        val name = request["name"]?.jsonPrimitive?.contentOrNull ?: return error("bad_request", "Missing name")
        val capability = registry.get(name) ?: return error(ActionResult.UNKNOWN_TOOL, "No tool $name").also { log?.record(ApiSource.Bridge, "call $name", request["arguments"]?.toString().orEmpty(), "No tool $name", ApiStatus.Error) }
        val args = (request["arguments"] as? JsonObject) ?: JsonObject(emptyMap())
        val entry = log?.begin(ApiSource.Bridge, "call $name", args.toString())
        val result = try {
            capability.executor.execute(args)
        } catch (e: kotlinx.coroutines.CancellationException) {
            entry?.let { log?.finish(it, ApiStatus.Error, "cancelled") }
            throw e
        } catch (e: Exception) {
            ActionResult.failed(e.message ?: e::class.simpleName.orEmpty())
        }
        entry?.let { log?.finish(it, if (result.status == ActionResult.COMPLETED || result.status == ActionResult.ACCEPTED) ApiStatus.Ok else ApiStatus.Error, "${result.status}: ${result.message}" + (result.data?.let { d -> "  $d" } ?: "")) }
        return buildJsonObject {
            put("status", result.status)
            put("message", result.message)
            put("data", result.data ?: JsonNull)
        }.toString()
    }

    private fun error(status: String, message: String): String =
        buildJsonObject { put("status", status); put("message", message) }.toString()
}
