package com.rishitgoklani.hermes_front_mobile.data.capabilities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CapabilityLogicTest {
    private val apps = mapOf("com.google.android.youtube" to "YouTube", "com.android.settings" to "Settings", "com.android.chrome" to "Chrome", "com.google.chrome.beta" to "Chrome Beta")

    @Test fun matchesPackageExact() = assertEquals("com.android.settings", AppMatcher.match("com.android.settings", apps))
    @Test fun matchesLabelCaseInsensitive() = assertEquals("com.google.android.youtube", AppMatcher.match("youtube", apps))
    @Test fun exactLabelBeatsPrefix() = assertEquals("com.android.chrome", AppMatcher.match("chrome", apps))
    @Test fun ambiguousContainsIsNull() = assertNull(AppMatcher.match("o", apps))
    @Test fun blankIsNull() = assertNull(AppMatcher.match("  ", apps))
    @Test fun uniquePrefix() = assertEquals("com.android.settings", AppMatcher.match("sett", apps))

    @Test fun percentMath() { assertEquals(50, BatteryMath.percent(50, 100)); assertEquals(-1, BatteryMath.percent(-1, 100)); assertEquals(-1, BatteryMath.percent(5, 0)) }
    @Test fun tempMath() = assertEquals(31.5, BatteryMath.tempC(315), 0.001)
}
