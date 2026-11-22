package com.rishitgoklani.hermes_front_mobile.domain.voice

import com.rishitgoklani.hermes_front_mobile.domain.BotState
import com.rishitgoklani.hermes_front_mobile.domain.VoiceEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Owns the lifecycle of the always-listening loop: start/stop, persisted preference, live bot state and forwarded events. */
class WakeController(
    private val loop: ConversationLoop,
    private val scope: CoroutineScope,
    private val persist: (Boolean) -> Unit = {},
    initiallyEnabled: Boolean = false,
) {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled
    private val _state = MutableStateFlow(BotState.Idle)
    val state: StateFlow<BotState> = _state
    private val _events = MutableSharedFlow<VoiceEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<VoiceEvent> = _events
    private var runJob: Job? = null
    private var collectJob: Job? = null

    init { if (initiallyEnabled) start(save = false) }

    fun start(save: Boolean = true) {
        if (runJob?.isActive == true) return
        _enabled.value = true; if (save) persist(true)
        collectJob = scope.launch { loop.events.collect { e -> if (e is VoiceEvent.State) _state.value = e.state; _events.emit(e) } }
        runJob = scope.launch { loop.run() }
    }

    fun stop() {
        runJob?.cancel(); collectJob?.cancel(); runJob = null; collectJob = null
        _enabled.value = false; _state.value = BotState.Idle; persist(false)
    }

    fun toggle() = if (_enabled.value) stop() else start()
}
