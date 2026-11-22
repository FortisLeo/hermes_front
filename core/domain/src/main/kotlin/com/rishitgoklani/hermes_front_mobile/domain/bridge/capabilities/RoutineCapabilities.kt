package com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.CapabilityRegistry
import com.rishitgoklani.hermes_front_mobile.domain.routines.Routine
import com.rishitgoklani.hermes_front_mobile.domain.routines.RoutineRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private val EVENTS = setOf("charger_connected", "charger_disconnected", "wifi_connected", "wifi_disconnected", "battery_low", "notification", "screen_on", "screen_off", "app_foreground", "motion", "place", "clipboard", "visual_change", "sound")

fun routineCapabilities(repo: RoutineRepository, registry: () -> CapabilityRegistry): List<Capability> = listOf(
    Capability("routine_add", "Create/replace an event routine: {id,name,trigger:{event,app?,contains?,ssid?,pkg?,kind?,place?,below?},actions:[{tool,args}],cooldownMs?}. Events: ${EVENTS.joinToString()}.",
        """{"type":"object","properties":{"routine":{"type":"object"}},"required":["routine"]}""") { a ->
        val r = runCatching { Json { ignoreUnknownKeys = true }.decodeFromJsonElement(Routine.serializer(), a["routine"] ?: return@Capability ActionResult.failed("routine required")) }
            .getOrElse { return@Capability ActionResult.failed("invalid routine: ${it.message?.take(80)}") }
        if (!Regex("^[a-z0-9][a-z0-9-]{1,40}$").matches(r.id)) return@Capability ActionResult.failed("id must be lowercase letters/digits/dashes")
        if (r.trigger.event !in EVENTS) return@Capability ActionResult.failed("unknown event '${r.trigger.event}'")
        if (r.actions.isEmpty() || r.actions.size > 8) return@Capability ActionResult.failed("1-8 actions required")
        r.actions.firstOrNull { registry().get(it.tool) == null }?.let { return@Capability ActionResult.failed("unknown tool '${it.tool}'") }
        if (r.actions.any { it.tool == "shell" }) return@Capability ActionResult(ActionResult.DENIED, "routines may not run shell commands")
        if (r.cooldownMs < 1000) return@Capability ActionResult.failed("cooldownMs must be at least 1000")
        repo.save(r); ActionResult.completed("Saved routine ${r.id}")
    },
    Capability("routine_list", "List routines.") {
        val all = repo.all()
        ActionResult.completed(if (all.isEmpty()) "No routines" else all.joinToString("\n") { "${if (it.enabled) "on " else "off"} ${it.id}: ${it.name} when ${it.trigger.event} -> ${it.actions.joinToString { a -> a.tool }}" })
    },
    Capability("routine_remove", "Delete a routine by id.", """{"type":"object","properties":{"id":{"type":"string"}},"required":["id"]}""") { a ->
        if (repo.delete(a["id"]?.jsonPrimitive?.contentOrNull.orEmpty())) ActionResult.completed("Removed") else ActionResult.failed("No such routine")
    },
)
