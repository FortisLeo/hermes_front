package com.rishitgoklani.hermes_front_mobile.domain

import com.rishitgoklani.hermes_front_mobile.testing.FakeHermesGateway
import com.rishitgoklani.hermes_front_mobile.testing.FakeSpeechSynthesizer
import com.rishitgoklani.hermes_front_mobile.testing.FakeSpeechToText
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class RunVoiceTurnTest {
    private val said = listOf(SttEvent.Partial("hel"), SttEvent.Final("hello"))

    @Test fun fullTurnSpeaksSentencesAndEndsIdle() = runBlocking {
        val tts = FakeSpeechSynthesizer()
        val turn = RunVoiceTurn(FakeSpeechToText(said), FakeHermesGateway(listOf("Hi there. ", "Bye")), tts)
        val events = turn().toList()
        assertEquals(listOf("Hi there.", "Bye"), tts.spoken)
        assertTrue(tts.awaited)
        val states = events.filterIsInstance<VoiceEvent.State>().map { it.state }
        assertEquals(listOf(BotState.Listening, BotState.Thinking, BotState.Speaking, BotState.Idle), states)
        assertTrue(events.contains(VoiceEvent.Heard("hello")))
    }

    @Test fun silenceReturnsToIdleWithoutCallingHermes() = runBlocking {
        val gateway = FakeHermesGateway()
        val events = RunVoiceTurn(FakeSpeechToText(emptyList()), gateway, FakeSpeechSynthesizer())().toList()
        assertTrue(gateway.sent.isEmpty())
        assertEquals(VoiceEvent.State(BotState.Idle), events.last())
    }

    @Test fun sttFailurePropagates() {
        val turn = RunVoiceTurn(FakeSpeechToText(failure = java.io.IOException("no mic")), FakeHermesGateway(), FakeSpeechSynthesizer())
        assertThrows(java.io.IOException::class.java) { runBlocking { turn().toList() } }
    }
}
