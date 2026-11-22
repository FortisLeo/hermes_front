package com.rishitgoklani.hermes_front_mobile.data.capabilities

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentRulesTest {
    @Test fun actionsAllowListed() { listOf("android.intent.action.VIEW", "android.settings.WIFI_SETTINGS", "android.media.action.IMAGE_CAPTURE", "android.provider.AlarmClock").forEach { assertTrue(it, IntentRules.actionAllowed(it)) }; listOf("com.evil.ACTION", "android.net.wifi.PICK_WIFI_NETWORK_X", "").forEach { assertFalse(it, IntentRules.actionAllowed(it)) } }
}
