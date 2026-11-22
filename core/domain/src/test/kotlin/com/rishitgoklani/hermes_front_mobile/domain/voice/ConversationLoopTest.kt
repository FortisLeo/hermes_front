package com.rishitgoklani.hermes_front_mobile.domain.voice

import com.rishitgoklani.hermes_front_mobile.domain.BotState
import com.rishitgoklani.hermes_front_mobile.domain.RunVoiceTurn
import com.rishitgoklani.hermes_front_mobile.domain.SttEvent
import com.rishitgoklani.hermes_front_mobile.domain.VoiceEvent
import com.rishitgoklani.hermes_front_mobile.testing.FakeHermesGateway
import com.rishitgoklani.hermes_front_mobile.testing.FakeKeywordSpotter
import com.rishitgoklani.hermes_front_mobile.testing.FakeSpeechSynthesizer
import com.rishitgoklani.hermes_front_mobile.testing.FakeSpeechToText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationLoopTest {
    private fun loop(spotter: FakeKeywordSpotter, tts: FakeSpeechSynthesizer, gateway: FakeHermesGateway = FakeHermesGateway(listOf("Hello there.")), onWake: () -> Unit = {}, stt: FakeSpeechToText = FakeSpeechToText(listOf(SttEvent.Final("hi")))) =
        ConversationLoop(spotter, RunVoiceTurn(stt, gateway, tts), tts, VoiceLoopConfig(restartDelayMs = 100), onWake)

    @Test fun wakePhraseTriggersATurnThenReturnsToWaiting() = runTest {
        val spotter = FakeKeywordSpotter(); val tts = FakeSpeechSynthesizer(); var woke = 0
        val l = loop(spotter, tts, onWake = { woke++ }); val seen = mutableListOf<VoiceEvent>()
        backgroundScope.launch { l.events.collect { seen += it } }; backgroundScope.launch { l.run() }
        runCurrent(); spotter.hearWake(); advanceTimeBy(1000)
        assertEquals(1, woke); assertEquals(listOf("Hello there."), tts.spoken)
        assertTrue(seen.any { it is VoiceEvent.State && it.state == BotState.Speaking }); assertEquals(VoiceEvent.State(BotState.Idle), seen.last())
    }
    @Test fun secondWakeStartsSecondTurn() = runTest {
        val spotter = FakeKeywordSpotter(); val tts = FakeSpeechSynthesizer(); val l = loop(spotter, tts)
        backgroundScope.launch { l.run() }; runCurrent(); spotter.hearWake(); advanceTimeBy(1000); spotter.hearWake(); advanceTimeBy(1000)
        assertEquals(listOf("Hello there.", "Hello there."), tts.spoken)
    }
    @Test fun stopPhraseCancelsTurnAndSilencesSpeech() = runTest {
        val spotter = FakeKeywordSpotter(); val tts = FakeSpeechSynthesizer()
        val slow = object : com.rishitgoklani.hermes_front_mobile.domain.HermesGateway { override fun send(text: String, imageJpegBase64: String?): Flow<String> = flow { emit("Part one. "); kotlinx.coroutines.delay(60_000); emit("never") } }
        val l = ConversationLoop(spotter, RunVoiceTurn(FakeSpeechToText(listOf(SttEvent.Final("hi"))), slow, tts), tts, VoiceLoopConfig(restartDelayMs = 100))
        backgroundScope.launch { l.run() }; runCurrent(); spotter.hearWake(); runCurrent(); advanceTimeBy(500)
        assertEquals(listOf("Part one."), tts.spoken); spotter.hearStop(); advanceTimeBy(500)
        assertTrue(tts.stopped)
    }
    @Test fun usesConfiguredPhrases() = runTest {
        val spotter = FakeKeywordSpotter(); val tts = FakeSpeechSynthesizer()
        val l = ConversationLoop(spotter, RunVoiceTurn(FakeSpeechToText(emptyList()), FakeHermesGateway(), tts), tts, VoiceLoopConfig(wakePhrases = listOf("hey robo"), stopPhrases = listOf("halt")))
        backgroundScope.launch { l.run() }; runCurrent()
        assertEquals(listOf("hey robo", "emergency stop", "kill switch"), spotter.listenCalls.first())
    }
    @Test fun micFailureReportsErrorAndRetries() = runTest {
        var n = 0
        val flaky = object : KeywordSpotter { override fun listen(keywords: List<String>): Flow<String> = flow { if (keywords.first() == "stop") kotlinx.coroutines.awaitCancellation(); if (n++ < 2) throw java.io.IOException("mic busy"); emit("hey bot") } }
        val tts = FakeSpeechSynthesizer(); val l = ConversationLoop(flaky, RunVoiceTurn(FakeSpeechToText(listOf(SttEvent.Final("hi"))), FakeHermesGateway(listOf("ok.")), tts), tts, VoiceLoopConfig(restartDelayMs = 50))
        val seen = mutableListOf<VoiceEvent>(); backgroundScope.launch { l.events.collect { seen += it } }; backgroundScope.launch { l.run() }
        advanceTimeBy(5000)
        assertTrue(seen.any { it is VoiceEvent.State && it.state == BotState.Error }); assertTrue(tts.spoken.isNotEmpty() && tts.spoken.all { it == "ok." })
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationLoopKillTest {
    @Test fun killPhraseWhileIdleTriggersKillAndNoTurn() = runTest {
        val killed = mutableListOf<String>(); val tts = FakeSpeechSynthesizer()
        val spotter = object : KeywordSpotter { var n = 0; override fun listen(keywords: List<String>): Flow<String> = flow { if (n++ == 0) emit("emergency stop") else kotlinx.coroutines.awaitCancellation() } }
        val l = ConversationLoop(spotter, RunVoiceTurn(FakeSpeechToText(listOf(SttEvent.Final("hi"))), FakeHermesGateway(listOf("x.")), tts), tts, VoiceLoopConfig(restartDelayMs = 50), onKill = { killed += it })
        backgroundScope.launch { l.run() }; advanceTimeBy(1000)
        assertEquals(listOf("emergency stop"), killed); assertTrue(tts.spoken.isEmpty())
    }
}
