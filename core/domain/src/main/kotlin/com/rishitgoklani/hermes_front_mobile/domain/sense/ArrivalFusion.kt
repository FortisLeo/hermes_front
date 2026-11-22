package com.rishitgoklani.hermes_front_mobile.domain.sense

import com.rishitgoklani.hermes_front_mobile.domain.routines.DeviceEvent

/** Evidence that someone is around. Weights: a seen face is strong, sound/motion/visual change are weak hints. */
enum class PresenceSignal(val weight: Int) { Face(3), Sound(1), Motion(1), Visual(1) }

/**
 * Fuses camera, sound, motion and visual-change signals into arrive/depart events (#331).
 * Arrived when the recent evidence score reaches [arriveScore]; departed after [absentAfterMs] without any evidence.
 */
class ArrivalFusion(
    private val arriveScore: Int = 3, private val windowMs: Long = 60_000, private val absentAfterMs: Long = 10 * 60_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val evidence = ArrayDeque<Pair<Long, PresenceSignal>>()
    var present = false; private set
    private var lastEvidence = 0L

    /** Feed one signal; returns an event when the state flips. */
    fun signal(s: PresenceSignal): DeviceEvent.Presence? {
        val now = clock(); evidence.addLast(now to s); lastEvidence = now; trim(now)
        if (!present && evidence.sumOf { it.second.weight } >= arriveScore) { present = true; return DeviceEvent.Presence(true) }
        return null
    }

    /** Call periodically; returns a departure event after a long quiet spell. */
    fun tick(): DeviceEvent.Presence? {
        val now = clock(); trim(now)
        if (present && now - lastEvidence >= absentAfterMs) { present = false; evidence.clear(); return DeviceEvent.Presence(false) }
        return null
    }

    private fun trim(now: Long) { while (evidence.isNotEmpty() && now - evidence.first().first > windowMs) evidence.removeFirst() }
}
