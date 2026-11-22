package com.rishitgoklani.hermes_front_mobile.domain.avatar

import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarSafetyTest {
    @Test fun firstStartKeepsAvatarAndCounts() { assertEquals(AvatarSafety.Decision(true, 1), AvatarSafety.onStart(true, 0)) }
    @Test fun secondUncleanStartFallsBackToEyes() { assertEquals(AvatarSafety.Decision(false, 0), AvatarSafety.onStart(true, 1)) }
    @Test fun eyesModeNeverCounts() { assertEquals(AvatarSafety.Decision(false, 0), AvatarSafety.onStart(false, 0)) }
}
