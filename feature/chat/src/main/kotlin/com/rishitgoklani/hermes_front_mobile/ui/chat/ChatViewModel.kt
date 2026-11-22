package com.rishitgoklani.hermes_front_mobile.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rishitgoklani.hermes_front_mobile.domain.BotState
import com.rishitgoklani.hermes_front_mobile.domain.ChatMessage
import com.rishitgoklani.hermes_front_mobile.domain.HermesGateway
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val bot: BotState = BotState.Idle,
    val error: String? = null,
)

class ChatViewModel(private val gateway: HermesGateway, private val onExpression: (com.rishitgoklani.hermes_front_mobile.domain.face.Expr) -> Unit = {}) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state
    private var nextId = 0L
    private var job: Job? = null

    fun send(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty() || job?.isActive == true) return
        val replyId = nextId + 1
        _state.update {
            it.copy(
                messages = it.messages + ChatMessage(nextId, true, prompt) + ChatMessage(replyId, false, ""),
                bot = BotState.Thinking, error = null,
            )
        }
        nextId += 2
        job = viewModelScope.launch {
            try {
                val tags = com.rishitgoklani.hermes_front_mobile.domain.face.TagFilter()
                fun show(segments: List<com.rishitgoklani.hermes_front_mobile.domain.face.TagFilter.Segment>) = segments.forEach { seg ->
                    when (seg) {
                        is com.rishitgoklani.hermes_front_mobile.domain.face.TagFilter.Segment.Tag -> onExpression(seg.expr)
                        is com.rishitgoklani.hermes_front_mobile.domain.face.TagFilter.Segment.Text -> _state.update { s ->
                            s.copy(bot = BotState.Speaking, messages = s.messages.map { if (it.id == replyId) it.copy(text = it.text + seg.text) else it })
                        }
                    }
                }
                gateway.send(prompt).collect { delta -> show(tags.feed(delta)) }
                show(tags.flush())
                _state.update { it.copy(bot = BotState.Idle) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(bot = BotState.Error, error = e.message ?: "Hermes unreachable") }
            }
        }
    }
}
