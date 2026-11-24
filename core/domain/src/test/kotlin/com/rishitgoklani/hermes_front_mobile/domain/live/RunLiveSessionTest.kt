package com.rishitgoklani.hermes_front_mobile.domain.live

import com.rishitgoklani.hermes_front_mobile.domain.BotState
import com.rishitgoklani.hermes_front_mobile.domain.CameraFacing
import com.rishitgoklani.hermes_front_mobile.domain.VoiceEvent
import com.rishitgoklani.hermes_front_mobile.domain.behavior.RunCapability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.CapabilityRegistry
import com.rishitgoklani.hermes_front_mobile.testing.FakeLiveAudioIn
import com.rishitgoklani.hermes_front_mobile.testing.FakeLiveAudioOut
import com.rishitgoklani.hermes_front_mobile.testing.FakeLiveSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

private inline fun <reified T : Throwable> failsWith(block: () -> Unit): T { try { block() } catch (e: Throwable) { if (e is T) return e; throw e }; throw AssertionError("expected ${T::class.simpleName}") }

@OptIn(ExperimentalCoroutinesApi::class)
class RunLiveSessionTest {
    private class Rig(val scope: TestScope, val settings: LiveSettings = LiveSettings(enabled = true, apiKey = "k", idleSeconds = 25), denied: () -> String? = { null }, camera: Boolean = true) {
        val session = FakeLiveSession(); val mic = FakeLiveAudioIn(); val speaker = FakeLiveAudioOut()
        private val registry = CapabilityRegistry(listOf(Capability("battery", "Battery", executor = { ActionResult.completed("85 percent") })))
        val tools = LiveToolBridge(registry, RunCapability(registry), setOf("battery"), denied = denied)
        val events = mutableListOf<VoiceEvent>()
        private val shot: suspend (CameraFacing) -> ByteArray = { f -> byteArrayOf((if (f == CameraFacing.Back) 9 else 1).toByte()) }
        val run = RunLiveSession(session, mic, speaker, tools, { settings }, { "be brief" },
            frame = if (camera) shot else null, clock = { scope.testScheduler.currentTime }, halfDuplexTailMs = 350)
        lateinit var job: Job
        fun start() { job = scope.backgroundScope.launch { run().collect { events += it } }; scope.testScheduler.runCurrent() }
        val states get() = events.filterIsInstance<VoiceEvent.State>().map { it.state }
    }

    private val call = { id: String, name: String, args: JsonObject -> LiveEvent.ToolCall(listOf(LiveCall(id, name, args))) }
    private val none = JsonObject(emptyMap())

    @Test fun opensWithTheModelVoicePromptAndTools() = runTest {
        val r = Rig(this).also { it.start() }
        val cfg = r.session.opened.single()
        assertEquals("gemini-3.8-live", cfg.model); assertEquals("Kore", cfg.voice); assertEquals("be brief", cfg.systemInstruction)
        assertEquals(setOf("battery", "end_conversation", "look"), cfg.tools.map { it.name }.toSet())
        assertEquals(listOf(BotState.Thinking, BotState.Listening), r.states)
    }

    @Test fun withoutACameraThereIsNoLookTool() = runTest {
        val r = Rig(this, camera = false).also { it.start() }
        assertTrue(r.session.opened.single().tools.none { it.name == "look" })
    }

    @Test fun microphoneAudioIsForwarded() = runTest {
        val r = Rig(this, LiveSettings(enabled = true, apiKey = "k", halfDuplex = false)).also { it.start() }
        r.mic.mic.emit(byteArrayOf(1, 2)); runCurrent()
        assertEquals(1, r.session.audio.size)
    }

    @Test fun speechPlaysAsItArrivesAndFirstAudioLatencyIsReported() = runTest {
        val r = Rig(this).also { it.start() }
        r.session.emit(LiveEvent.InputText("hello there")); runCurrent(); advanceTimeBy(300)
        r.session.emit(LiveEvent.AudioOut(byteArrayOf(5, 5)), LiveEvent.OutputText("Hi!")); runCurrent()
        assertEquals(1, r.speaker.played.size)
        assertTrue(r.events.contains(VoiceEvent.Heard("hello there")))
        assertTrue(r.events.contains(VoiceEvent.ReplyDelta("Hi!")))
        assertTrue(BotState.Speaking in r.states)
        assertEquals(300, r.events.filterIsInstance<VoiceEvent.Timing>().single().timing.firstSentenceMs)
    }

    @Test fun userBargeInFlushesQueuedSpeech() = runTest {
        val r = Rig(this).also { it.start() }
        r.session.emit(LiveEvent.AudioOut(byteArrayOf(1)), LiveEvent.Interrupted); runCurrent()
        assertEquals(1, r.speaker.flushes)
        assertEquals(BotState.Listening, r.states.last())
    }

    @Test fun toolCallsRunOnThePhoneAndAreAnswered() = runTest {
        val r = Rig(this).also { it.start() }
        r.session.emit(call("c1", "battery", none)); runCurrent()
        val res = r.session.toolResponses.single()
        assertEquals("c1", res.id); assertEquals("85 percent", res.response["message"]!!.jsonPrimitive.content)
        assertTrue(BotState.Working in r.states)
    }

    @Test fun killSwitchStopsToolCallsFromRunning() = runTest {
        val r = Rig(this, denied = { "Kill switch active" }).also { it.start() }
        r.session.emit(call("c1", "battery", none)); runCurrent()
        assertEquals(ActionResult.DENIED, r.session.toolResponses.single().response["status"]!!.jsonPrimitive.content)
    }

