package com.rishitgoklani.hermes_front_mobile.domain.face

import com.rishitgoklani.hermes_front_mobile.domain.routines.DeviceEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArrivalGreeterTest {
    private var now = 0L
    private fun greeter(hour: Int = 9) = ArrivalGreeter({ now }, { hour })

    @Test fun firstArrivalGreetsByTimeOfDay() { assertEquals("Good morning.", greeter(9).onEvent(DeviceEvent.Presence(true))) }
    @Test fun shortAbsenceStaysQuiet() { val g = greeter(); g.onEvent(DeviceEvent.Presence(false)); now = 10 * 60_000; assertNull(g.onEvent(DeviceEvent.Presence(true))) }
    @Test fun longAbsenceSaysWelcomeBack() { val g = greeter(18); g.onEvent(DeviceEvent.Presence(false)); now = 8 * 3600_000L; assertEquals("Good evening. Welcome back.", g.onEvent(DeviceEvent.Presence(true))) }
    @Test fun departureAndOtherEventsAreSilent() { val g = greeter(); assertNull(g.onEvent(DeviceEvent.Presence(false))); assertNull(g.onEvent(DeviceEvent.ScreenOn())) }
}
