package com.rishitgoklani.hermes_front_mobile.domain.ui

/**
 * Observed on a Redmi (HyperOS, Android 16): dispatching a two-stroke accessibility gesture made libmiinputflinger
 * (MiTouchState::onMotionEvent) throw std::out_of_range, aborting system_server (framework restart). Multi-stroke gestures
 * are therefore refused on the Xiaomi family; single-stroke taps/swipes and root `input` commands are unaffected.
 */
object MultiTouchPolicy {
    const val REASON = "multi-touch gestures are disabled on Xiaomi/Redmi/POCO devices: they crashed the system input service"
    private val families = setOf("xiaomi", "redmi", "poco", "blackshark")
    fun blocked(manufacturer: String?, brand: String?): Boolean = listOf(manufacturer, brand).any { it?.trim()?.lowercase() in families }
}
