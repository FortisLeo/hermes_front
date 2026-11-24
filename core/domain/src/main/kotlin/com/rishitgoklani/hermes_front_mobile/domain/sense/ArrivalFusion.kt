package com.rishitgoklani.hermes_front_mobile.domain.sense

import com.rishitgoklani.hermes_front_mobile.domain.routines.DeviceEvent

/** Evidence that someone is around. Weights: a seen face is strong, sound/motion/visual change are weak hints. */
enum class PresenceSignal(val weight: Int) { Face(3), Sound(1), Motion(1), Visual(1), Wifi(2) }

/**
 * Fuses camera, sound, motion and visual-change signals into arrive/depart events (#331).
 * Arrived when the recent evidence score reaches [arriveScore]; departed after [absentAfterMs] without any evidence.
 */
class ArrivalFusion(
    private val arriveScore: Int = 3, private val windowMs: Long = 60_000, private val absentAfterMs: Long = 10 * 60_000,
    private val clock: () -> Long = System::currentTimeMillis,
    private val wifiLostAbsentMs: Long = 2 * 60_000,
) {
    private val evidence = ArrayDeque<Pair<Long, PresenceSignal>>()
    var present = false; private set
    private var lastEvidence = 0L
    private var wifiLostAt: Long? = null

    /** Feed one signal; returns an event when the state flips. */
    fun signal(s: PresenceSignal): DeviceEvent.Presence? {
        val now = clock(); evidence.addLast(now to s); lastEvidence = now; wifiLostAt = null; trim(now)
        if (!present && evidence.sumOf { it.second.weight } >= arriveScore) { present = true; return DeviceEvent.Presence(true) }
        return null
    }

    /** The home Wi-Fi dropped (phone left range): departure is then declared after a much shorter quiet spell. */
    fun wifiLost() { wifiLostAt = clock() }

    /** Call periodically; returns a departure event after a long quiet spell (short after Wi-Fi loss). */
    fun tick(): DeviceEvent.Presence? {
        val now = clock(); trim(now)
        val limit = if (wifiLostAt != null) minOf(absentAfterMs, wifiLostAbsentMs) else absentAfterMs
        if (present && now - lastEvidence >= limit) { present = false; evidence.clear(); return DeviceEvent.Presence(false) }
        return null
    }

    private fun trim(now: Long) { while (evidence.isNotEmpty() && now - evidence.first().first > windowMs) evidence.removeFirst() }
}
