package com.rishitgoklani.hermes_front_mobile.domain.power

import com.rishitgoklani.hermes_front_mobile.testing.FakeBattery
import com.rishitgoklani.hermes_front_mobile.testing.FakeNotificationPoster
import com.rishitgoklani.hermes_front_mobile.testing.FakeSpeechSynthesizer
import com.rishitgoklani.hermes_front_mobile.testing.FakeStateStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeCapTest {
    private val store = FakeStateStore(); private val guard = ChargeCapGuard(store)

    @Test fun offByDefaultNeverAlerts() = assertFalse(guard.shouldAlert(BatteryStatus(100, true)))

    @Test fun alertsOncePerChargeSession() {
        guard.setCap(80)
        assertFalse(guard.shouldAlert(BatteryStatus(79, true)))
        assertTrue(guard.shouldAlert(BatteryStatus(80, true)))
        assertFalse(guard.shouldAlert(BatteryStatus(83, true)))
    }

    @Test fun rearmsAfterUnplugAndAfterDrop() {
        guard.setCap(80); guard.shouldAlert(BatteryStatus(80, true))
        assertFalse(guard.shouldAlert(BatteryStatus(78, false))); assertTrue(guard.shouldAlert(BatteryStatus(81, true)))
        guard.shouldAlert(BatteryStatus(75, true)); assertTrue(guard.shouldAlert(BatteryStatus(80, true)))
    }

    @Test fun notChargingAboveCapDoesNotAlertAndUnknownLevelIgnored() {
        guard.setCap(80)
        assertFalse(guard.shouldAlert(BatteryStatus(95, false))); assertFalse(guard.shouldAlert(BatteryStatus(-1, true)))
    }

    @Test fun rejectsAbsurdCapAndSurvivesCorruptStore() {
        assertFalse(runCatching { guard.setCap(10) }.isSuccess)
        assertEquals(0, ChargeCapGuard(FakeStateStore(mutableMapOf("charge_cap" to "x"))).config().capPercent)
    }

    @Test fun taskPostsAndSpeaksOnce() = runTest {
        val poster = FakeNotificationPoster(); val tts = FakeSpeechSynthesizer(); val bat = FakeBattery(BatteryStatus(85, true))
        guard.setCap(80); val task = ChargeCapTask(guard, bat, poster, tts)
        task.tick(0); task.tick(1)
        assertEquals(1, poster.posted.size); assertTrue(tts.spoken.single().contains("85"))
    }

    @Test fun toolSetStatusOffAndValidation() = runTest {
        val bat = FakeBattery(BatteryStatus(60, true)); val poster = FakeNotificationPoster()
        val cap = chargeLimitCapability(guard, bat, ChargeCapTask(guard, bat, poster, FakeSpeechSynthesizer()))
        suspend fun call(s: String) = cap.executor.execute(Json.parseToJsonElement(s).jsonObject)
        assertEquals("failed", call("""{"action":"set","percent":20}""").status); assertEquals("failed", call("""{"action":"set"}""").status)
        assertEquals("completed", call("""{"action":"set","percent":55}""").status)
        assertEquals("completed", call("""{"action":"check"}""").status); assertEquals(1, poster.posted.size)
        assertTrue(call("""{"action":"status"}""").message.contains("55"))
        assertEquals("completed", call("""{"action":"off"}""").status); assertEquals(0, guard.config().capPercent)
        assertEquals("failed", call("""{"action":"x"}""").status)
    }
}
