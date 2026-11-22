package com.rishitgoklani.hermes_front_mobile.domain.voice

import com.rishitgoklani.hermes_front_mobile.domain.BotState
import com.rishitgoklani.hermes_front_mobile.domain.RunVoiceTurn
import com.rishitgoklani.hermes_front_mobile.domain.SpeechSynthesizer
import com.rishitgoklani.hermes_front_mobile.domain.VoiceEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Port: keeps the microphone open and emits when one of [keywords] is heard. Completes/throws when the mic is lost. */
interface KeywordSpotter {
    fun listen(keywords: List<String>): Flow<String>
}

data class VoiceLoopConfig(val wakePhrases: List<String> = listOf("hey bot"), val stopPhrases: List<String> = listOf("stop", "be quiet"), val restartDelayMs: Long = 500, val maxFailureBackoffMs: Long = 30_000, val killPhrases: List<String> = listOf("emergency stop", "kill switch"))

/**
 * Always-listening voice loop: wake phrase -> one voice turn -> back to waiting.
 * While the bot is speaking a stop phrase cancels the turn and silences TTS (barge-in).
 * Events (state, expressions, text) are re-published on [events] for the UI/face.
 */
class ConversationLoop(
    private val spotter: KeywordSpotter,
    private val runTurn: RunVoiceTurn,
    private val tts: SpeechSynthesizer,
    private val config: VoiceLoopConfig = VoiceLoopConfig(),
    private val onWake: () -> Unit = {},
    private val onKill: (String) -> Unit = {},
) {
    private val _events = MutableSharedFlow<VoiceEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<VoiceEvent> = _events

    /** Runs until cancelled. Failures of the mic or a turn are reported as Error state and retried with backoff. */
    suspend fun run() {
        var failures = 0
        while (true) {
            try {
                _events.emit(VoiceEvent.State(BotState.Idle))
                val heard = spotter.listen(config.wakePhrases + config.killPhrases).first()
                if (heard in config.killPhrases) { onKill(heard); continue }
                onWake()
                failures = 0
                turnWithBargeIn()
                delay(config.restartDelayMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: NoSuchElementException) {
                delay(config.restartDelayMs) // spotter completed without a detection (mic released); try again
            } catch (e: Exception) {
                _events.emit(VoiceEvent.State(BotState.Error))
                failures++
                delay((config.restartDelayMs shl failures.coerceAtMost(6)).coerceAtMost(config.maxFailureBackoffMs))
            }
        }
    }

    private suspend fun turnWithBargeIn() = coroutineScope {
        val turn: Job = launch { runTurn().collect { _events.emit(it) } }
        val bargeIn: Job = launch {
            // Only meaningful once the bot starts speaking, but harmless earlier: "stop" while thinking also cancels.
            try { val heard = spotter.listen(config.stopPhrases + config.killPhrases).first(); turn.cancel(); tts.stop(); if (heard in config.killPhrases) onKill(heard) } catch (e: NoSuchElementException) { } catch (e: CancellationException) { throw e } catch (e: Exception) { /* mic busy during STT is expected */ }
        }
        turn.join(); bargeIn.cancel()
    }
}
