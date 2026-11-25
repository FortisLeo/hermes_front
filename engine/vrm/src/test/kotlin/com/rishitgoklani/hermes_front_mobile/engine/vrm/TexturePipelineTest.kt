package com.rishitgoklani.hermes_front_mobile.engine.vrm

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class TexturePipelineTest {
    private val pool = Executors.newFixedThreadPool(2)
    @After fun down() { pool.shutdownNow() }

    /** A fake GL thread: runs queued uploads one by one on its own thread. */
    private class Gl { val q = LinkedBlockingQueue<() -> Unit>(); val t = Thread { try { while (true) q.take()() } catch (_: InterruptedException) {} }.also { it.isDaemon = true; it.start() } }

    @Test fun everyImageIsDecodedAndUploadedAndTheCallReturnsAfterTheLastUpload() {
        val gl = Gl(); val uploaded = Collections.synchronizedList(ArrayList<Int>())
        TexturePipeline(pool).run((0 until 10).toList(), decode = { "img$it" }, upload = { i, _, done -> gl.q.add { Thread.sleep(2); uploaded += i; done(null) } }, discard = {})
        assertEquals((0 until 10).toSet(), uploaded.toSet())
    }

    @Test fun neverMoreDecodedImagesAliveThanAllowed() {
        val gl = Gl(); val alive = AtomicInteger(); val peak = AtomicInteger()
        TexturePipeline(pool, maxAlive = 3).run((0 until 20).toList(),
            decode = { val n = alive.incrementAndGet(); peak.accumulateAndGet(n) { a, b -> maxOf(a, b) }; Thread.sleep(3); it },
            upload = { _, _, done -> gl.q.add { Thread.sleep(5); alive.decrementAndGet(); done(null) } }, discard = { alive.decrementAndGet() })
        assertTrue("peak ${peak.get()}", peak.get() <= 3)
    }

    @Test fun skippedImagesStillCountAsDone() {
        val gl = Gl(); val uploaded = AtomicInteger()
        TexturePipeline(pool).run((0 until 6).toList(), decode = { if (it % 2 == 0) null else it }, upload = { _, _, done -> gl.q.add { uploaded.incrementAndGet(); done(null) } }, discard = {})
        assertEquals(3, uploaded.get())
    }

    @Test fun aDecodeFailureIsThrownToTheCaller() {
        val gl = Gl()
        try { TexturePipeline(pool).run((0 until 5).toList(), decode = { if (it == 2) error("corrupt png") else it }, upload = { _, _, done -> gl.q.add { done(null) } }, discard = {}); fail() }
        catch (e: IllegalStateException) { assertEquals("corrupt png", e.message) }
    }

    @Test fun anUploadFailureIsThrownToTheCaller() {
        val gl = Gl()
        try { TexturePipeline(pool).run((0 until 4).toList(), decode = { it }, upload = { i, _, done -> gl.q.add { done(if (i == 1) IllegalStateException("GL out of memory") else null) } }, discard = {}); fail() }
        catch (e: IllegalStateException) { assertEquals("GL out of memory", e.message) }
    }

    @Test fun theAbortCheckStopsTheWaitWhenTheGlThreadNeverRuns() {
        // Uploads are queued but nothing ever runs them (GL gone): the abort check ends the wait.
        val t0 = System.nanoTime(); var checks = 0
        try { TexturePipeline(pool).run((0 until 4).toList(), decode = { it }, upload = { _, _, _ -> }, discard = {}, abortCheck = { if (++checks >= 3) throw IllegalStateException("GL stopped") }, tickMs = 20); fail() }
        catch (e: IllegalStateException) { assertEquals("GL stopped", e.message) }
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0) < 2000)
    }

    @Test fun nothingToDoReturnsAtOnce() { TexturePipeline(pool).run(emptyList(), decode = { it }, upload = { _, _: Int, _ -> fail() }, discard = {}) }
}
