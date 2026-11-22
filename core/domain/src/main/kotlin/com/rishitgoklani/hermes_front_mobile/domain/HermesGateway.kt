package com.rishitgoklani.hermes_front_mobile.domain

import kotlinx.coroutines.flow.Flow

/** Port: sends one user turn to Hermes and streams the reply text deltas. */
interface HermesGateway {
    fun send(text: String, imageJpegBase64: String? = null): Flow<String>
}

data class HermesConfig(
    val baseUrl: String = "http://127.0.0.1:8642",
    val apiKey: String = "",
    val sessionId: String = "deskbot-main",
)
