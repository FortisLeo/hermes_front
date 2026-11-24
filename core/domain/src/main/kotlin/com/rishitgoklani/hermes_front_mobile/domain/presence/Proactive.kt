package com.rishitgoklani.hermes_front_mobile.domain.presence

import com.rishitgoklani.hermes_front_mobile.domain.routines.DeviceEvent

data class UpcomingEvent(val id: String, val title: String, val startMs: Long)

/** Port: the next calendar event starting within [withinMs] from now, or null. */
fun interface UpcomingEvents { suspend fun next(withinMs: Long): UpcomingEvent? }

/**
 * #332 Proactive suggestions: a meeting in 10 minutes, a low battery, or a late night. Each suggestion has a
 * cooldown so the bot offers once instead of nagging. Spoken suggestions are Normal urgency, so quiet context holds them.
 */
class ProactiveSuggestions(
    private val calendar: UpcomingEvents,
    private val clock: () -> Long = System::currentTimeMillis,
    private val hour: () -> Int,
    private val meetingLeadMs: Long = 10 * 60_000L,
    private val lateFromHour: Int = 23,
    private val lateUntilHour: Int = 5,
    private val lateCooldownMs: Long = 4 * 3600_000L,
    private val batteryCooldownMs: Long = 60 * 60_000L,
) : Behavior {
    override val id = "proactive-suggestions"
    private val told = HashMap<String, Long>()

    private fun due(key: String, cooldownMs: Long): Boolean {
        val now = clock(); val last = told[key]
        if (last != null && now - last < cooldownMs) return false
        told[key] = now; return true
    }

    override suspend fun react(input: Input): List<Cue> = when (input) {
        is Input.Device -> (input.event as? DeviceEvent.BatteryLow)?.let { e ->
            if (due("battery", batteryCooldownMs)) listOf(Cue.Speak("Battery is at ${e.level} percent. Want me to turn on battery saver?")) else emptyList()
        }.orEmpty()
        is Input.Tick -> tick()
        else -> emptyList()
    }

    private suspend fun tick(): List<Cue> {
        val cues = mutableListOf<Cue>()
        calendar.next(meetingLeadMs)?.let { ev ->
            if (due("meeting:${ev.id}:${ev.startMs}", Long.MAX_VALUE / 2)) {
                val mins = ((ev.startMs - clock()) / 60_000L).coerceAtLeast(1)
                cues += Cue.Speak("${ev.title.ifBlank { "Your meeting" }} starts in $mins minutes.", Urgency.Important)
            }
        }
        val h = hour()
        val late = if (lateFromHour > lateUntilHour) h >= lateFromHour || h < lateUntilHour else h in lateFromHour until lateUntilHour
        if (late && due("late", lateCooldownMs)) cues += Cue.Speak("It is getting late. Want me to set an alarm for tomorrow?", Urgency.Low)
        return cues
    }
}
