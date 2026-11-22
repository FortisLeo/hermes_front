package com.rishitgoklani.hermes_front_mobile.domain

import kotlinx.coroutines.flow.Flow

sealed interface SttEvent {
    data class Partial(val text: String) : SttEvent
    data class Final(val text: String) : SttEvent
}

/** Port: one listening session. The flow completes after the final result; errors are thrown. */
interface SpeechToText { fun listen(): Flow<SttEvent> }

/** Port: queued speech output. [enqueue] never blocks; [awaitDone] suspends until the queue drains. */
interface SpeechSynthesizer {
    fun enqueue(text: String)
    suspend fun awaitDone()
    fun stop()
}
