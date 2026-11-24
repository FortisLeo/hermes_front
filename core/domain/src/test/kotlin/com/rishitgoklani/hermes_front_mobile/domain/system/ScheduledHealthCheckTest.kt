package com.rishitgoklani.hermes_front_mobile.domain.system

import com.rishitgoklani.hermes_front_mobile.testing.FakeNotificationPoster
import com.rishitgoklani.hermes_front_mobile.testing.FakeSpeechSynthesizer
import com.rishitgoklani.hermes_front_mobile.testing.FakeStateStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduledHealthCheckTest {
    private val good = HealthScore.Input(90, true, 30.0, 50, true, 0)
    private val hot = good.copy(tempC = 47.0)
    private var input = good
    private val poster = FakeNotificationPoster(); private val tts = FakeSpeechSynthesizer()
    private val check = ScheduledHealthCheck({ input }, FakeStateStore(), poster, tts)
    private val min = 60_000L

    @Test fun disabledByDefaultDoesNothing() = runTest { input = hot; check.tick(0); assertTrue(poster.posted.isEmpty()) }

    @Test fun runsOnlyWhenIntervalElapsed() = runTest {
        check.configure(HealthCheckConfig(enabled = true, intervalMin = 10, minScore = 90)); input = hot
        check.tick(5 * min); assertEquals(1, poster.posted.size)
        check.tick(10 * min); assertEquals(5 * min, check.config().lastRunMs)
        check.tick(16 * min); assertEquals("ran again but same issue deduped", 1, poster.posted.size); assertEquals(16 * min, check.config().lastRunMs)
    }

    @Test fun alertsOnlyBelowThreshold() = runTest {
        check.configure(HealthCheckConfig(enabled = true, minScore = 70)); input = good
        check.tick(0); assertTrue(poster.posted.isEmpty()); assertEquals(100, check.config().lastScore)
    }

    @Test fun sameIssueRealertsAfterSixHoursAndNewIssueImmediately() = runTest {
        check.configure(HealthCheckConfig(enabled = true, intervalMin = 5, minScore = 90, speak = true)); input = hot
        check.run(0); check.run(min); assertEquals(1, poster.posted.size)
        check.run(6 * 3_600_000L + 1); assertEquals(2, poster.posted.size)
        input = hot.copy(killActive = true); check.run(6 * 3_600_000L + 2); assertEquals(3, poster.posted.size); assertEquals(3, tts.spoken.size)
    }

    @Test fun recoveryClearsIssueSoRelapseAlertsAgain() = runTest {
        check.configure(HealthCheckConfig(enabled = true, intervalMin = 5, minScore = 90)); input = hot
        check.run(0); input = good; check.run(min); input = hot; check.run(2 * min)
        assertEquals(2, poster.posted.size)
    }

    @Test fun configureValidatesRange() {
        assertTrue(runCatching { check.configure(HealthCheckConfig(intervalMin = 1)) }.isFailure)
        assertTrue(runCatching { check.configure(HealthCheckConfig(minScore = 0)) }.isFailure)
    }

    @Test fun toolEnableStatusRunDisable() = runTest {
        var now = 1_000L; val cap = healthCheckCapability(check) { now }
        suspend fun call(s: String) = cap.executor.execute(Json.parseToJsonElement(s).jsonObject)
        assertEquals("failed", call("""{"action":"enable","interval_min":1}""").status)
        assertTrue(call("""{"action":"enable","interval_min":30,"min_score":80}""").message.contains("30"))
        assertEquals(30, check.config().intervalMin); assertEquals(0L, check.config().lastRunMs)
        input = hot; assertTrue(call("""{"action":"run"}""").message.startsWith("70"))
        assertTrue(call("""{"action":"status"}""").message.contains("last 70"))
        assertTrue(call("""{"action":"disable"}""").message.contains("off"))
        assertEquals("failed", call("""{"action":"x"}""").status)
    }
}
