package com.rishitgoklani.hermes_front_mobile.domain.safety

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Emergency stop: once triggered the bridge refuses tool calls until a *local* reset (app UI / voice "resume"), never from Hermes. */
class KillSwitch(private val onTrigger: () -> Unit = {}, private val clock: () -> Long = System::currentTimeMillis) {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active
    var reason: String = ""; private set
    var triggeredAt: Long = 0; private set

    fun trigger(why: String) { if (_active.value) return; reason = why; triggeredAt = clock(); _active.value = true; onTrigger() }
    fun reset() { _active.value = false; reason = "" }
    fun isActive() = _active.value
}

/** Detects N presses of a key within a window (default: volume-down x3 in 1.5 s). */
class KeyComboDetector(private val required: Int = 3, private val windowMs: Long = 1500, private val clock: () -> Long = System::currentTimeMillis) {
    private val presses = ArrayDeque<Long>()
    /** Call on each key-down; returns true when the combo completes (and resets). */
    fun onPress(): Boolean {
        val now = clock(); presses.addLast(now)
        while (presses.isNotEmpty() && now - presses.first() > windowMs) presses.removeFirst()
        if (presses.size >= required) { presses.clear(); return true }
        return false
    }
}
