package com.rishitgoklani.hermes_front_mobile.domain.sense

import com.rishitgoklani.hermes_front_mobile.domain.power.BatteryStatus
import com.rishitgoklani.hermes_front_mobile.domain.routines.DeviceEvent
import com.rishitgoklani.hermes_front_mobile.testing.FakeBattery
import com.rishitgoklani.hermes_front_mobile.testing.FakePlaceRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofencePollerTest {
    private val home = Place("home", "Home", 12.9716, 77.5946, 100.0)
    private val ok = BatteryStatus(80, false)
    private val events = mutableListOf<DeviceEvent>()
    private var fix: Pair<Double, Double>? = 12.9716 to 77.5946
    private var enabled = true
    private val battery = FakeBattery(ok)
    private fun poller(repo: FakePlaceRepository = FakePlaceRepository(listOf(home)), locate: suspend () -> Pair<Double, Double>? = { fix }, tracker: PlaceTracker = PlaceTracker(), emit: suspend (DeviceEvent) -> Unit = { events += it }) =
        GeofencePoller(repo, locate, battery, tracker, { enabled }, emit)

    // Policy
    @Test fun noPlacesIsIdle() = assertEquals(GeofencePolicy.IDLE_MS, GeofencePolicy.nextDelayMs(null, ok, 0))
    @Test fun pollsFasterNearAPlaceThanFar() {
        val near = GeofencePolicy.nextDelayMs(50.0, ok, 0); val mid = GeofencePolicy.nextDelayMs(1_000.0, ok, 0); val far = GeofencePolicy.nextDelayMs(50_000.0, ok, 0)
        assertTrue(near < mid && mid < far)
    }
    @Test fun lowBatterySlowsAndChargingSpeedsUp() {
        val base = GeofencePolicy.nextDelayMs(1_000.0, ok, 0)
        assertEquals(base * 2, GeofencePolicy.nextDelayMs(1_000.0, BatteryStatus(25, false), 0))
        assertEquals(base * 3, GeofencePolicy.nextDelayMs(1_000.0, BatteryStatus(10, false), 0))
        assertEquals(base / 2, GeofencePolicy.nextDelayMs(1_000.0, BatteryStatus(10, true), 0))
        assertEquals(base, GeofencePolicy.nextDelayMs(1_000.0, BatteryStatus(-1, false), 0))
    }
    @Test fun failuresBackOffAndAreCapped() {
        val d = (0..8).map { GeofencePolicy.nextDelayMs(1_000.0, ok, it) }
        assertTrue(d[1] == d[0] * 2 && d[2] == d[0] * 4); assertEquals(d[4], d[8]); assertTrue(d.all { it in GeofencePolicy.MIN_MS..GeofencePolicy.MAX_MS })
    }
    @Test fun delayNeverBelowMinimumOrAboveMax() {
        assertEquals(GeofencePolicy.MIN_MS, GeofencePolicy.nextDelayMs(-500.0, BatteryStatus(100, true), 0))
        assertEquals(GeofencePolicy.MAX_MS, GeofencePolicy.nextDelayMs(90_000.0, BatteryStatus(5, false), 4))
    }
    @Test fun fixValidation() {
        assertTrue(GeofencePolicy.validFix(12.0, 77.0))
        listOf(Double.NaN to 1.0, 91.0 to 0.0, 0.0 to 181.0, 0.0 to 0.0).forEach { assertTrue("$it", !GeofencePolicy.validFix(it.first, it.second)) }
    }

    // Poller
    @Test fun emitsEnterOnceAndStaysQuiet() = runTest {
        val p = poller(); p.pollOnce(); p.pollOnce()
        assertEquals(listOf(DeviceEvent.Place("Home", true)), events); assertTrue(p.pollOnce() <= 60_000L * 1)
    }
    @Test fun exitNeedsHysteresis() = runTest {
        val p = poller(); p.pollOnce()
        fix = 12.9716 + 0.0012 to 77.5946 // about 133 m: outside 100 m radius but inside 130 m hysteresis? 133 > 130 so exits
        p.pollOnce(); assertEquals(DeviceEvent.Place("Home", false), events.last())
        events.clear(); fix = 12.9716 + 0.0002 to 77.5946; p.pollOnce(); assertEquals(DeviceEvent.Place("Home", true), events.single())
    }
    @Test fun jitterInsideHysteresisBandDoesNotFlap() = runTest {
        val p = poller(); p.pollOnce(); events.clear()
        fix = 12.9716 + 0.00105 to 77.5946 // about 117 m: in the band
        p.pollOnce(); assertTrue(events.isEmpty())
    }
    @Test fun nullAndInvalidFixesBackOffThenRecover() = runTest {
        val p = poller(); fix = null
        val d1 = p.pollOnce(); val d2 = p.pollOnce(); assertTrue(d2 > d1); assertEquals(2, p.failures)
        fix = 0.0 to 0.0; p.pollOnce(); assertEquals(3, p.failures); assertTrue(events.isEmpty())
        fix = 12.9716 to 77.5946; p.pollOnce(); assertEquals(0, p.failures); assertEquals(1, events.size)
    }
    @Test fun locationExceptionIsTreatedAsFailure() = runTest {
        val p = poller(locate = { error("gps off") }); p.pollOnce(); assertEquals(1, p.failures)
    }
    @Test fun repositoryFailureDoesNotCrash() = runTest {
        val p = poller(FakePlaceRepository(listOf(home), failing = true)); assertTrue(p.pollOnce() >= GeofencePolicy.MIN_MS); assertEquals(1, p.failures)
    }
    @Test fun noPlacesSkipsLocationAndClearsInsideState() = runTest {
        var asked = 0; val tracker = PlaceTracker(); val repo = FakePlaceRepository(listOf(home))
        val p = poller(repo, { asked++; fix }, tracker); p.pollOnce(); assertEquals(setOf("home"), tracker.current())
        repo.items.clear(); asked = 0; assertEquals(GeofencePolicy.IDLE_MS, p.pollOnce()); assertEquals(0, asked); assertTrue(tracker.current().isEmpty())
    }
    @Test fun disabledByKillSwitchDoesNotLocate() = runTest {
        var asked = 0; enabled = false; val p = poller(locate = { asked++; fix }); assertEquals(GeofencePolicy.IDLE_MS, p.pollOnce()); assertEquals(0, asked)
    }
    @Test fun failingConsumerDoesNotStopPolling() = runTest {
        val p = poller(emit = { error("boom") }); p.pollOnce(); assertEquals(0, p.failures)
    }
    @Test fun lowBatteryStretchesNextPoll() = runTest {
        val normal = poller().pollOnce(); battery.status = BatteryStatus(10, false); val low = poller().pollOnce(); assertTrue(low > normal)
    }
}
