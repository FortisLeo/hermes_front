package com.rishitgoklani.hermes_front_mobile.ui.chat

import com.rishitgoklani.hermes_front_mobile.domain.BotState
import com.rishitgoklani.hermes_front_mobile.testing.FakeHermesGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun streamsReplyIntoOneMessageAndReturnsToIdle() {
        val vm = ChatViewModel(FakeHermesGateway(listOf("Hel", "lo")))
        vm.send("hi")
        val s = vm.state.value
        assertEquals(listOf("hi", "Hello"), s.messages.map { it.text })
        assertEquals(BotState.Idle, s.bot)
    }

    @Test fun ignoresBlankInput() {
        val gateway = FakeHermesGateway()
        val vm = ChatViewModel(gateway)
        vm.send("   ")
        assertTrue(gateway.sent.isEmpty())
        assertTrue(vm.state.value.messages.isEmpty())
    }

    @Test fun failureSetsErrorState() {
        val vm = ChatViewModel(FakeHermesGateway(failure = java.io.IOException("down")))
        vm.send("hi")
        assertEquals(BotState.Error, vm.state.value.bot)
        assertEquals("down", vm.state.value.error)
    }

    @Test fun nextSendClearsError() {
        val vm = ChatViewModel(FakeHermesGateway(failure = java.io.IOException("down")))
        vm.send("a")
        assertNotNull(vm.state.value.error)
        vm.send("b")
        assertEquals(4, vm.state.value.messages.size)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelExpressionTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun tagsAreStrippedFromTextAndForwardedAsExpressions() {
        val seen = mutableListOf<com.rishitgoklani.hermes_front_mobile.domain.face.Expr>()
        val vm = ChatViewModel(FakeHermesGateway(listOf("Sure <ex", "pr happy 0.7/>! Done.")), seen::add)
        vm.send("hi")
        assertEquals("Sure ! Done.", vm.state.value.messages.last().text)
        assertEquals(listOf<com.rishitgoklani.hermes_front_mobile.domain.face.Expr>(com.rishitgoklani.hermes_front_mobile.domain.face.Expr.MoodExpr(com.rishitgoklani.hermes_front_mobile.domain.face.Mood.Happy, 0.7f)), seen)
    }
}
