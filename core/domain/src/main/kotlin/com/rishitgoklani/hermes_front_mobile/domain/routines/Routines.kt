package com.rishitgoklani.hermes_front_mobile.domain.routines

import com.rishitgoklani.hermes_front_mobile.domain.behavior.RunCapability
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Things that happen on the phone that routines can react to. */
sealed interface DeviceEvent {
    val type: String
    data class ChargerConnected(val level: Int = -1) : DeviceEvent { override val type = "charger_connected" }
    data class ChargerDisconnected(val level: Int = -1) : DeviceEvent { override val type = "charger_disconnected" }
    data class WifiConnected(val ssid: String) : DeviceEvent { override val type = "wifi_connected" }
    data class WifiDisconnected(val ssid: String = "") : DeviceEvent { override val type = "wifi_disconnected" }
    data class BatteryLow(val level: Int) : DeviceEvent { override val type = "battery_low" }
    data class NotificationPosted(val app: String, val title: String, val text: String) : DeviceEvent { override val type = "notification" }
    data class ScreenOn(val unused: Boolean = true) : DeviceEvent { override val type = "screen_on" }
    data class ScreenOff(val unused: Boolean = true) : DeviceEvent { override val type = "screen_off" }
    data class AppForeground(val pkg: String) : DeviceEvent { override val type = "app_foreground" }
    data class Motion(val kind: String) : DeviceEvent { override val type = "motion" }
    data class Place(val name: String, val entered: Boolean) : DeviceEvent { override val type = "place" }
    data class Clipboard(val text: String) : DeviceEvent { override val type = "clipboard" }
    data class VisualChange(val score: Int) : DeviceEvent { override val type = "visual_change" }
    data class Sound(val label: String, val dbfs: Int) : DeviceEvent { override val type = "sound" }
}

@Serializable data class ToolCall(val tool: String, val args: JsonObject = JsonObject(emptyMap()))

/** Trigger: [event] is a DeviceEvent.type; optional filters narrow it (substring, case-insensitive). */
@Serializable data class Trigger(val event: String, val app: String? = null, val contains: String? = null, val ssid: String? = null, val pkg: String? = null, val kind: String? = null, val place: String? = null, val below: Int? = null)

@Serializable data class Routine(val id: String, val name: String, val trigger: Trigger, val actions: List<ToolCall>, val cooldownMs: Long = 30_000, val enabled: Boolean = true)

interface RoutineRepository {
    suspend fun all(): List<Routine>
    suspend fun save(routine: Routine)
    suspend fun delete(id: String): Boolean
}

object TriggerMatcher {
    fun matches(t: Trigger, e: DeviceEvent): Boolean {
        if (t.event != e.type) return false
        fun has(hay: String, needle: String?) = needle == null || hay.contains(needle, true)
        return when (e) {
            is DeviceEvent.NotificationPosted -> has(e.app, t.app) && has("${e.title} ${e.text}", t.contains)
            is DeviceEvent.WifiConnected -> has(e.ssid, t.ssid)
            is DeviceEvent.AppForeground -> has(e.pkg, t.pkg)
            is DeviceEvent.Motion -> t.kind == null || e.kind.equals(t.kind, true)
            is DeviceEvent.Place -> has(e.name, t.place)
            is DeviceEvent.Clipboard -> has(e.text, t.contains)
            is DeviceEvent.Sound -> t.kind == null || e.label.equals(t.kind, true)
            is DeviceEvent.BatteryLow -> t.below == null || e.level <= t.below
            else -> true
        }
    }
}

/** Runs matching routines' tool calls through the same capability path as Hermes; cooldown prevents loops. */
class RoutineEngine(private val repo: RoutineRepository, private val run: RunCapability, private val clock: () -> Long = System::currentTimeMillis, private val audit: com.rishitgoklani.hermes_front_mobile.domain.bridge.shell.AuditLog? = null) {
    private val lastRun = HashMap<String, Long>()

    suspend fun onEvent(event: DeviceEvent): List<String> {
        val ran = mutableListOf<String>()
        for (r in repo.all()) {
            if (!r.enabled || !TriggerMatcher.matches(r.trigger, event)) continue
            val now = clock(); val last = lastRun[r.id]
            if (last != null && now - last < r.cooldownMs) continue
            lastRun[r.id] = now
            val statuses = r.actions.map { run(it.tool, it.args).status }
            audit?.record("routine:${r.id}", event.type, statuses.joinToString(","))
            ran += r.id
        }
        return ran
    }
}
