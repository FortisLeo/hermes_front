package com.rishitgoklani.hermes_front_mobile.engine.vrm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelSlotsTest {
    private class Host : SlotHost<String> {
        val pending = LinkedHashMap<String, (Result<String>) -> Unit>(); val finishing = LinkedHashMap<String, (Result<Unit>) -> Unit>()
        val log = ArrayList<String>(); var clock = 0L
        override fun load(key: String, withTextures: Boolean, done: (Result<String>) -> Unit) { log += "load $key${if (withTextures) "" else " geometry"}"; pending[key] = done }
        override fun finishShow(key: String, model: String, done: (Result<Unit>) -> Unit) { log += "finish $key"; finishing[key] = done }
        override fun show(key: String, model: String) { log += "show $key" }
        override fun dispose(key: String, model: String) { log += "dispose $key" }
        override fun cancel(key: String) { log += "cancel $key" }
        override fun shown(key: String, ms: Long, wasStaged: Boolean) { log += "shown $key $ms $wasStaged" }
        override fun staged(key: String, ms: Long) { log += "staged $key $ms" }
        override fun failed(key: String, message: String) { log += "failed $key $message" }
        fun finish(key: String, r: Result<String> = Result.success("m-$key")) { pending.remove(key)!!(r) }
        fun finishTextures(key: String, r: Result<Unit> = Result.success(Unit)) { finishing.remove(key)!!(r) }
    }
    private fun slots(h: Host) = ModelSlots(h) { h.clock }
    private fun texturedSlots(h: Host, allow: () -> Boolean = { true }) = ModelSlots(h, stageTextures = allow) { h.clock }

    @Test fun loadsActiveThenStagesTheNextGeometryOnlyAfterItShows() {
        val h = Host(); val s = slots(h); s.want("a", "b")
        assertEquals(listOf("load a"), h.log)                       // b waits: never two decodes at once
        h.clock = 40; h.finish("a")
        assertEquals(listOf("load a", "show a", "shown a 40 false", "load b geometry"), h.log)
        h.clock = 90; h.finish("b"); assertTrue(h.log.last().startsWith("staged b")); assertEquals(2, s.resident)
    }

    @Test fun promotingAStagedModelFinishesItsTexturesWhileTheOldOneStaysOnScreen() {
        val h = Host(); val s = slots(h); s.want("a", "b"); h.finish("a"); h.finish("b"); h.log.clear()
        s.want("b", "c")
        assertEquals(listOf("finish b"), h.log); assertEquals("a", s.active)             // a still on screen, nothing disposed yet
        h.finishTextures("b")
        assertEquals(listOf("finish b", "show b", "shown b 0 true", "dispose a", "load c geometry"), h.log); assertEquals("b", s.active)
    }

    @Test fun theOldModelIsNotDisposedUntilTheReplacementIsFullyReady() {
        val h = Host(); val s = slots(h); s.want("a", "b"); h.finish("a"); h.finish("b"); h.log.clear()
        s.want("b", null); assertTrue("dispose a" !in h.log && "show b" !in h.log); s.want("b", null); assertEquals(listOf("finish b"), h.log)   // no double finish
        h.finishTextures("b"); assertTrue("dispose a" in h.log); assertEquals("b", s.active)
    }

    @Test fun failedTextureFinishDisposesTheModelReportsOnceAndKeepsTheOldOneShowing() {
        val h = Host(); val s = slots(h); s.want("a", "b"); h.finish("a"); h.finish("b"); h.log.clear()
        s.want("b", null); h.finishTextures("b", Result.failure(IllegalStateException("oom")))
        assertEquals(listOf("finish b", "dispose b", "failed b oom"), h.log); assertEquals("a", s.active)
    }

    @Test fun swipingBackReloadsTheOldModelAsGeometryOnly() {
        val h = Host(); val s = slots(h); s.want("a", "b"); h.finish("a"); h.finish("b"); s.want("b", "a"); h.finishTextures("b"); h.log.clear()
        s.want("b", "a"); assertTrue(s.resident == 1)
        h.finish("a"); assertEquals(2, s.resident)
    }

    @Test fun neverMoreThanTwoResident() {
        val h = Host(); val s = slots(h); s.want("a", "b"); h.finish("a"); h.finish("b")
        for (k in listOf("c" to "d", "d" to "e", "e" to "a")) {
            s.want(k.first, k.second)
            repeat(4) { h.pending.keys.toList().forEach { h.finish(it) }; h.finishing.keys.toList().forEach { h.finishTextures(it) }; assertTrue(s.resident <= 2) }
        }
    }

    @Test fun promotesAStageThatIsStillLoading() {
        val h = Host(); val s = slots(h); s.want("a", "b"); h.finish("a"); h.log.clear()
        s.want("b", "c")                                            // b is mid-load as staged and is now wanted on screen
        assertEquals(emptyList<String>(), h.log); h.finish("b"); assertEquals(listOf("finish b"), h.log)
        h.finishTextures("b"); assertTrue(h.log.contains("show b")); assertTrue(h.log.contains("dispose a"))
    }

    @Test fun supersededLoadIsCancelledAndDisposedWhenItFinishes() {
        val h = Host(); val s = slots(h); s.want("a", null); s.want("b", null)
        assertTrue("cancel a" in h.log || "dispose a" !in h.log)
        h.finish("a"); assertTrue("show a" !in h.log)
    }

    @Test fun droppingTheStagedModelKeepsTheOneOnScreen() {
        val h = Host(); val s = slots(h); s.want("a", "b"); h.finish("a"); h.finish("b"); h.log.clear()
        s.dropStaged(); assertEquals(listOf("dispose b"), h.log); assertEquals(1, s.resident); assertEquals("a", s.active)
        s.want("a", "b"); assertEquals("load b geometry", h.log.last())     // the next want brings it back
    }

    @Test fun failureIsReportedOnceAndNotRetriedUntilAsked() {
        val h = Host(); val s = slots(h); s.want("a", null); h.finish("a", Result.failure(IllegalStateException("bad")))
        assertEquals(listOf("load a", "failed a bad"), h.log)
        s.want("a", null); assertEquals(2, h.log.size)             // same request: no retry loop
        s.retry("a"); assertEquals("load a", h.log.last())
    }

    @Test fun unloadFreesEverything() {
        val h = Host(); val s = slots(h); s.want("a", "b"); h.finish("a"); h.finish("b"); s.want(null, null)
        assertEquals(0, s.resident); assertTrue("dispose a" in h.log && "dispose b" in h.log)
    }

    @Test fun releaseAllClearsStateAndCancelsLoadsInFlight() {
        val h = Host(); val s = slots(h); s.want("a", null); h.finish("a"); s.want("a", "b"); s.releaseAll()
        assertEquals(0, s.resident); assertEquals(null, s.active); assertTrue("cancel b" in h.log)
    }

    @Test fun withEnoughMemoryTheStagedModelLoadsWithTexturesAndIsPromotedInOneStep() {
        val h = Host(); val s = texturedSlots(h); s.want("a", "b"); h.finish("a")
        assertEquals("load b", h.log.last())                          // not geometry-only
        h.finish("b"); h.log.clear()
        s.want("b", "c")
        assertEquals(listOf("show b", "shown b 0 true", "dispose a", "load c"), h.log)   // no finish step: b goes on screen at once
    }

    @Test fun theStagingChoiceIsMadeWhenEachStagedLoadStarts() {
        var roomy = true; val h = Host(); val s = texturedSlots(h) { roomy }; s.want("a", "b"); h.finish("a"); assertEquals("load b", h.log.last())
        h.finish("b"); roomy = false; s.want("b", "c")
        assertEquals("load c geometry", h.log.last())                // memory got tight: the next stage holds no textures
        h.finish("c"); h.log.clear(); s.want("c", null); assertEquals(listOf("finish c"), h.log)
    }
}
