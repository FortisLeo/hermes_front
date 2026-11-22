package com.rishitgoklani.hermes_front_mobile.domain.sense

import com.rishitgoklani.hermes_front_mobile.domain.routines.DeviceEvent
import com.rishitgoklani.hermes_front_mobile.domain.routines.Trigger
import com.rishitgoklani.hermes_front_mobile.domain.routines.TriggerMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrivalFusionTest {
    private var now = 0L
    private fun fusion() = ArrivalFusion(clock = { now })

    @Test fun aSeenFaceAloneMeansArrived() { assertEquals(DeviceEvent.Presence(true), fusion().signal(PresenceSignal.Face)) }
    @Test fun weakHintsNeedToAddUp() {
        val f = fusion(); assertNull(f.signal(PresenceSignal.Sound)); assertNull(f.signal(PresenceSignal.Motion)); assertEquals(DeviceEvent.Presence(true), f.signal(PresenceSignal.Visual))
    }
    @Test fun oldHintsExpire() {
        val f = fusion(); f.signal(PresenceSignal.Sound); f.signal(PresenceSignal.Motion); now = 120_000; assertNull(f.signal(PresenceSignal.Visual))
    }
    @Test fun arrivalFiresOnlyOnce() { val f = fusion(); f.signal(PresenceSignal.Face); assertNull(f.signal(PresenceSignal.Face)) }
    @Test fun departsAfterQuietSpell() {
        val f = fusion(); f.signal(PresenceSignal.Face); now = 5 * 60_000; assertNull(f.tick()); now = 11 * 60_000
        assertEquals(DeviceEvent.Presence(false), f.tick()); assertFalse(f.present)
    }
    @Test fun canArriveAgainAfterDeparting() { val f = fusion(); f.signal(PresenceSignal.Face); now = 11 * 60_000; f.tick(); assertTrue(f.signal(PresenceSignal.Face) != null) }
    @Test fun triggerKindFiltersArrivedVsDeparted() {
        assertTrue(TriggerMatcher.matches(Trigger("presence", kind = "arrived"), DeviceEvent.Presence(true)))
        assertFalse(TriggerMatcher.matches(Trigger("presence", kind = "arrived"), DeviceEvent.Presence(false)))
        assertTrue(TriggerMatcher.matches(Trigger("presence"), DeviceEvent.Presence(false)))
    }
}
