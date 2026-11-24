package com.rishitgoklani.hermes_front_mobile.engine.vrm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FramePacingTest {
    private val vsync = 16_670_000L
    private fun rendered(p: FramePacer, moving: Boolean, frames: Int, urgent: Boolean = false, jitter: Long = 0): Int {
        var n = 0; for (i in 0 until frames) if (p.shouldRender(moving, i * vsync + (if (i % 2 == 0) jitter else -jitter), urgent)) n++; return n
    }

    @Test fun movingDrawsEverySecondVsyncAndIdleEverySixth() {
        assertEquals(30, rendered(FramePacer(16.67f), true, 60)); assertEquals(10, rendered(FramePacer(16.67f), false, 60))
    }
    @Test fun scalesWithTheRefreshRate() { val p = FramePacer(8.33f); assertEquals(4, p.movingEvery); assertEquals(12, p.idleEvery) }
    @Test fun jitterDoesNotSkipOrDoubleFrames() { assertEquals(30, rendered(FramePacer(16.67f), true, 60, jitter = 4_000_000)) }
    @Test fun urgentWorkDrawsEveryVsync() { assertEquals(60, rendered(FramePacer(16.67f), false, 60, urgent = true)) }
    @Test fun firstFrameAlwaysDrawsAndResetRestartsTheCount() {
        val p = FramePacer(16.67f); assertTrue(p.shouldRender(false, 5)); assertFalse(p.shouldRender(false, 5 + vsync)); p.reset(); assertTrue(p.shouldRender(false, 6 + vsync))
    }
    @Test fun idleDelayLandsJustBeforeTheNextDraw() { assertEquals(83L, FramePacer(16.67f).idleDelayMs()) }

    @Test fun gateSleepsOnlyAfterAQuietPeriod() {
        val g = IdleGate(quietNanos = 1000); assertFalse(g.asleep(0)); g.update(true, 0)
        assertFalse(g.asleep(999)); g.update(true, 500); assertFalse(g.asleep(1499)); assertTrue(g.asleep(1500))
    }
    @Test fun touchWakesTheGateAndCanBeShorterThanTheQuietPeriod() {
        val g = IdleGate(quietNanos = 1000); g.touch(0); assertTrue(g.asleep(1000)); g.touch(2000, forNanos = 100); assertFalse(g.asleep(2050)); assertTrue(g.asleep(2100))
    }

    @Test fun workQueueRunsOneTaskPerFrameByDefault() {
        val q = GlWorkQueue { 0L }; val ran = ArrayList<Int>(); q.add { ran += 1 }; q.add { ran += 2 }
        assertTrue(q.hasWork); assertEquals(1, q.drain()); assertEquals(listOf(1), ran); assertEquals(1, q.drain()); assertFalse(q.hasWork); assertEquals(0, q.drain())
    }
    @Test fun workQueueStopsStartingTasksOnceTheBudgetIsSpent() {
        var now = 0L; val q = GlWorkQueue { now }; var ran = 0
        repeat(3) { q.add { now += 3_000_000; ran++ } }
        assertEquals(2, q.drain(budgetNanos = 4_000_000, maxTasks = 5)); assertEquals(2, ran); assertTrue(q.hasWork)
    }
    @Test fun aFailingTaskDoesNotStopTheQueue() {
        val q = GlWorkQueue { 0L }; var ok = false; q.add { error("boom") }; q.add { ok = true }
        assertEquals(2, q.drain(maxTasks = 5)); assertTrue(ok)
    }

    @Test fun trimLevelsMapToActions() {
        val normal = MemoryPolicy(false); val low = MemoryPolicy(true)
        assertEquals(TrimAction.NONE, normal.onTrim(5)); assertEquals(TrimAction.DROP_STAGED, normal.onTrim(10)); assertEquals(TrimAction.DROP_STAGED, normal.onTrim(15))
        assertEquals(TrimAction.DROP_STAGED, normal.onTrim(20)); assertEquals(TrimAction.RELEASE_ALL, low.onTrim(20)); assertEquals(TrimAction.RELEASE_ALL, normal.onTrim(40))
        assertEquals(30_000L, normal.backgroundDelayMs)
    }
}
