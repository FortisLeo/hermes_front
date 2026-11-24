package com.rishitgoklani.hermes_front_mobile.domain.presence

import com.rishitgoklani.hermes_front_mobile.domain.behavior.RunCapability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.CapabilityRegistry
import com.rishitgoklani.hermes_front_mobile.domain.face.Expr
import com.rishitgoklani.hermes_front_mobile.domain.face.FaceController
import com.rishitgoklani.hermes_front_mobile.domain.face.Mood
import com.rishitgoklani.hermes_front_mobile.testing.FakeCueSink
import com.rishitgoklani.hermes_front_mobile.testing.FakeQuietContext
import com.rishitgoklani.hermes_front_mobile.testing.FakeSpeechSynthesizer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

private class Scripted(override val id: String, var out: List<Cue> = emptyList(), val fail: Boolean = false) : Behavior {
    override suspend fun react(input: Input): List<Cue> { if (fail) error("boom"); return out }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HubTest {
    private val quiet = FakeQuietContext()
    private val sink = FakeCueSink()
    private var now = 0L
    private fun hub(vararg b: Behavior) = BehaviorHub(BehaviorRegistry(b.toList()), quiet, sink, { now })

    @Test fun registryRejectsDuplicateIds() { runCatching { BehaviorRegistry(listOf(Scripted("a"), Scripted("a"))) }.let { assertTrue(it.isFailure) } }

    @Test fun cuesFromEveryBehaviorAreApplied() = runTest {
        hub(Scripted("a", listOf(Cue.Speak("one"))), Scripted("b", listOf(Cue.Look(0.1f, 0f)))).dispatch(Input.Tick)
        assertEquals(2, sink.cues.size)
    }

    @Test fun aFailingBehaviorDoesNotStopOthers() = runTest {
        hub(Scripted("bad", fail = true), Scripted("ok", listOf(Cue.Speak("still here")))).dispatch(Input.Tick)
        assertEquals(listOf("still here"), sink.spoken)
    }

    @Test fun callSilencesEvenImportantSpeech() = runTest {
        quiet.context = QuietContext(inCall = true)
        hub().publish(listOf(Cue.Speak("urgent", Urgency.Important)))
        assertTrue(sink.cues.isEmpty())
    }

    @Test fun dndHoldsNormalSpeechButLetsImportantThrough() = runTest {
        quiet.context = QuietContext(dnd = true)
        val h = hub()
        h.publish(listOf(Cue.Speak("chat"), Cue.Speak("fire", Urgency.Important)))
        assertEquals(listOf("fire"), sink.spoken)
        assertEquals(1, h.heldCount)
    }

    @Test fun heldSpeechIsReleasedWhenQuietEnds() = runTest {
        quiet.context = QuietContext(sleeping = true)
        val h = hub(); h.publish(listOf(Cue.Speak("meeting soon")))
        quiet.context = QuietContext()
        h.dispatch(Input.Tick)
        assertEquals(listOf("meeting soon"), sink.spoken); assertEquals(0, h.heldCount)
    }

    @Test fun heldSpeechExpires() = runTest {
        quiet.context = QuietContext(focus = true)
        val h = hub(); h.publish(listOf(Cue.Speak("stale")))
        now = 31 * 60_000; quiet.context = QuietContext()
        h.dispatch(Input.Tick)
        assertTrue(sink.cues.isEmpty())
    }

    @Test fun lowValueSpeechIsDroppedNotQueued() = runTest {
        quiet.context = QuietContext(dnd = true)
        val h = hub(); h.publish(listOf(Cue.Speak("fyi", Urgency.Low)))
        assertEquals(0, h.heldCount)
    }

    @Test fun duplicateHeldSpeechIsKeptOnce() = runTest {
        quiet.context = QuietContext(dnd = true)
        val h = hub(); h.publish(listOf(Cue.Speak("x"), Cue.Speak("x")))
        assertEquals(1, h.heldCount)
    }

    @Test fun nonSpeechCuesAreNeverHeld() = runTest {
        quiet.context = QuietContext(inCall = true)
        hub().publish(listOf(Cue.Express(Expr.MoodExpr(Mood.Sad, 1f))))
        assertEquals(1, sink.cues.size)
    }

    @Test fun tickLoopDispatchesOnVirtualTime() = runTest {
        var ticks = 0
        val counter = object : Behavior { override val id = "c"; override suspend fun react(input: Input): List<Cue> { if (input is Input.Tick) ticks++; return emptyList() } }
        val job = backgroundScope.launch { hub(counter).runTicks(1000) }
        advanceTimeBy(3500); runCurrent()
        assertEquals(3, ticks); job.cancel()
    }

    @Test fun quietPolicyTable() {
        assertTrue(QuietPolicy.allows(Urgency.Low, QuietContext()))
        assertFalse(QuietPolicy.allows(Urgency.Normal, QuietContext(focus = true)))
        assertTrue(QuietPolicy.allows(Urgency.Important, QuietContext(sleeping = true)))
        assertFalse(QuietPolicy.allows(Urgency.Important, QuietContext(inCall = true, dnd = true)))
        assertEquals(listOf("call", "sleep"), QuietContext(inCall = true, sleeping = true).reasons())
    }

    @Test fun bodySinkRoutesEveryCueKind() = runTest {
        val tts = FakeSpeechSynthesizer(); val face = FaceController(backgroundScope)
        val called = mutableListOf<String>()
        val run = RunCapability(CapabilityRegistry(listOf(Capability("vibrate", "v") { called += "vibrate"; ActionResult.completed() })))
        val s = BodyCueSink(tts, face, run)
        s.apply(Cue.Speak("hi")); s.apply(Cue.Express(Expr.MoodExpr(Mood.Happy, 0.5f))); s.apply(Cue.Look(0.5f, -0.5f)); s.apply(Cue.Call("vibrate"))
        assertEquals(listOf("hi"), tts.spoken); assertEquals(Mood.Happy, face.state.value.mood); assertEquals(0.5f, face.state.value.lookX); assertEquals(listOf("vibrate"), called)
    }
}
