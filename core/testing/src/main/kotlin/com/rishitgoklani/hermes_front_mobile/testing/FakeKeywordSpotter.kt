package com.rishitgoklani.hermes_front_mobile.testing

import com.rishitgoklani.hermes_front_mobile.domain.voice.KeywordSpotter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Scriptable spotter: each `listen` call waits for the next queued detection for that keyword set. */
class FakeKeywordSpotter : KeywordSpotter {
    private val wake = Channel<String>(Channel.UNLIMITED)
    private val stop = Channel<String>(Channel.UNLIMITED)
    val listenCalls = mutableListOf<List<String>>()
    fun hearWake(p: String = "hey bot") { wake.trySend(p) }
    fun hearStop(p: String = "stop") { stop.trySend(p) }
    override fun listen(keywords: List<String>): Flow<String> = flow {
        listenCalls += keywords
        emit((if (keywords.any { it.startsWith("hey") }) wake else stop).receive())
    }
}
