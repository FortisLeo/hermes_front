package com.rishitgoklani.hermes_front_mobile.domain.voice

import com.rishitgoklani.hermes_front_mobile.domain.VoiceEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge

/** Port: an always-on voice loop (wake phrase, then a conversation). Hermes and Gemini Live are two implementations. */
interface VoiceLoop {
    val events: Flow<VoiceEvent>
    /** Runs until cancelled. */
    suspend fun run()
}

/** Picks the backend each time the loop (re)starts, so the Settings switch takes effect on the next wake-up. */
class SwitchingVoiceLoop(
    private val hermes: VoiceLoop,
    private val live: VoiceLoop,
    private val useLive: suspend () -> Boolean,
) : VoiceLoop {
    override val events: Flow<VoiceEvent> = merge(hermes.events, live.events)
    override suspend fun run() { if (useLive()) live.run() else hermes.run() }
}
