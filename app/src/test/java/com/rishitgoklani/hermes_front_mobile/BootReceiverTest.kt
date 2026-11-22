package com.rishitgoklani.hermes_front_mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootReceiverTest {
    @Test fun startsOnBootAndOnUpdate() {
        assertTrue(BootReceiver.shouldStart("android.intent.action.BOOT_COMPLETED"))
        assertTrue(BootReceiver.shouldStart("android.intent.action.MY_PACKAGE_REPLACED"))
    }
    @Test fun ignoresOtherActions() { assertFalse(BootReceiver.shouldStart("android.intent.action.SCREEN_ON")); assertFalse(BootReceiver.shouldStart(null)) }
}
