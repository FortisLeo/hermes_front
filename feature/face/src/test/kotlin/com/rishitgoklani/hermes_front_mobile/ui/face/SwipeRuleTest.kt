package com.rishitgoklani.hermes_front_mobile.ui.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeRuleTest {
    @Test fun commitsOnlyForALongLeftwardDrag() {
        assertTrue(SwipeRule.commits(-300f, 1000f)); assertFalse(SwipeRule.commits(-100f, 1000f)); assertFalse(SwipeRule.commits(400f, 1000f)); assertFalse(SwipeRule.commits(-300f, 0f))
    }
    @Test fun faceFollowsTheFingerLeftwardsOnly() { assertEquals(-60f, SwipeRule.follow(-100f), 0.01f); assertEquals(0f, SwipeRule.follow(50f), 0f) }
}
