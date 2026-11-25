package com.rishitgoklani.hermes_front_mobile.engine.vrm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class PreloadSlotTest {
    private val direct = Executor { it.run() }
    private val discarded = ArrayList<String>()
    private var clock = 0L
    private fun slot(ttl: Long = 1000) = PreloadSlot<String>({ discarded += it }, ttl) { clock }

    @Test fun theMatchingKeyGetsTheResultOnce() {
        val s = slot(); s.start("a", direct) { "model-a" }
        assertEquals("model-a", s.take("a", 0)); assertNull(s.take("a", 0)); assertEquals(emptyList<String>(), discarded)
    }

    @Test fun anotherKeyGetsNothingAndTheHeldResultIsFreed() {
        val s = slot(); s.start("a", direct) { "model-a" }
        assertNull(s.take("b", 0)); assertEquals(listOf("model-a"), discarded); assertNull(s.heldKey)
    }

    @Test fun startingAnotherKeyFreesThePreviousResult() {
        val s = slot(); s.start("a", direct) { "model-a" }; s.start("b", direct) { "model-b" }
        assertEquals(listOf("model-a"), discarded); assertEquals("model-b", s.take("b", 0))
    }

    @Test fun startingTheSameKeyAgainDoesNotRepeatTheWork() {
        val s = slot(); var runs = 0; s.start("a", direct) { runs++; "x" }; s.start("a", direct) { runs++; "y" }
        assertEquals(1, runs); assertEquals("x", s.take("a", 0))
    }

    @Test fun takeWaitsForWorkStillInProgress() {
        val s = slot(); val gate = CountDownLatch(1)
        s.start("a", { r -> Thread(r).start() }) { gate.await(2, TimeUnit.SECONDS); "late" }
        Thread { Thread.sleep(50); gate.countDown() }.start()
        assertEquals("late", s.take("a", 2000))
    }

    @Test fun aResultNotTakenInTimeIsFreed() {
        val s = slot(ttl = 1000); s.start("a", direct) { "model-a" }
        clock = 500; s.expire("a"); assertEquals("a", s.heldKey)           // still fresh
        clock = 1000; s.expire("a"); assertNull(s.heldKey); assertEquals(listOf("model-a"), discarded)
    }

    @Test fun aStaleResultIsNotHandedOut() {
        val s = slot(ttl = 1000); s.start("a", direct) { "model-a" }; clock = 5000
        assertNull(s.take("a", 0)); assertEquals(listOf("model-a"), discarded)
    }

    @Test fun failedWorkGivesNull() {
        val s = slot(); s.start("a", direct) { error("bad file") }; assertNull(s.take("a", 0))
    }
}
