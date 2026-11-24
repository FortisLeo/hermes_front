package com.rishitgoklani.hermes_front_mobile.domain.voice

import com.rishitgoklani.hermes_front_mobile.domain.BotState
import com.rishitgoklani.hermes_front_mobile.domain.VoiceEvent
import com.rishitgoklani.hermes_front_mobile.domain.live.RunLiveSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first

/**
 * Wake phrase, then one Gemini Live conversation, then back to waiting.
 * The wake spotter releases the microphone before the session opens it; barge-in is the model's own (it hears you), so no stop-phrase spotter runs.
 * A failed session is reported as an Error state and retried with backoff.
 */
class LiveConversationLoop(
    private val spotter: KeywordSpotter,
    private val runLive: () -> RunLiveSession,
    private val config: VoiceLoopConfig = VoiceLoopConfig(),
    private val onWake: () -> Unit = {},
    private val onKill: (String) -> Unit = {},
) : VoiceLoop {
    private val _events = MutableSharedFlow<VoiceEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<VoiceEvent> = _events

    override suspend fun run() {
        var failures = 0
        while (true) {
            try {
                _events.emit(VoiceEvent.State(BotState.Idle))
                val heard = spotter.listen(config.wakePhrases + config.killPhrases).first()
                if (heard in config.killPhrases) { onKill(heard); continue }
                onWake()
                runLive()().collect { _events.emit(it) }
                failures = 0
                delay(config.restartDelayMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: NoSuchElementException) {
                delay(config.restartDelayMs)
            } catch (e: Exception) {
                _events.emit(VoiceEvent.State(BotState.Error))
                failures++
                delay((config.restartDelayMs shl failures.coerceAtMost(6)).coerceAtMost(config.maxFailureBackoffMs))
            }
        }
    }
}
