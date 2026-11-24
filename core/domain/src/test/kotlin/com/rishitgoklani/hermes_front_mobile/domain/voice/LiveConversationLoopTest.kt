package com.rishitgoklani.hermes_front_mobile.domain.voice

import com.rishitgoklani.hermes_front_mobile.domain.BotState
import com.rishitgoklani.hermes_front_mobile.domain.VoiceEvent
import com.rishitgoklani.hermes_front_mobile.domain.behavior.RunCapability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.CapabilityRegistry
import com.rishitgoklani.hermes_front_mobile.domain.live.LiveSettings
import com.rishitgoklani.hermes_front_mobile.domain.live.LiveToolBridge
import com.rishitgoklani.hermes_front_mobile.domain.live.RunLiveSession
import com.rishitgoklani.hermes_front_mobile.testing.FakeKeywordSpotter
import com.rishitgoklani.hermes_front_mobile.testing.FakeLiveAudioIn
import com.rishitgoklani.hermes_front_mobile.testing.FakeLiveAudioOut
import com.rishitgoklani.hermes_front_mobile.testing.FakeLiveSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveConversationLoopTest {
    private class Rig(scope: TestScope, settings: LiveSettings = LiveSettings(enabled = true, apiKey = "k", idleSeconds = 25)) {
        val spotter = FakeKeywordSpotter(); val session = FakeLiveSession()
        private val registry = CapabilityRegistry()
        val run = RunLiveSession(session, FakeLiveAudioIn(), FakeLiveAudioOut(), LiveToolBridge(registry, RunCapability(registry), emptySet()), { settings }, { "p" }, clock = { scope.testScheduler.currentTime })
        val killed = mutableListOf<String>(); val errors = mutableListOf<Throwable>()
        val loop = LiveConversationLoop(spotter, { run }, VoiceLoopConfig(restartDelayMs = 10), onKill = { killed += it }, onError = { errors += it })
        val events = mutableListOf<VoiceEvent>()
        init {
            scope.backgroundScope.launch { loop.events.collect { events += it } }
            scope.backgroundScope.launch { loop.run() }
            scope.testScheduler.runCurrent()
        }
    }

    @Test fun tappingStartsAConversationWithoutTheWakePhrase() = runTest {
        val r = Rig(this); r.loop.requestStart(); runCurrent()
        assertEquals(1, r.session.opened.size); assertTrue(r.run.active.value)
    }

    @Test fun aConversationStartedByTapStaysOpenThroughSilence() = runTest {
        val r = Rig(this); r.loop.requestStart(); runCurrent()
        advanceTimeBy(5 * 60_000L); runCurrent()
        assertTrue(r.run.active.value)
    }

    @Test fun aWakePhraseConversationStillEndsAfterSilenceAndReturnsToWaiting() = runTest {
        val r = Rig(this); r.spotter.hearWake(); runCurrent(); assertTrue(r.run.active.value)
        advanceTimeBy(27_000); runCurrent()
        assertTrue(!r.run.active.value)
        assertTrue("the loop must listen for the wake phrase again", r.spotter.listenCalls.size >= 2)
    }

    @Test fun tappingAgainStopsTheConversationAndClosesTheConnection() = runTest {
        val r = Rig(this); r.loop.requestStart(); runCurrent()
        r.loop.requestStop(); runCurrent()
        assertTrue(!r.run.active.value); assertTrue(r.session.closes >= 1)
        r.loop.requestStart(); advanceTimeBy(100); runCurrent()
        assertEquals("a new tap must start a new conversation", 2, r.session.opened.size)
    }

    @Test fun aStopTapThatArrivedBeforeTheConversationDoesNotKillIt() = runTest {
        val r = Rig(this); r.loop.requestStop(); r.loop.requestStart(); runCurrent()
        assertTrue(r.run.active.value)
    }

    @Test fun theKillPhraseStillWorksAndOpensNothing() = runTest {
        val r = Rig(this); r.spotter.hearWake("emergency stop"); runCurrent()
        assertEquals(listOf("emergency stop"), r.killed); assertTrue(r.session.opened.isEmpty())
    }

    @Test fun aSessionThatCannotStartIsReportedAndTheLoopKeepsRunning() = runTest {
        val r = Rig(this, LiveSettings(enabled = true, apiKey = "")); r.loop.requestStart(); runCurrent()
        assertTrue(r.errors.single() is IllegalArgumentException)
        assertTrue(r.events.any { it == VoiceEvent.State(BotState.Error) })
        advanceTimeBy(5_000); runCurrent()
        r.loop.requestStart(); runCurrent() // still alive: the second tap is handled too
        assertEquals(2, r.errors.size)
    }
}
