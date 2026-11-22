package com.rishitgoklani.hermes_front_mobile.domain.face

import com.rishitgoklani.hermes_front_mobile.domain.routines.DeviceEvent

/** Greets on arrival (#325): remembers when the user left and builds the greeting from the time away. Null = stay quiet. */
class ArrivalGreeter(private val clock: () -> Long = System::currentTimeMillis, private val hour: () -> Int) {
    private var departedAt: Long? = null

    fun onEvent(e: DeviceEvent): String? {
        if (e !is DeviceEvent.Presence) return null
        val now = clock()
        if (!e.arrived) { departedAt = now; return null }
        val away = departedAt?.let { now - it } ?: 60 * 60_000L  // first arrival since launch counts as "a while"
        departedAt = null
        return Greetings.greeting(hour(), away)
    }
}
