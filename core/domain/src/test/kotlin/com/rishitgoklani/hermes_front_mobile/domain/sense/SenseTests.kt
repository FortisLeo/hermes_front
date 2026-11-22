package com.rishitgoklani.hermes_front_mobile.domain.sense

import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.SelfCheck
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.killSwitchCapability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.presenceCapability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.selfTestCapability
import com.rishitgoklani.hermes_front_mobile.domain.safety.KillSwitch
import com.rishitgoklani.hermes_front_mobile.testing.FakeCamera
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SenseTests {
    private fun still(t: Long, x: Float = 0f, y: Float = 0f, z: Float = 9.81f) = AccelSample(x, y, z, t)

    @Test fun pickUpAfterBeingStill() {
        val d = MotionDetector(); var t = 0L
        repeat(25) { assertNull(d.feed(still(t))); t += 100 }
        var got: MotionKind? = null
        repeat(15) { got = got ?: d.feed(AccelSample(2f, 3f, 12f + (it % 3), t)); t += 100 }
        assertEquals(MotionKind.PickUp, got)
    }
    @Test fun knockIsShortSpike() {
        val d = MotionDetector(); var t = 0L; repeat(25) { d.feed(still(t)); t += 100 }
        assertEquals(MotionKind.Knock, d.feed(AccelSample(0f, 0f, 16f, t)))
    }
    @Test fun tiltDetectedAfterStillBaseline() {
        val d = MotionDetector(); var t = 0L; repeat(30) { d.feed(still(t)); t += 100 }
        var got: MotionKind? = null; repeat(30) { got = got ?: d.feed(still(t, x = 7f, z = 6.9f)); t += 100 }
        assertEquals(MotionKind.Tilt, got)
    }
    @Test fun steadyDeskProducesNoEvents() { val d = MotionDetector(); var t = 0L; repeat(100) { assertNull(d.feed(still(t, z = 9.8f + (it % 2) * 0.05f))); t += 100 } }

    @Test fun ambientMapsLuxMonotonically() {
        val a = AmbientBrightness(); assertEquals(5, a.percentFor(0f)); assertTrue(a.percentFor(50f) < a.percentFor(300f)); assertEquals(100, a.percentFor(5000f)); assertTrue(a.percentFor(10f) in 5..100)
    }
    @Test fun ambientHysteresisSuppressesSmallChanges() {
        val a = AmbientBrightness(hysteresis = 10); assertNotNull(a.update(100f)); assertNull(a.update(105f)); assertNotNull(a.update(900f))
    }

    private fun args() = JsonObject(emptyMap())
    @Test fun presenceReportsFaceCountOnly() = runBlocking {
        val r = presenceCapability(FakeCamera(byteArrayOf(1))) { 2 }.executor.execute(args()); assertEquals("completed", r.status); assertTrue(r.data!!["present"]!!.jsonPrimitive.content.toBoolean()); assertEquals("2", r.data!!["faces"]!!.jsonPrimitive.content)
        assertTrue(presenceCapability(FakeCamera(byteArrayOf(1))) { 0 }.executor.execute(args()).message.contains("Nobody"))
    }
    @Test fun killSwitchStatusAndTriggerButNoReset() = runBlocking {
        val k = KillSwitch(); val c = killSwitchCapability(k)
        assertEquals("Inactive", c.executor.execute(JsonObject(mapOf("action" to JsonPrimitive("status")))).message)
        c.executor.execute(JsonObject(mapOf("action" to JsonPrimitive("trigger")))); assertTrue(k.isActive())
        assertEquals("failed", c.executor.execute(JsonObject(mapOf("action" to JsonPrimitive("reset")))).status); assertTrue(k.isActive())
    }
    @Test fun selfTestReportsPassFailAndTimeout() = runBlocking {
        val cap = selfTestCapability(listOf(SelfCheck("ok") { null }, SelfCheck("bad") { "no engine" }, SelfCheck("boom") { error("exploded") }, SelfCheck("slow") { delay(5000); null }), timeoutMs = 100)
        val r = cap.executor.execute(args()); assertEquals("failed", r.status)
        assertTrue(r.message.contains("PASS ok")); assertTrue(r.message.contains("FAIL bad: no engine")); assertTrue(r.message.contains("FAIL boom: exploded")); assertTrue(r.message.contains("timed out"))
        assertEquals("1", r.data!!["passed"]!!.jsonPrimitive.content)
    }
    @Test fun selfTestAllPassIsCompleted() = runBlocking { assertEquals("completed", selfTestCapability(listOf(SelfCheck("a") { null })).executor.execute(args()).status) }
}
