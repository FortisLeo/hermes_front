package com.rishitgoklani.hermes_front_mobile.testing

import com.rishitgoklani.hermes_front_mobile.domain.SpeechSynthesizer
import com.rishitgoklani.hermes_front_mobile.domain.SpeechToText
import com.rishitgoklani.hermes_front_mobile.domain.SttEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeSpeechToText(private val events: List<SttEvent> = emptyList(), private val failure: Throwable? = null) : SpeechToText {
    override fun listen(): Flow<SttEvent> = flow { events.forEach { emit(it) }; failure?.let { throw it } }
}

class FakeSpeechSynthesizer : SpeechSynthesizer {
    val spoken = mutableListOf<String>()
    var stopped = false
    var awaited = false
    override fun enqueue(text: String) { spoken += text }
    override suspend fun awaitDone() { awaited = true }
    override fun stop() { stopped = true }
}
