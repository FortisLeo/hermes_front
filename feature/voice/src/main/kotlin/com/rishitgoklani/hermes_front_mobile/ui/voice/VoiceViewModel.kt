package com.rishitgoklani.hermes_front_mobile.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rishitgoklani.hermes_front_mobile.domain.BotState
import com.rishitgoklani.hermes_front_mobile.domain.RunVoiceTurn
import com.rishitgoklani.hermes_front_mobile.domain.VoiceEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VoiceUiState(
    val bot: BotState = BotState.Idle,
    val heard: String = "",
    val reply: String = "",
    val error: String? = null,
    val timing: String = "",
)

class VoiceViewModel(private val runTurn: RunVoiceTurn, private val onExpression: (com.rishitgoklani.hermes_front_mobile.domain.face.Expr) -> Unit = {}) : ViewModel() {
    private val _state = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = _state
    private var job: Job? = null

    fun talk() {
        if (job?.isActive == true) return
        _state.value = VoiceUiState()
        job = viewModelScope.launch {
            try {
                runTurn().collect { e ->
                    _state.update {
                        when (e) {
                            is VoiceEvent.State -> it.copy(bot = e.state)
                            is VoiceEvent.Partial -> it.copy(heard = e.text)
                            is VoiceEvent.Heard -> it.copy(heard = e.text)
                            is VoiceEvent.ReplyDelta -> it.copy(reply = it.reply + e.text)
                            is VoiceEvent.Expression -> { onExpression(e.expr); it }
                            is VoiceEvent.Timing -> it.copy(timing = "first token ${e.timing.firstTokenMs} ms, first speech ${e.timing.firstSentenceMs} ms" + if (e.timing.visionAttached) ", camera" else "")
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(bot = BotState.Error, error = e.message ?: "Voice failed") }
            }
        }
    }

    fun cancel() { job?.cancel(); _state.update { it.copy(bot = BotState.Idle) } }
}
