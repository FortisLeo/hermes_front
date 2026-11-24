package com.rishitgoklani.hermes_front_mobile.domain.routines

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.notifications.NotificationRecord
import com.rishitgoklani.hermes_front_mobile.domain.notifications.NotificationSource
import com.rishitgoklani.hermes_front_mobile.domain.state.StateJson
import com.rishitgoklani.hermes_front_mobile.domain.state.StateStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Serializable data class FocusState(val untilMs: Long = 0, val allow: List<String> = emptyList())

/** Blocked-notification filter for focus sessions (#228). Stored with an end time so it expires even if the process restarted. */
class FocusFilter(private val store: StateStore, private val clock: () -> Long = System::currentTimeMillis) {
    private val key = "focus_filter"
    fun state(): FocusState = runCatching { StateJson.decodeFromString(FocusState.serializer(), store.load(key) ?: "") }.getOrDefault(FocusState())
    fun active() = state().untilMs > clock()
    fun start(minutes: Int, allow: List<String>) = store.save(key, StateJson.encodeToString(FocusState.serializer(), FocusState(clock() + minutes * 60_000L, allow.map { it.trim() }.filter { it.isNotEmpty() })))
    fun stop() = store.save(key, StateJson.encodeToString(FocusState.serializer(), FocusState()))
    /** Blocked while active unless the app label or package matches an allowed name. */
    fun blocks(r: NotificationRecord): Boolean {
        val s = state(); if (s.untilMs <= clock()) return false
        return s.allow.none { r.app.contains(it, true) || r.pkg.contains(it, true) }
    }
}

/** Dismisses blocked notifications as they arrive during a focus session. Register as a DeviceEvent observer. */
class FocusNotificationGuard(private val filter: FocusFilter, private val source: NotificationSource, private val ownPackage: String) {
    suspend fun onEvent(e: DeviceEvent): Int {
        if (e !is DeviceEvent.NotificationPosted || !filter.active()) return 0
        return source.active().filter { !it.ongoing && it.pkg != ownPackage && it.app == e.app && it.title == e.title && filter.blocks(it) }.count { source.dismiss(it.key) }
    }
}

fun focusFilterCapability(filter: FocusFilter) = Capability("focus_filter",
    "Focus notification filter: action=on (minutes 1-180, allow = app names that may still notify) | off | status. While on, notifications from other apps are dismissed.",
    """{"type":"object","properties":{"action":{"type":"string","enum":["on","off","status"]},"minutes":{"type":"integer"},"allow":{"type":"array","items":{"type":"string"}}},"required":["action"]}""") { a: JsonObject ->
    fun status(msg: String) = ActionResult.completed(msg, buildJsonObject { put("active", filter.active()); put("allow", filter.state().allow.joinToString()) })
    when (a["action"]?.jsonPrimitive?.contentOrNull) {
        "on" -> {
            val m = a["minutes"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 25
            if (m !in 1..180) return@Capability ActionResult.failed("minutes must be 1-180")
            filter.start(m, (a["allow"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()); status("Focus filter on for $m minutes")
        }
        "off" -> { filter.stop(); status("Focus filter off") }
        "status" -> status(if (filter.active()) "Focus filter on" else "Focus filter off")
        else -> ActionResult.failed("action must be on, off or status")
    }
}
