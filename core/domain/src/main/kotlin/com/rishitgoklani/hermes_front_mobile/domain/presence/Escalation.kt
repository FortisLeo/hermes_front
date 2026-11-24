package com.rishitgoklani.hermes_front_mobile.domain.presence

import com.rishitgoklani.hermes_front_mobile.domain.routines.DeviceEvent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * #336 Escalation: an important alert is repeated, louder each time, until the user acknowledges it
 * (speaks, picks the phone up, or knocks on it). Levels: speak -> speak + 70% volume + buzz -> 100% volume + long buzz.
 * Important speech passes the quiet policy (except during calls, where only the buzz is useful).
 */
class Escalation(
    private val clock: () -> Long = System::currentTimeMillis,
    private val repeatEveryMs: Long = 20_000,
    private val maxRepeats: Int = 6,
) : Behavior {
    override val id = "escalation"
    private class Active(val text: String, var level: Int, var nextAt: Long, var repeats: Int)
    private val active = LinkedHashMap<String, Active>()
    val activeIds: Set<String> get() = active.keys.toSet()

    /** Starts (or restarts) an alert and returns its first cues. */
    fun raise(id: String, text: String): List<Cue> {
        active[id] = Active(text, 1, clock() + repeatEveryMs, 1)
        return cuesFor(text, 1)
    }

    fun acknowledge(id: String? = null): Boolean = if (id == null) active.isNotEmpty().also { active.clear() } else active.remove(id) != null

    override suspend fun react(input: Input): List<Cue> = when (input) {
        is Input.Heard -> { acknowledge(); emptyList() }
        is Input.Device -> { if (input.event is DeviceEvent.Motion) acknowledge(); emptyList() }
        is Input.Tick -> tick()
        else -> emptyList()
    }

    private fun tick(): List<Cue> {
        val now = clock(); val out = mutableListOf<Cue>()
        val it = active.entries.iterator()
        while (it.hasNext()) {
            val a = it.next().value
            if (now < a.nextAt) continue
            if (a.repeats >= maxRepeats) { it.remove(); continue }
            a.level = (a.level + 1).coerceAtMost(3); a.repeats++; a.nextAt = now + repeatEveryMs
            out += cuesFor(a.text, a.level)
        }
        return out
    }

    private fun cuesFor(text: String, level: Int): List<Cue> = when (level) {
        1 -> listOf(Cue.Speak(text, Urgency.Important))
        2 -> listOf(volume(70), Cue.Call("vibrate", buildJsonObject { put("duration_ms", 600) }), Cue.Speak("Reminder: $text", Urgency.Important))
        else -> listOf(volume(100), Cue.Call("vibrate", buildJsonObject { put("pattern", JsonArray(listOf(0L, 500L, 200L, 500L, 200L, 900L).map { JsonPrimitive(it) })) }), Cue.Speak("Important: $text", Urgency.Important))
    }

    private fun volume(pct: Int) = Cue.Call("volume", buildJsonObject { put("level", pct) })
}
