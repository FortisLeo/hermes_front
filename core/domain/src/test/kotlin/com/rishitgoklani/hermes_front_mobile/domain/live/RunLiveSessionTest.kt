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
        val session = FakeLiveSession(); val mic = FakeLiveAudioIn(); val speaker = FakeLiveAudioOut(); val audit = com.rishitgoklani.hermes_front_mobile.testing.FakeAuditLog()
        private val registry = CapabilityRegistry(listOf(Capability("battery", "Battery", executor = { ActionResult.completed("85 percent") })))
        val tools = LiveToolBridge(registry, RunCapability(registry), setOf("battery"), denied = denied)
        val events = mutableListOf<VoiceEvent>()
        private val shot: suspend (CameraFacing) -> ByteArray = { f -> byteArrayOf((if (f == CameraFacing.Back) 9 else 1).toByte()) }
        val run = RunLiveSession(session, mic, speaker, tools, { settings }, { "be brief" },
            frame = if (camera) shot else null, clock = { scope.testScheduler.currentTime }, audit = audit, halfDuplexTailMs = 350)
        lateinit var job: Job
        var failure: Throwable? = null
        fun start(keepAlive: Boolean = false) { job = scope.backgroundScope.launch { try { run(keepAlive).collect { events += it } } catch (e: java.io.IOException) { failure = e } }; scope.testScheduler.runCurrent() }
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
        r.session.emit(LiveEvent.Resumable("h1"), LiveEvent.Closed("goaway", error = false)); runCurrent(); advanceTimeBy(1_000); runCurrent()
        assertEquals(listOf(null, "h1"), r.session.opened.map { it.resumeHandle })
        assertTrue(r.job.isActive)
    }

    private suspend fun TestScope.drop(r: Rig, reason: String = "server went away", error: Boolean = false) {
        r.session.emit(LiveEvent.Closed(reason, error)); runCurrent(); advanceTimeBy(5_000); runCurrent()
    }

    @Test fun aCloseWithoutAHandleStartsAFreshSessionInsteadOfEndingTheConversation() = runTest {
        val r = Rig(this).also { it.start() }
        drop(r)
        assertEquals(listOf<String?>(null, null), r.session.opened.map { it.resumeHandle })
        assertTrue(r.job.isActive); assertEquals("Connection dropped, starting a new session…", r.run.notice.value!!.text)
    }

    @Test fun itGivesUpOnceTheReconnectBudgetIsSpentAndRecordsWhy() = runTest {
        val r = Rig(this).also { it.start() }
        repeat(5) { drop(r) }
        assertTrue(r.job.isCompleted); assertEquals(5, r.session.opened.size) // the first open plus four retries
        assertTrue("server closed: server went away" in r.audit.entries.single().second)
    }

    @Test fun aLongHealthyStretchEarnsAFreshReconnectBudget() = runTest {
        val r = Rig(this, LiveSettings(enabled = true, apiKey = "k", idleSeconds = 300)).also { it.start() }
        repeat(4) { drop(r) }
        assertEquals(5, r.session.opened.size)
        advanceTimeBy(61_000); runCurrent()
        drop(r)
        assertTrue(r.job.isActive); assertEquals(6, r.session.opened.size)
    }

    @Test fun anErrorCloseBeyondTheBudgetSurfacesTheServersReasonToo() = runTest {
        val r = Rig(this).also { it.start() }
        repeat(5) { drop(r, "quota exceeded", error = true) }
        assertEquals("quota exceeded", r.failure?.message); assertEquals("Stopped: quota exceeded", r.run.notice.value!!.text)
        assertEquals("failed", r.audit.entries.single().third)
    }

    @Test fun aMicrophoneTakenAwayBrieflyIsReopenedAndTheConversationContinues() = runTest {
        val r = Rig(this).also { it.mic.failures = 2; it.start() }
        assertEquals("Microphone interrupted, reconnecting…", r.run.notice.value!!.text)
        advanceTimeBy(1_500); runCurrent()
        assertEquals(3, r.mic.opens); assertTrue(r.job.isActive)
    }

    @Test fun aMicrophoneThatNeverComesBackEndsTheSessionWithAClearError() = runTest {
        val r = Rig(this).also { it.mic.failures = 99; it.start() }
        advanceTimeBy(20_000); runCurrent()
        assertTrue(r.job.isCompleted); assertTrue(r.failure!!.message!!.startsWith("microphone unavailable"))
    }

    @Test fun silenceEndsAWakeWordConversationAndTellsTheUser() = runTest {
        val r = Rig(this).also { it.start() }
        advanceTimeBy(26_000); runCurrent()
        assertTrue(r.job.isCompleted)
        assertTrue("Stopped listening after 25 s" in r.run.notice.value!!.text); assertTrue("idle" in r.audit.entries.single().second)
    }

    @Test fun aConversationStartedByTapStaysOpenThroughSilence() = runTest {
        val r = Rig(this).also { it.start(keepAlive = true) }
        advanceTimeBy(10 * 60_000L); runCurrent()
        assertTrue(r.job.isActive)
    }

    @Test fun goodbyeIsRecordedAsTheReason() = runTest {
        val r = Rig(this).also { it.start() }
        r.session.emit(call("e1", "end_conversation", none)); runCurrent(); r.session.emit(LiveEvent.TurnComplete); runCurrent(); advanceTimeBy(3_000); runCurrent()
        assertTrue("goodbye" in r.audit.entries.single().second)
    }

    @Test fun beingStoppedIsRecordedAsCancelled() = runTest {
        val r = Rig(this).also { it.start() }
        r.job.cancel(); runCurrent()
        assertTrue("cancelled" in r.audit.entries.single().second)
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

@OptIn(ExperimentalCoroutinesApi::class)
class RunLiveSessionActivityTest {
    private fun pcm(level: Int) = ByteArray(640) { if (it % 2 == 0) (level and 0xff).toByte() else (level shr 8).toByte() }
    private class Rig(scope: TestScope, halfDuplex: Boolean = false) {
        val session = FakeLiveSession(); val mic = FakeLiveAudioIn(); val speaker = FakeLiveAudioOut()
        private val registry = CapabilityRegistry()
        val run = RunLiveSession(session, mic, speaker, LiveToolBridge(registry, RunCapability(registry), emptySet()), { LiveSettings(enabled = true, apiKey = "k", halfDuplex = halfDuplex) }, { "p" }, clock = { scope.testScheduler.currentTime })
        lateinit var job: Job
        fun start(scope: TestScope) { job = scope.backgroundScope.launch { run().collect { } }; scope.testScheduler.runCurrent() }
    }

    @Test fun activeOnlyWhileTheConversationRuns() = runTest {
        val r = Rig(this); assertTrue(!r.run.active.value)
        r.start(this); assertTrue(r.run.active.value)
        r.job.cancel(); runCurrent()
        assertTrue(!r.run.active.value); assertEquals(0f, r.run.micLevel.value, 0f)
        assertTrue("cancelling must also close the socket", r.session.closes >= 1)
    }

    @Test fun micLevelFollowsHowLoudTheUserIs() = runTest {
        val r = Rig(this).also { it.start(this) }
        r.mic.mic.emit(pcm(0)); runCurrent(); assertEquals(0f, r.run.micLevel.value, 0f)
        r.mic.mic.emit(pcm(6000)); runCurrent(); assertTrue(r.run.micLevel.value > 0.5f)
    }

    @Test fun micLevelReadsZeroWhileTheBotTalksInHalfDuplex() = runTest {
        val r = Rig(this, halfDuplex = true).also { it.start(this) }
        r.mic.mic.emit(pcm(6000)); runCurrent(); assertTrue(r.run.micLevel.value > 0.5f)
        r.speaker.setPlaying(true); r.mic.mic.emit(pcm(6000)); runCurrent()
        assertEquals(0f, r.run.micLevel.value, 0f)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RunLiveSessionToolScopeTest {
    private fun rig(scope: TestScope, all: Boolean, promptSeen: MutableList<Boolean>): FakeLiveSession {
        val session = FakeLiveSession()
        val registry = CapabilityRegistry(listOf(Capability("battery", "b", executor = { ActionResult.completed("ok") }), Capability("torch", "t", executor = { ActionResult.completed("ok") })))
        val run = RunLiveSession(session, FakeLiveAudioIn(), FakeLiveAudioOut(), LiveToolBridge(registry, RunCapability(registry), setOf("battery")), { LiveSettings(enabled = true, apiKey = "k", allTools = all) },
            { a -> promptSeen += a; "p" }, clock = { scope.testScheduler.currentTime })
        scope.backgroundScope.launch { run().collect { } }; scope.testScheduler.runCurrent()
        return session
    }

    @Test fun withAllToolsOnEveryToolIsDeclaredAndThePromptKnowsSkillsAreOn() = runTest {
        val seen = mutableListOf<Boolean>(); val s = rig(this, true, seen)
        assertEquals(setOf("battery", "torch", "end_conversation", "look").minus("look"), s.opened.single().tools.map { it.name }.toSet())
        assertEquals(listOf(true), seen)
    }

    @Test fun inLeanModeOnlyTheEssentialToolsAreDeclared() = runTest {
        val seen = mutableListOf<Boolean>(); val s = rig(this, false, seen)
        assertEquals(setOf("battery", "end_conversation"), s.opened.single().tools.map { it.name }.toSet())
        assertEquals(listOf(false), seen)
    }
}
