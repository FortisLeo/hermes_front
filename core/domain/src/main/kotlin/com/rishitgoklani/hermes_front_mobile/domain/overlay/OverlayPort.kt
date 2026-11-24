package com.rishitgoklani.hermes_front_mobile.domain.overlay

import com.rishitgoklani.hermes_front_mobile.domain.a2ui.A2uiStore
import com.rishitgoklani.hermes_front_mobile.domain.a2ui.Screen
import com.rishitgoklani.hermes_front_mobile.domain.a2ui.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Port: a window drawn above other apps. Implemented with TYPE_APPLICATION_OVERLAY on Android. [onDismiss] is invoked when the user closes the window. */
interface OverlayPort {
    /** True when the system lets us draw over other apps (SYSTEM_ALERT_WINDOW granted). */
    fun isAllowed(): Boolean
    /** Shows or replaces the content. Must not throw when the window is already showing. */
    suspend fun show(screen: Screen, onDismiss: () -> Unit)
    suspend fun hide()
}

/**
 * Bridges the A2UI overlay surface to a real window (#414). Shows the Overlay screen in the window when drawing over apps is allowed,
 * hides it when the screen is cleared/expired/dismissed, and reports [fallback] = true when the window cannot be used so the in-app card still shows it.
 */
class A2uiOverlayController(private val store: A2uiStore, private val port: OverlayPort, private val scope: CoroutineScope, private val sweepEveryMs: Long = 2_000) {
    private val _fallback = MutableStateFlow(false)
    /** True while an overlay screen exists but could not be shown in a window (permission missing or window failed). */
    val fallback: StateFlow<Boolean> = _fallback
    private var showing = false

    fun start() {
        scope.launch { store.screens.map { it[Surface.Overlay] }.distinctUntilChanged().collect { apply(it) } }
        scope.launch { while (true) { delay(sweepEveryMs); store.sweep() } }
    }

    /** Closes the window and removes the screen; wired to the window's close button. */
    fun dismiss() { store.clear(Surface.Overlay) }

    private suspend fun apply(screen: Screen?) {
        if (screen == null) { _fallback.value = false; if (showing) { showing = false; runCatching { port.hide() } }; return }
        if (!port.isAllowed()) { _fallback.value = true; return }
        val ok = runCatching { port.show(screen, ::dismiss) }.isSuccess
        showing = ok; _fallback.value = !ok
    }
}
