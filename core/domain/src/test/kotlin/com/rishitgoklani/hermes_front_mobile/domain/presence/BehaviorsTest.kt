package com.rishitgoklani.hermes_front_mobile.domain.presence

import com.rishitgoklani.hermes_front_mobile.domain.BotState
import com.rishitgoklani.hermes_front_mobile.domain.face.ArrivalGreeter
import com.rishitgoklani.hermes_front_mobile.domain.face.Expr
import com.rishitgoklani.hermes_front_mobile.domain.face.Mood
import com.rishitgoklani.hermes_front_mobile.domain.face.Reaction
import com.rishitgoklani.hermes_front_mobile.domain.routines.DeviceEvent
import com.rishitgoklani.hermes_front_mobile.testing.FakeAmbientSense
import com.rishitgoklani.hermes_front_mobile.testing.FakeUpcomingEvents
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun List<Cue>.moods() = filterIsInstance<Cue.Express>().map { it.expr }.filterIsInstance<Expr.MoodExpr>().map { it.mood }
private fun List<Cue>.reactions() = filterIsInstance<Cue.Express>().map { it.expr }.filterIsInstance<Expr.ReactionExpr>().map { it.reaction }

class StatusDisplayTest {
    private val s = StatusDisplay()
    @Test fun eachStateHasItsFace() = runTest {
        assertEquals(listOf(Mood.Surprised), s.react(Input.State(BotState.Listening)).moods())
        assertEquals(listOf(Mood.Focused), s.react(Input.State(BotState.Thinking)).moods())
        assertEquals(listOf(Mood.Happy), s.react(Input.State(BotState.Speaking)).moods())
        assertEquals(listOf(Mood.Sad), s.react(Input.State(BotState.Error)).moods())
    }
    @Test fun lowBatteryWorriesTheRestingFaceUntilCharging() = runTest {
        val low = s.react(Input.Device(DeviceEvent.BatteryLow(15)))
        assertEquals(listOf(Mood.Worried), low.moods()); assertTrue(Reaction.Sigh in low.reactions())
        assertEquals(listOf(Mood.Worried), s.react(Input.State(BotState.Idle)).moods())
        assertEquals(listOf(Mood.Neutral), s.react(Input.Device(DeviceEvent.ChargerConnected(16))).moods())
    }
    @Test fun batteryLowDoesNotOverrideAnError() = runTest {
        s.react(Input.State(BotState.Error)); assertEquals(listOf(Mood.Sad), s.react(Input.Device(DeviceEvent.BatteryLow(10))).moods())
    }
    @Test fun aBatteryWarningAboveTheThresholdIsIgnored() = runTest { assertTrue(s.react(Input.Device(DeviceEvent.BatteryLow(50))).isEmpty()) }
    @Test fun chargerWhenNotLowIsSilent() = runTest { assertTrue(s.react(Input.Device(DeviceEvent.ChargerConnected())).isEmpty()) }
}

class ToneTest {
    private val c = KeywordToneClassifier()
    @Test fun classifiesByKeyword() {
        assertEquals(Tone.Grateful, c.classify("Thanks a lot")); assertEquals(Tone.Praise, c.classify("good job bot"))
        assertEquals(Tone.Affection, c.classify("I love you")); assertEquals(Tone.Upset, c.classify("that was wrong"))
        assertEquals(Tone.Frustrated, c.classify("this is so useless")); assertEquals(Tone.Apology, c.classify("sorry about that"))
        assertEquals(Tone.Joking, c.classify("hahaha")); assertEquals(Tone.Farewell, c.classify("good night"))
        assertEquals(Tone.Greeting, c.classify("hello there")); assertEquals(Tone.Sleepy, c.classify("I'm tired"))
    }
    @Test fun shoutingAndPunctuationReadAsFrustration() {
        assertEquals(Tone.Frustrated, c.classify("OPEN THE DOOR NOW")); assertEquals(Tone.Frustrated, c.classify("why is it not working???"))
    }
    @Test fun plainSpeechAndEmptyAreNeutral() { assertEquals(Tone.Neutral, c.classify("what time is it")); assertEquals(Tone.Neutral, c.classify("  ")) }
    @Test fun shortAllCapsIsNotShouting() { assertEquals(Tone.Neutral, c.classify("OK")) }

