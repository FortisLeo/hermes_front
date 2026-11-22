package com.rishitgoklani.hermes_front_mobile.ui.voice

import com.rishitgoklani.hermes_front_mobile.domain.BotState
import com.rishitgoklani.hermes_front_mobile.domain.RunVoiceTurn
import com.rishitgoklani.hermes_front_mobile.domain.SttEvent
import com.rishitgoklani.hermes_front_mobile.testing.FakeHermesGateway
import com.rishitgoklani.hermes_front_mobile.testing.FakeSpeechSynthesizer
import com.rishitgoklani.hermes_front_mobile.testing.FakeSpeechToText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun talkProducesHeardReplyAndIdle() {
        val vm = VoiceViewModel(RunVoiceTurn(
            FakeSpeechToText(listOf(SttEvent.Final("hello"))), FakeHermesGateway(listOf("Hi.")), FakeSpeechSynthesizer()))
        vm.talk()
        val s = vm.state.value
        assertEquals("hello", s.heard); assertEquals("Hi.", s.reply); assertEquals(BotState.Idle, s.bot)
    }

    @Test fun sttFailureShowsError() {
        val vm = VoiceViewModel(RunVoiceTurn(
            FakeSpeechToText(failure = java.io.IOException("Microphone permission missing")), FakeHermesGateway(), FakeSpeechSynthesizer()))
        vm.talk()
        assertEquals(BotState.Error, vm.state.value.bot)
        assertEquals("Microphone permission missing", vm.state.value.error)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceViewModelExpressionTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun expressionEventsReachCallbackAndNeverShowInReplyText() {
        val seen = mutableListOf<com.rishitgoklani.hermes_front_mobile.domain.face.Expr>()
        val vm = VoiceViewModel(RunVoiceTurn(FakeSpeechToText(listOf(SttEvent.Final("what time is it"))), FakeHermesGateway(listOf("<expr wink/>Hi there.")), FakeSpeechSynthesizer()), seen::add)
        vm.talk()
        assertEquals(1, seen.size); assertEquals("Hi there.", vm.state.value.reply)
    }
}
