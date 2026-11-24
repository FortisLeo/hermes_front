package com.rishitgoklani.hermes_front_mobile.domain.messaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsRulesTest {
    @Test fun acceptsConfirmedMessageAndCleansNumber() =
        assertEquals(SmsCheck.Ok("+911234567890", "hi"), SmsRules.check("+91 (123) 456-7890", " hi ", true))

    @Test fun refusesWithoutConfirm() =
        assertTrue((SmsRules.check("12345", "hi", false) as SmsCheck.Refused).reason.contains("confirm=true"))

    @Test fun refusesBadNumbers() {
        assertTrue(SmsRules.check("12", "hi", true) is SmsCheck.Refused)
        assertTrue(SmsRules.check("12+345", "hi", true) is SmsCheck.Refused)
        assertTrue(SmsRules.check(null, "hi", true) is SmsCheck.Refused)
    }

    @Test fun refusesEmptyAndTooLongText() {
        assertTrue(SmsRules.check("12345", "  ", true) is SmsCheck.Refused)
        assertTrue(SmsRules.check("12345", "x".repeat(SmsRules.MAX_CHARS + 1), true) is SmsCheck.Refused)
    }
}
