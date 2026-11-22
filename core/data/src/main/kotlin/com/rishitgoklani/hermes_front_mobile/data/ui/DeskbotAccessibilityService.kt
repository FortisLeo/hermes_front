package com.rishitgoklani.hermes_front_mobile.data.ui

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/** Holds the live instance so [AccessibilityUiAutomation] can drive it. Events are only used for foreground tracking. */
class DeskbotAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) lastPackage = event.packageName?.toString()
    }
    override fun onKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN && event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN && event.repeatCount == 0 && comboDetector.onPress()) onKillCombo?.invoke()
        return false // never consume: normal volume behaviour stays
    }
    override fun onInterrupt() {}
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }

    companion object {
        @Volatile var instance: DeskbotAccessibilityService? = null
        @Volatile var lastPackage: String? = null
        @Volatile var onKillCombo: (() -> Unit)? = null
        private val comboDetector = com.rishitgoklani.hermes_front_mobile.domain.safety.KeyComboDetector()
    }
}
