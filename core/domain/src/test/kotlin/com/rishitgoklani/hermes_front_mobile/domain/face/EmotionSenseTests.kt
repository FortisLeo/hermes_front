package com.rishitgoklani.hermes_front_mobile.domain.face

import com.rishitgoklani.hermes_front_mobile.domain.routines.DeviceEvent
import com.rishitgoklani.hermes_front_mobile.domain.routines.Trigger
import com.rishitgoklani.hermes_front_mobile.domain.routines.TriggerMatcher
import com.rishitgoklani.hermes_front_mobile.domain.sense.ChangeDetector
import com.rishitgoklani.hermes_front_mobile.domain.sense.FrameDiff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EmotionSenseTests {
    @Test fun thanksAndPraiseAndCorrections() {
        assertEquals(Expr.ReactionExpr(Reaction.HeartEyes, 0.8f), EmotionHints.forUtterance("Thanks a lot!")); assertEquals(Mood.Proud, (EmotionHints.forUtterance("good job bot") as Expr.MoodExpr).mood)
        assertEquals(Mood.Sad, (EmotionHints.forUtterance("That's wrong") as Expr.MoodExpr).mood); assertEquals(Mood.Worried, (EmotionHints.forUtterance("sorry about that") as Expr.MoodExpr).mood); assertEquals(Mood.Sleepy, (EmotionHints.forUtterance("good night") as Expr.MoodExpr).mood)
        assertNull(EmotionHints.forUtterance("what time is it")); assertNull(EmotionHints.forUtterance("thankful")) 
    }
    @Test fun greetingsDependOnAbsenceAndHour() {
        assertNull(Greetings.greeting(9, 10 * 60_000)); assertEquals("Good morning, Sam.", Greetings.greeting(9, 45 * 60_000, "Sam")); assertEquals("Good evening. Welcome back.", Greetings.greeting(19, 8 * 3600_000)); assertEquals("Hello.", Greetings.greeting(2, 3600_000))
        assertEquals("Good night.", Greetings.farewell(23)); assertEquals("See you later.", Greetings.farewell(14))
    }
    private fun thumb(v: Int, n: Int = 64) = ByteArray(n) { v.toByte() }
    @Test fun frameDiffScores() { assertEquals(0.0, FrameDiff.score(thumb(10), thumb(10)), 0.0); assertEquals(50.0, FrameDiff.score(thumb(0), thumb(50)), 0.0); assertEquals(255.0, FrameDiff.score(thumb(0), thumb(255)), 0.0); assertThrows(IllegalArgumentException::class.java) { FrameDiff.score(thumb(1, 4), thumb(1, 5)) } }
    @Test fun detectorFiresOnceWithCooldownAndIgnoresSlowDrift() {
        var now = 0L; val d = ChangeDetector(threshold = 20.0, cooldownMs = 10_000) { now }
        assertNull(d.update(thumb(100))); now += 1000; assertNull(d.update(thumb(105))); assertNotNull(d.update(thumb(200)).also { now += 1000 }); assertNull(d.update(thumb(30))); now += 20_000; assertNotNull(d.update(thumb(150)))
    }
    @Test fun clipboardAndSoundTriggers() {
        assertTrue(TriggerMatcher.matches(Trigger("clipboard", contains = "http"), DeviceEvent.Clipboard("see https://x.y"))); assertTrue(!TriggerMatcher.matches(Trigger("clipboard", contains = "http"), DeviceEvent.Clipboard("plain")))
        assertTrue(TriggerMatcher.matches(Trigger("sound", kind = "loud"), DeviceEvent.Sound("loud", -10))); assertTrue(TriggerMatcher.matches(Trigger("visual_change"), DeviceEvent.VisualChange(40)))
    }
}
