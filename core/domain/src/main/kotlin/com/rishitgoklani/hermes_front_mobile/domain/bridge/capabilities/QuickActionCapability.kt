package com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities

import com.rishitgoklani.hermes_front_mobile.domain.behavior.QuickAction
import com.rishitgoklani.hermes_front_mobile.domain.behavior.QuickActionsRepository
import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.CapabilityRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Lets Hermes read/replace the buttons on the bot's quick-actions panel. Unknown tool names are refused up front. */
fun quickActionsCapability(repo: QuickActionsRepository, registry: () -> CapabilityRegistry) = Capability(
    "quick_actions", "List the quick-action buttons on the bot screen, or replace them with set=[{id,label,tool,args}].",
    """{"type":"object","properties":{"set":{"type":"array","items":{"type":"object"}}}}""",
) { a ->
    (a["set"] as? JsonArray)?.let { arr ->
        val parsed = runCatching { arr.map { Json { ignoreUnknownKeys = true }.decodeFromJsonElement(QuickAction.serializer(), it) } }.getOrElse { return@Capability ActionResult.failed("invalid action: ${it.message?.take(80)}") }
        parsed.firstOrNull { registry().get(it.tool) == null }?.let { return@Capability ActionResult.failed("unknown tool '${it.tool}' in action '${it.id}'") }
        if (parsed.size > 12) return@Capability ActionResult.failed("max 12 actions")
        repo.set(parsed)
    }
    val all = repo.all()
    ActionResult.completed(all.joinToString("\n") { "${it.id}: ${it.label} -> ${it.tool}" }, buildJsonObject { put("count", all.size); put("actions", buildJsonArray { all.forEach { add(buildJsonObject { put("id", it.id); put("label", it.label); put("tool", it.tool) }) } }) })
}