    @Test fun respondsToPraiseWithPride() = runTest {
        val cues = ToneResponder().react(Input.Heard("great job")); assertEquals(listOf(Mood.Proud), cues.moods()); assertTrue(Reaction.Celebrate in cues.reactions())
    }
    @Test fun frustrationGetsWorriedFaceAndALowUrgencyApology() = runTest {
        val cues = ToneResponder().react(Input.Heard("you are useless"))
        assertEquals(listOf(Mood.Worried), cues.moods()); assertEquals(Urgency.Low, cues.filterIsInstance<Cue.Speak>().single().urgency)
    }
    @Test fun neutralAndFarewellProduceNothing() = runTest { assertTrue(ToneResponder().react(Input.Heard("what time is it")).isEmpty()); assertTrue(ToneResponder().react(Input.Heard("goodbye")).isEmpty()) }
    @Test fun ownErrorsShowSadness() = runTest { assertEquals(listOf(Mood.Sad), ToneResponder().react(Input.State(BotState.Error)).moods()) }
    @Test fun customClassifierIsUsed() = runTest { assertEquals(listOf(Mood.Love), ToneResponder { Tone.Affection }.react(Input.Heard("anything")).moods()) }
}

class GreetingBehaviorTest {
    private var now = 0L
    private fun b(hour: Int = 9) = GreetingBehavior(ArrivalGreeter({ now }, { hour }), hour = { hour })
    @Test fun greetsOnArrival() = runTest { assertEquals(listOf("Good morning."), b().react(Input.Device(DeviceEvent.Presence(true))).filterIsInstance<Cue.Speak>().map { it.text }) }
    @Test fun saysGoodbyeWhenTheUserDoes() = runTest { assertEquals(listOf("See you later."), b(14).react(Input.Heard("ok bye")).filterIsInstance<Cue.Speak>().map { it.text }) }
    @Test fun goodNightAtNight() = runTest { assertEquals(listOf("Good night."), b(23).react(Input.Heard("good night")).filterIsInstance<Cue.Speak>().map { it.text }) }
    @Test fun otherSpeechAndDeparturesAreSilent() = runTest { assertTrue(b().react(Input.Heard("hello")).isEmpty()); assertTrue(b().react(Input.Device(DeviceEvent.Presence(false))).isEmpty()) }
}

class AttentionCueTest {
    private var now = 0L
    private val a = AttentionCue(8_000) { now }
    @Test fun whenSpokenToLooksAtTheUserWithTheFrontCameraCheck() = runTest {
        val cues = a.react(Input.State(BotState.Listening))
        assertTrue(Cue.Call("presence_check") in cues); assertTrue(Cue.Look(0f, 0f) in cues); assertTrue(Reaction.Glance in cues.reactions())
    }
    @Test fun doesNotSpamTheCamera() = runTest {
        a.react(Input.State(BotState.Listening)); now = 3_000
        assertEquals(listOf<Cue>(Cue.Look(0f, 0f)), a.react(Input.State(BotState.Listening)))
        now = 9_000; assertTrue(Cue.Call("presence_check") in a.react(Input.State(BotState.Listening)))
    }
    @Test fun ignoresOtherStates() = runTest { assertTrue(a.react(Input.State(BotState.Thinking)).isEmpty()); assertTrue(a.react(Input.Tick).isEmpty()) }
}

class ProactiveSuggestionsTest {
    private var now = 0L
    private val cal = FakeUpcomingEvents()
    private fun p(hour: Int = 14) = ProactiveSuggestions(cal, { now }, { hour })
    private fun List<Cue>.said() = filterIsInstance<Cue.Speak>().map { it.text }

    @Test fun announcesAMeetingOnceAndImportantly() = runTest {
        cal.event = UpcomingEvent("e1", "Standup", 9 * 60_000L)
        val p = p(); val cues = p.react(Input.Tick)
        assertEquals(listOf("Standup starts in 9 minutes."), cues.said()); assertEquals(Urgency.Important, (cues.single() as Cue.Speak).urgency)
        assertTrue(p.react(Input.Tick).isEmpty())
    }
    @Test fun noMeetingNoSuggestion() = runTest { assertTrue(p().react(Input.Tick).isEmpty()) }
    @Test fun suggestsBatterySaverThenWaitsOutTheCooldown() = runTest {
        val p = p()
        assertTrue(p.react(Input.Device(DeviceEvent.BatteryLow(14))).said().single().contains("14 percent"))
        assertTrue(p.react(Input.Device(DeviceEvent.BatteryLow(13))).isEmpty())
        now = 61 * 60_000L; assertEquals(1, p.react(Input.Device(DeviceEvent.BatteryLow(12))).size)
    }
    @Test fun lateAtNightOffersAnAlarmOncePerCooldown() = runTest {
        val p = p(23)
        assertTrue(p.react(Input.Tick).said().single().contains("late")); assertTrue(p.react(Input.Tick).isEmpty())
        now = 5 * 3600_000L; assertEquals(1, p.react(Input.Tick).size)
    }
    @Test fun notLateInTheAfternoonOrOtherEvents() = runTest { assertTrue(p(15).react(Input.Tick).isEmpty()); assertTrue(p().react(Input.Heard("hi")).isEmpty()); assertTrue(p().react(Input.Device(DeviceEvent.ScreenOn())).isEmpty()) }
    @Test fun earlyMorningCountsAsLate() = runTest { assertEquals(1, p(3).react(Input.Tick).size) }
}

