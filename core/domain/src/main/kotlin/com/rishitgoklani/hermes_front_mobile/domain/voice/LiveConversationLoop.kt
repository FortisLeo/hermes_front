package com.rishitgoklani.hermes_front_mobile.domain.voice

import com.rishitgoklani.hermes_front_mobile.domain.BotState
import com.rishitgoklani.hermes_front_mobile.domain.VoiceEvent
import com.rishitgoklani.hermes_front_mobile.domain.live.RunLiveSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

/**
 * Wake phrase (or a tap), then one Gemini Live conversation, then back to waiting.
 * The wake spotter releases the microphone before the session opens it; barge-in is the model's own (it hears you), so no stop-phrase spotter runs.
 * A conversation started by [requestStart] (the wave button) is kept open through silence until [requestStop]; one started by the wake phrase ends after a quiet spell.
 * A failed session is reported as an Error state and retried with backoff.
 */
class LiveConversationLoop(
    private val spotter: KeywordSpotter,
    private val runLive: () -> RunLiveSession,
    private val config: VoiceLoopConfig = VoiceLoopConfig(),
    private val onWake: () -> Unit = {},
    private val onKill: (String) -> Unit = {},
    /** Why a session failed (already free of the API key); the app logs it. */
    private val onError: (Throwable) -> Unit = {},
) : VoiceLoop {
    private val _events = MutableSharedFlow<VoiceEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<VoiceEvent> = _events
    private val startRequests = Channel<Unit>(Channel.CONFLATED)
    private val stopRequests = Channel<Unit>(Channel.CONFLATED)

    /** Starts a conversation now, without the wake phrase. Takes effect while the loop is waiting. */
    fun requestStart() { startRequests.trySend(Unit) }
    /** Ends the running conversation (and returns to waiting for the wake phrase). */
    fun requestStop() { stopRequests.trySend(Unit) }

    override suspend fun run() {
        var failures = 0
        while (true) {
            try {
                _events.emit(VoiceEvent.State(BotState.Idle))
                val heard = awaitTrigger()
                if (heard != null && heard in config.killPhrases) { onKill(heard); continue }
                val manual = heard == null
                onWake()
                failures = 0
                stopRequests.tryReceive() // a tap meant for an earlier conversation must not end this one
                coroutineScope {
                    val conversation = launch { runLive().invoke(keepAlive = manual).collect { _events.emit(it) } }
                    val stopper = launch { stopRequests.receive(); conversation.cancel() }
                    conversation.join(); stopper.cancel()
                }
                delay(config.restartDelayMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: NoSuchElementException) {
                delay(config.restartDelayMs)
            } catch (e: Exception) {
                onError(e)
                _events.emit(VoiceEvent.State(BotState.Error))
                failures++
                delay((config.restartDelayMs shl failures.coerceAtMost(6)).coerceAtMost(config.maxFailureBackoffMs))
            }
        }
    }

    /** The phrase heard, or null when the user tapped to start. */
    private suspend fun awaitTrigger(): String? = coroutineScope {
        val wake = async { spotter.listen(config.wakePhrases + config.killPhrases).first() }
        val tap = async { startRequests.receive() }
        select<String?> { wake.onAwait { it }; tap.onAwait { null } }.also { wake.cancel(); tap.cancel() }
    }
}
