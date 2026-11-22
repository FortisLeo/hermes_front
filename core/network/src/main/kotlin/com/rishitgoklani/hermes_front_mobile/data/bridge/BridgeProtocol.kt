package com.rishitgoklani.hermes_front_mobile.data.bridge

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.CapabilityRegistry
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
    private val token: () -> String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun handle(line: String): String {
        val request = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull()
            ?: return error("bad_request", "Invalid JSON")
        val expected = token()
        if (expected.isEmpty() || request["token"]?.jsonPrimitive?.contentOrNull != expected) {
            return error(ActionResult.DENIED, "Unauthorized")
        }
        return when (request["method"]?.jsonPrimitive?.contentOrNull) {
            "list" -> list()
            "call" -> if (isKilled()) error(ActionResult.DENIED, "Kill switch active: reset it on the phone") else call(request)
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
        val capability = registry.get(name) ?: return error(ActionResult.UNKNOWN_TOOL, "No tool $name")
        val args = (request["arguments"] as? JsonObject) ?: JsonObject(emptyMap())
        val result = try {
            capability.executor.execute(args)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            ActionResult.failed(e.message ?: e::class.simpleName.orEmpty())
        }
        return buildJsonObject {
            put("status", result.status)
            put("message", result.message)
            put("data", result.data ?: JsonNull)
        }.toString()
    }

    private fun error(status: String, message: String): String =
        buildJsonObject { put("status", status); put("message", message) }.toString()
}
