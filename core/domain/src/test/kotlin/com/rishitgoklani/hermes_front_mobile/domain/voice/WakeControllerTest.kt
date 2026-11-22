package com.rishitgoklani.hermes_front_mobile.domain.voice

import com.rishitgoklani.hermes_front_mobile.domain.BotState
import com.rishitgoklani.hermes_front_mobile.domain.RunVoiceTurn
import com.rishitgoklani.hermes_front_mobile.domain.SttEvent
import com.rishitgoklani.hermes_front_mobile.testing.FakeHermesGateway
import com.rishitgoklani.hermes_front_mobile.testing.FakeKeywordSpotter
import com.rishitgoklani.hermes_front_mobile.testing.FakeSpeechSynthesizer
import com.rishitgoklani.hermes_front_mobile.testing.FakeSpeechToText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WakeControllerTest {
    private fun parts(): Triple<FakeKeywordSpotter, FakeSpeechSynthesizer, ConversationLoop> {
        val s = FakeKeywordSpotter(); val t = FakeSpeechSynthesizer()
        return Triple(s, t, ConversationLoop(s, RunVoiceTurn(FakeSpeechToText(listOf(SttEvent.Final("hi"))), FakeHermesGateway(listOf("Yo.")), t), t, VoiceLoopConfig(restartDelayMs = 50)))
    }

    @Test fun startRunsLoopAndPersists() = runTest {
        val (spotter, tts, loop) = parts(); val saved = mutableListOf<Boolean>(); val c = WakeController(loop, backgroundScope, saved::add)
        c.start(); runCurrent(); spotter.hearWake(); advanceTimeBy(500)
        assertTrue(c.enabled.value); assertEquals(listOf(true), saved); assertEquals(listOf("Yo."), tts.spoken)
    }
    @Test fun stopCancelsAndResetsState() = runTest {
        val (spotter, tts, loop) = parts(); val saved = mutableListOf<Boolean>(); val c = WakeController(loop, backgroundScope, saved::add)
        c.start(); runCurrent(); c.stop(); spotter.hearWake(); advanceTimeBy(500)
        assertFalse(c.enabled.value); assertEquals(BotState.Idle, c.state.value); assertTrue(tts.spoken.isEmpty()); assertEquals(listOf(true, false), saved)
    }
    @Test fun doubleStartIsIgnoredAndToggleFlips() = runTest {
        val (_, _, loop) = parts(); val saved = mutableListOf<Boolean>(); val c = WakeController(loop, backgroundScope, saved::add)
        c.start(); c.start(); assertEquals(listOf(true), saved); c.toggle(); assertFalse(c.enabled.value); c.toggle(); assertTrue(c.enabled.value)
    }
    @Test fun initiallyEnabledStartsWithoutRepersisting() = runTest {
        val (_, _, loop) = parts(); val saved = mutableListOf<Boolean>(); val c = WakeController(loop, backgroundScope, saved::add, initiallyEnabled = true)
        assertTrue(c.enabled.value); assertTrue(saved.isEmpty())
    }
    @Test fun liveStateFollowsTurn() = runTest {
        val (spotter, _, loop) = parts(); val c = WakeController(loop, backgroundScope); val seen = mutableSetOf<BotState>()
        backgroundScope.launch { c.events.collect { if (it is com.rishitgoklani.hermes_front_mobile.domain.VoiceEvent.State) seen += it.state } }
        runCurrent(); c.start(); runCurrent(); spotter.hearWake(); advanceTimeBy(500)
        assertTrue(BotState.Speaking in seen); assertEquals(BotState.Idle, c.state.value)
    }
}