    @Test fun lookSendsACameraFrameAndAnswers() = runTest {
        val r = Rig(this).also { it.start() }
        r.session.emit(call("l1", "look", buildJsonObject { put("facing", JsonPrimitive("back")) })); runCurrent()
        assertEquals(9, r.session.frames.single()[0].toInt())
        assertEquals("completed", r.session.toolResponses.single().response["status"]!!.jsonPrimitive.content)
    }

    @Test fun endConversationFinishesAfterTheGoodbyeIsSpoken() = runTest {
        val r = Rig(this).also { it.start() }
        r.session.emit(call("e1", "end_conversation", none), LiveEvent.AudioOut(byteArrayOf(1))); runCurrent()
        assertTrue(r.job.isActive)
        r.session.emit(LiveEvent.TurnComplete); runCurrent(); advanceTimeBy(3_000); runCurrent()
        assertTrue(r.job.isCompleted)
        assertEquals(BotState.Idle, r.states.last())
        assertTrue(r.session.closes >= 1)
    }

    @Test fun silenceEndsTheConversation() = runTest {
        val r = Rig(this).also { it.start() }
        advanceTimeBy(24_000); runCurrent(); assertTrue(r.job.isActive)
        advanceTimeBy(2_000); runCurrent(); assertTrue(r.job.isCompleted)
    }

    @Test fun speakingKeepsTheConversationAlive() = runTest {
        val r = Rig(this).also { it.start() }
        r.speaker.setPlaying(true)
        advanceTimeBy(60_000); runCurrent(); assertTrue(r.job.isActive)
    }

    @Test fun halfDuplexMutesTheMicWhileTheBotTalksAndShortlyAfter() = runTest {
        val r = Rig(this).also { it.start() }
        r.speaker.setPlaying(true); r.mic.mic.emit(byteArrayOf(1)); runCurrent()
        r.speaker.setPlaying(false); r.mic.mic.emit(byteArrayOf(2)); runCurrent()
        assertEquals(0, r.session.audio.size)
        advanceTimeBy(400); r.mic.mic.emit(byteArrayOf(3)); runCurrent()
        assertEquals(1, r.session.audio.size)
    }

    @Test fun reconnectsWithTheResumptionHandleWhenTheServerClosesCleanly() = runTest {
        val r = Rig(this).also { it.start() }
        r.session.emit(LiveEvent.Resumable("h1"), LiveEvent.Closed("goaway", error = false)); runCurrent()
        assertEquals(listOf(null, "h1"), r.session.opened.map { it.resumeHandle })
        assertTrue(r.job.isActive)
    }

    @Test fun closeWithoutAHandleEndsTheConversation() = runTest {
        val r = Rig(this).also { it.start() }
        r.session.emit(LiveEvent.Closed("bye", error = false)); runCurrent(); advanceTimeBy(3_000); runCurrent()
        assertTrue(r.job.isCompleted)
    }

    @Test fun aServerErrorSurfacesAsAnException() = runTest {
        val rig = Rig(this); var caught: Throwable? = null
        val j = backgroundScope.launch { try { rig.run().toList() } catch (e: IOException) { caught = e } }
        runCurrent(); rig.session.emit(LiveEvent.Closed("quota", error = true)); runCurrent(); advanceTimeBy(3_000); runCurrent()
        assertEquals("quota", caught?.message); assertTrue(j.isCompleted)
    }

    @Test fun refusesToStartWithoutAnApiKey() = runTest {
        val rig = Rig(this, LiveSettings(enabled = true, apiKey = ""))
        failsWith<IllegalArgumentException> { rig.run().toList() }
    }

    @Test fun aRejectedSetupSurfacesTheServerMessage() = runTest {
        val rig = Rig(this); rig.session.openError = IOException("API key not valid")
        val e = failsWith<IOException> { rig.run().toList() }
        assertEquals("API key not valid", e.message)
        assertTrue(rig.session.closes >= 1)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RunLiveSessionLogTest {
    @Test fun aSpokenExchangeBecomesOneTurnEntry() = runTest {
        val log = com.rishitgoklani.hermes_front_mobile.domain.diagnostics.InMemoryApiLog(clock = { currentTime })
        val session = FakeLiveSession(); val registry = CapabilityRegistry()
        val run = RunLiveSession(session, FakeLiveAudioIn(), FakeLiveAudioOut(), LiveToolBridge(registry, RunCapability(registry), emptySet()), { LiveSettings(enabled = true, apiKey = "k") }, { "p" }, clock = { currentTime }, log = log)
        val job = backgroundScope.launch { run().collect { } }; runCurrent()
        session.emit(LiveEvent.InputText("what time is it"), LiveEvent.OutputText("It is "), LiveEvent.OutputText("noon.")); runCurrent()
        assertEquals(com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiStatus.Pending, log.entries.value.single().status)
        session.emit(LiveEvent.TurnComplete); runCurrent()
        val e = log.entries.value.single()
        assertEquals("what time is it", e.request); assertEquals("It is noon.", e.response)
        assertEquals(com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiStatus.Ok, e.status); assertTrue(job.isActive)
    }

    @Test fun anInterruptedAnswerIsMarkedAsSuch() = runTest {
        val log = com.rishitgoklani.hermes_front_mobile.domain.diagnostics.InMemoryApiLog(clock = { currentTime })
        val session = FakeLiveSession(); val registry = CapabilityRegistry()
        val run = RunLiveSession(session, FakeLiveAudioIn(), FakeLiveAudioOut(), LiveToolBridge(registry, RunCapability(registry), emptySet()), { LiveSettings(enabled = true, apiKey = "k") }, { "p" }, clock = { currentTime }, log = log)
        backgroundScope.launch { run().collect { } }; runCurrent()
        session.emit(LiveEvent.InputText("tell a story"), LiveEvent.OutputText("Once"), LiveEvent.Interrupted); runCurrent()
        assertTrue(log.entries.value.single().response.endsWith("[interrupted]"))
    }
}
