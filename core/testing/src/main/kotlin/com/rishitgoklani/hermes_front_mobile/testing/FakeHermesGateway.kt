package com.rishitgoklani.hermes_front_mobile.testing

import com.rishitgoklani.hermes_front_mobile.domain.HermesGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Shared fake: emits the scripted deltas, or throws [failure] if set. Records sent prompts. */
class FakeHermesGateway(
    private val deltas: List<String> = emptyList(),
    private val failure: Throwable? = null,
) : HermesGateway {
    val sent = mutableListOf<String>()
    override fun send(text: String, imageJpegBase64: String?): Flow<String> = flow {
        sent += text
        deltas.forEach { emit(it) }
        failure?.let { throw it }
    }
}