class EscalationTest {
    private var now = 0L
    private fun e() = Escalation({ now }, 20_000, 4)
    private fun List<Cue>.volumes() = filterIsInstance<Cue.Call>().filter { it.tool == "volume" }.map { it.args["level"]!!.jsonPrimitive.content.toInt() }

    @Test fun firstAlertIsPlainImportantSpeech() { val c = e().raise("pill", "Take your pill"); assertEquals(listOf<Cue>(Cue.Speak("Take your pill", Urgency.Important)), c) }
    @Test fun repeatsLouderEveryInterval() = runTest {
        val e = e(); e.raise("pill", "Take your pill")
        assertTrue(e.react(Input.Tick).isEmpty())
        now = 20_000; val second = e.react(Input.Tick); assertEquals(listOf(70), second.volumes()); assertTrue(second.any { it is Cue.Call && it.tool == "vibrate" })
        now = 40_000; val third = e.react(Input.Tick); assertEquals(listOf(100), third.volumes()); assertTrue((third.last() as Cue.Speak).text.startsWith("Important"))
    }
    @Test fun speakingAcknowledges() = runTest { val e = e(); e.raise("a", "x"); e.react(Input.Heard("ok")); now = 20_000; assertTrue(e.react(Input.Tick).isEmpty()); assertTrue(e.activeIds.isEmpty()) }
    @Test fun pickingUpThePhoneAcknowledges() = runTest { val e = e(); e.raise("a", "x"); e.react(Input.Device(DeviceEvent.Motion("pickup"))); assertTrue(e.activeIds.isEmpty()) }
    @Test fun givesUpAfterMaxRepeats() = runTest {
        val e = e(); e.raise("a", "x")
        repeat(3) { now += 20_000; e.react(Input.Tick) }
        now += 20_000; assertTrue(e.react(Input.Tick).isEmpty()); assertTrue(e.activeIds.isEmpty())
    }
    @Test fun ackByIdOnlyClearsThatAlert() { val e = e(); e.raise("a", "x"); e.raise("b", "y"); assertTrue(e.acknowledge("a")); assertEquals(setOf("b"), e.activeIds); assertTrue(!e.acknowledge("zzz")) }
}

class AmbientAwarenessTest {
    private var now = 0L
    private val sense = FakeAmbientSense()
    private val a = AmbientAwareness(sense, 60_000) { now }
    private fun List<Cue>.tools() = filterIsInstance<Cue.Call>().map { it.tool }

    @Test fun darkQuietRoomSoftensVoiceAndDimsScreen() = runTest {
        sense.snapshot = AmbientSnapshot(lux = 2f, noiseDbfs = -70)
        val cues = a.react(Input.Tick)
        assertEquals(listOf("volume", "screen_dim"), cues.tools()); assertEquals(RoomProfile.DarkQuiet, a.profile)
        assertEquals("25", (cues[0] as Cue.Call).args["level"]!!.jsonPrimitive.content)
    }
    @Test fun noisyRoomRaisesVolume() = runTest {
        sense.snapshot = AmbientSnapshot(lux = 300f, noiseDbfs = -15); a.react(Input.Tick); assertEquals(RoomProfile.Noisy, a.profile)
    }
    @Test fun actsOnlyOnChangeAndRespectsTheReadInterval() = runTest {
        sense.snapshot = AmbientSnapshot(lux = 2f, noiseDbfs = -70); a.react(Input.Tick)
        now = 30_000; sense.snapshot = AmbientSnapshot(lux = 500f); assertTrue(a.react(Input.Tick).isEmpty())
        now = 61_000; assertEquals(RoomProfile.Normal, run { a.react(Input.Tick); a.profile })
    }
    @Test fun unchangedRoomIsSilent() = runTest { sense.snapshot = AmbientSnapshot(lux = 200f, noiseDbfs = -50); assertTrue(a.react(Input.Tick).isEmpty()) }
    @Test fun hysteresisKeepsNoisyUntilClearlyQuieter() {
        assertEquals(RoomProfile.Noisy, RoomClassifier.classify(AmbientSnapshot(noiseDbfs = -28), RoomProfile.Noisy))
        assertEquals(RoomProfile.Normal, RoomClassifier.classify(AmbientSnapshot(noiseDbfs = -28), RoomProfile.Normal))
    }
    @Test fun missingSensorsMeanNormalAndNonTicksAreIgnored() = runTest { assertEquals(RoomProfile.Normal, RoomClassifier.classify(AmbientSnapshot(), RoomProfile.Normal)); assertTrue(a.react(Input.Heard("x")).isEmpty()) }
}
