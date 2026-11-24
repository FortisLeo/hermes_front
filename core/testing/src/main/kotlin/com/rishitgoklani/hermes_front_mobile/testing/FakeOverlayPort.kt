package com.rishitgoklani.hermes_front_mobile.testing

import com.rishitgoklani.hermes_front_mobile.domain.a2ui.Screen
import com.rishitgoklani.hermes_front_mobile.domain.overlay.OverlayPort

class FakeOverlayPort(var allowed: Boolean = true, var failing: Boolean = false) : OverlayPort {
    val shown = mutableListOf<Screen>(); var hides = 0; var visible = false; var dismissCallback: (() -> Unit)? = null
    override fun isAllowed() = allowed
    override suspend fun show(screen: Screen, onDismiss: () -> Unit) { if (failing) error("window failed"); shown += screen; visible = true; dismissCallback = onDismiss }
    override suspend fun hide() { hides++; visible = false }
    /** Simulates the user pressing the window's close button. */
    fun userDismiss() { dismissCallback?.invoke() }
}
