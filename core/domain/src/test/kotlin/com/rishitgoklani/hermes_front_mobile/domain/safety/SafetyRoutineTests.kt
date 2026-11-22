package com.rishitgoklani.hermes_front_mobile.domain.safety

import com.rishitgoklani.hermes_front_mobile.domain.CameraFacing
import com.rishitgoklani.hermes_front_mobile.domain.RunVoiceTurn
import com.rishitgoklani.hermes_front_mobile.domain.SttEvent
import com.rishitgoklani.hermes_front_mobile.domain.VoiceEvent
import com.rishitgoklani.hermes_front_mobile.domain.behavior.RunCapability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.CapabilityRegistry
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.routineCapabilities
import com.rishitgoklani.hermes_front_mobile.domain.routines.DeviceEvent
import com.rishitgoklani.hermes_front_mobile.domain.routines.Routine
import com.rishitgoklani.hermes_front_mobile.domain.routines.RoutineEngine
import com.rishitgoklani.hermes_front_mobile.domain.routines.ToolCall
import com.rishitgoklani.hermes_front_mobile.domain.routines.Trigger
import com.rishitgoklani.hermes_front_mobile.domain.routines.TriggerMatcher
import com.rishitgoklani.hermes_front_mobile.domain.voice.VisionTrigger
import com.rishitgoklani.hermes_front_mobile.testing.FakeAuditLog
import com.rishitgoklani.hermes_front_mobile.testing.FakeCamera
import com.rishitgoklani.hermes_front_mobile.testing.FakeHermesGateway
import com.rishitgoklani.hermes_front_mobile.testing.FakeRoutineRepository
import com.rishitgoklani.hermes_front_mobile.testing.FakeSpeechSynthesizer
import com.rishitgoklani.hermes_front_mobile.testing.FakeSpeechToText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyRoutineTests {
    // ---- kill switch
    @Test fun killSwitchTriggersOnceAndResetsLocally() {
        var fired = 0; val k = KillSwitch({ fired++ }, { 42 }); k.trigger("voice"); k.trigger("again")
        assertTrue(k.isActive()); assertEquals(1, fired); assertEquals("voice", k.reason); assertEquals(42L, k.triggeredAt); k.reset(); assertFalse(k.isActive())
    }
    @Test fun keyComboNeedsThreePressesInWindow() {
        var t = 0L; val d = KeyComboDetector(3, 1500) { t }
        assertFalse(d.onPress()); t += 400; assertFalse(d.onPress()); t += 400; assertTrue(d.onPress())
        t += 400; assertFalse(d.onPress())
    }
    @Test fun keyComboWindowExpires() { var t = 0L; val d = KeyComboDetector(3, 1000) { t }; d.onPress(); t += 600; d.onPress(); t += 600; assertFalse(d.onPress()) }

    // ---- vision trigger + turn
    @Test fun visionPhrasesDetected() { listOf("What's in my hand?", "look at this", "what do you see", "How many fingers").forEach { assertTrue(it, VisionTrigger.needsVision(it)) }; assertFalse(VisionTrigger.needsVision("what time is it")) }
    @Test fun facingChoosesBackForRoomQuestions() { assertEquals(CameraFacing.Back, VisionTrigger.facing("what's behind you")); assertEquals(CameraFacing.Front, VisionTrigger.facing("what is this")) }

    private class RecordingGateway : com.rishitgoklani.hermes_front_mobile.domain.HermesGateway {
        var lastImage: String? = "unset"; override fun send(text: String, imageJpegBase64: String?): Flow<String> = flow { lastImage = imageJpegBase64; emit("A mug.") }
    }
    @Test fun voiceTurnAttachesFrameOnlyWhenNeeded() = runBlocking {
        val gw = RecordingGateway(); val cam = FakeCamera(byteArrayOf(1, 2))
        RunVoiceTurn(FakeSpeechToText(listOf(SttEvent.Final("what is this"))), gw, FakeSpeechSynthesizer(), cam, { "B64" })().toList(); assertEquals("B64", gw.lastImage); assertEquals(1, cam.requests.size)
        val gw2 = RecordingGateway(); RunVoiceTurn(FakeSpeechToText(listOf(SttEvent.Final("what time is it"))), gw2, FakeSpeechSynthesizer(), cam, { "B64" })().toList(); assertEquals(null, gw2.lastImage); assertEquals(1, cam.requests.size)
    }
    @Test fun cameraFailureStillAnswersWithoutImage() = runBlocking {
        val gw = RecordingGateway(); RunVoiceTurn(FakeSpeechToText(listOf(SttEvent.Final("look at this"))), gw, FakeSpeechSynthesizer(), FakeCamera(failure = IllegalStateException("busy")), { "B64" })().toList(); assertEquals(null, gw.lastImage)
    }
    @Test fun timingEventAndAuditRecorded() = runBlocking {
        var t = 0L; val audit = FakeAuditLog()
        val events = RunVoiceTurn(FakeSpeechToText(listOf(SttEvent.Final("hi"))), FakeHermesGateway(listOf("Hello there.")), FakeSpeechSynthesizer(), clock = { t += 100; t }, audit = audit)().toList()
        val timing = events.filterIsInstance<VoiceEvent.Timing>().single().timing
        assertTrue(timing.firstTokenMs > 0 && timing.firstSentenceMs >= timing.firstTokenMs); assertEquals("voice_turn", audit.entries.single().first)
    }

    // ---- routines
    private val reg = CapabilityRegistry(listOf(Capability("battery", "b") { ActionResult.completed("ok") }, Capability("shell", "s") { ActionResult.completed("") }))
    private fun routine(id: String = "r1", trig: Trigger = Trigger("charger_connected"), cd: Long = 1000) = Routine(id, "R", trig, listOf(ToolCall("battery")), cd)

    @Test fun triggerFilters() {
        assertTrue(TriggerMatcher.matches(Trigger("notification", app = "whats", contains = "urgent"), DeviceEvent.NotificationPosted("WhatsApp", "Mom", "URGENT call")))
        assertFalse(TriggerMatcher.matches(Trigger("notification", app = "gmail"), DeviceEvent.NotificationPosted("WhatsApp", "a", "b")))
        assertTrue(TriggerMatcher.matches(Trigger("wifi_connected", ssid = "Home"), DeviceEvent.WifiConnected("HomeNet"))); assertFalse(TriggerMatcher.matches(Trigger("wifi_connected", ssid = "Work"), DeviceEvent.WifiConnected("HomeNet")))
        assertTrue(TriggerMatcher.matches(Trigger("battery_low", below = 20), DeviceEvent.BatteryLow(15))); assertFalse(TriggerMatcher.matches(Trigger("battery_low", below = 10), DeviceEvent.BatteryLow(15)))
        assertFalse(TriggerMatcher.matches(Trigger("charger_connected"), DeviceEvent.ChargerDisconnected())); assertTrue(TriggerMatcher.matches(Trigger("motion", kind = "pickup"), DeviceEvent.Motion("PICKUP")))
        assertTrue(TriggerMatcher.matches(Trigger("place", place = "home"), DeviceEvent.Place("Home", true)))
    }
    @Test fun engineRunsMatchingRoutinesWithCooldownAndAudit() = runBlocking {
        var now = 0L; val audit = FakeAuditLog(); val repo = FakeRoutineRepository(mutableListOf(routine(), routine("off").copy(enabled = false), routine("other", Trigger("screen_on"))))
        val e = RoutineEngine(repo, RunCapability(reg), { now }, audit)
        assertEquals(listOf("r1"), e.onEvent(DeviceEvent.ChargerConnected())); assertEquals(emptyList<String>(), e.onEvent(DeviceEvent.ChargerConnected()))
        now += 2000; assertEquals(listOf("r1"), e.onEvent(DeviceEvent.ChargerConnected())); assertEquals(2, audit.entries.size)
    }
    @Test fun routineCapabilitiesValidate() = runBlocking {
        val repo = FakeRoutineRepository(); val c = routineCapabilities(repo) { reg }.associateBy { it.name }
        fun a(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject
        assertEquals("completed", c["routine_add"]!!.executor.execute(a("""{"routine":{"id":"night","name":"Night","trigger":{"event":"charger_connected"},"actions":[{"tool":"battery"}]}}""")).status)
        assertEquals("failed", c["routine_add"]!!.executor.execute(a("""{"routine":{"id":"bad","name":"B","trigger":{"event":"explode"},"actions":[{"tool":"battery"}]}}""")).status)
        assertEquals("failed", c["routine_add"]!!.executor.execute(a("""{"routine":{"id":"bad2","name":"B","trigger":{"event":"screen_on"},"actions":[{"tool":"nope"}]}}""")).status)
        assertEquals("denied", c["routine_add"]!!.executor.execute(a("""{"routine":{"id":"sh","name":"S","trigger":{"event":"screen_on"},"actions":[{"tool":"shell","args":{"cmd":"id"}}]}}""")).status)
        assertEquals("failed", c["routine_add"]!!.executor.execute(a("""{"routine":{"id":"fast","name":"S","trigger":{"event":"screen_on"},"actions":[{"tool":"battery"}],"cooldownMs":10}}""")).status)
        assertTrue(c["routine_list"]!!.executor.execute(a("{}")).message.contains("night")); assertEquals("completed", c["routine_remove"]!!.executor.execute(a("""{"id":"night"}""")).status)
    }
}
