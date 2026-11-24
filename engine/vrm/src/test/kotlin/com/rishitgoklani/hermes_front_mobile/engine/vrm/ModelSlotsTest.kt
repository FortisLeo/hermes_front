package com.rishitgoklani.hermes_front_mobile.engine.vrm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelSlotsTest {
    private class Host : SlotHost<String> {
        val pending = LinkedHashMap<String, (Result<String>) -> Unit>(); val log = ArrayList<String>(); var clock = 0L
        override fun load(key: String, done: (Result<String>) -> Unit) { log += "load $key"; pending[key] = done }
        override fun show(key: String, model: String) { log += "show $key" }
        override fun dispose(key: String, model: String) { log += "dispose $key" }
        override fun shown(key: String, ms: Long, wasStaged: Boolean) { log += "shown $key $ms $wasStaged" }
        override fun staged(key: String, ms: Long) { log += "staged $key $ms" }
        override fun failed(key: String, message: String) { log += "failed $key $message" }
        fun finish(key: String, r: Result<String> = Result.success("m-$key")) { pending.remove(key)!!(r) }
    }
    private fun slots(h: Host) = ModelSlots(h) { h.clock }

    @Test fun loadsActiveThenStagesTheNextOnlyAfterItShows() {
        val h = Host(); val s = slots(h); s.want("a", "b")
        assertEquals(listOf("load a"), h.log)                       // b waits: never two decodes at once
        h.clock = 40; h.finish("a")
        assertEquals(listOf("load a", "show a", "shown a 40 false", "load b"), h.log)
        h.clock = 90; h.finish("b"); assertTrue(h.log.last().startsWith("staged b")); assertEquals(2, s.resident)
    }

    @Test fun swipingPromotesTheStagedModelWithoutLoading() {
        val h = Host(); val s = slots(h); s.want("a", "b"); h.finish("a"); h.finish("b"); h.log.clear()
        s.want("b", "c")
        assertEquals(listOf("show b", "shown b 0 true", "dispose a", "load c"), h.log)
        assertEquals("b", s.active)
    }

    @Test fun neverMoreThanTwoResident() {
        val h = Host(); val s = slots(h); s.want("a", "b"); h.finish("a"); h.finish("b")
        for (k in listOf("c" to "d", "d" to "e", "e" to "a")) { s.want(k.first, k.second); h.pending.keys.toList().forEach { h.finish(it) }; assertTrue(s.resident <= 2) }
    }

    @Test fun promotesAStageThatIsStillLoading() {
        val h = Host(); val s = slots(h); s.want("a", "b"); h.finish("a"); h.log.clear()
        s.want("b", "c")                                            // b is mid-load as staged and is now wanted on screen
        assertEquals(emptyList<String>(), h.log); h.finish("b")
        assertTrue(h.log.contains("show b")); assertTrue(h.log.contains("dispose a"))
    }

    @Test fun supersededLoadIsDisposedWhenItFinishes() {
        val h = Host(); val s = slots(h); s.want("a", null); s.want("b", null)
        h.finish("a"); assertTrue("dispose a" in h.log); assertTrue("show a" !in h.log)
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

    @Test fun releaseAllClearsState() {
        val h = Host(); val s = slots(h); s.want("a", null); h.finish("a"); s.releaseAll(); assertEquals(0, s.resident); assertEquals(null, s.active)
    }
}
