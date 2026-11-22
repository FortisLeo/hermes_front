package com.rishitgoklani.hermes_front_mobile.domain.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleFaceTest {
    @Test fun happyUsesArcs() { assertTrue(BubbleFace.from(FaceState(mood = Mood.Happy)).arcs) }
    @Test fun sleepyHalfClosesEyes() { assertTrue(BubbleFace.from(FaceState(mood = Mood.Sleepy)).openness < 0.5f) }
    @Test fun neutralIsOpenWithDefaultColour() { val b = BubbleFace.from(FaceState()); assertEquals(0.9f, b.openness, 0f); assertEquals(0xFF7FDBFF, b.colorHex) }
}
