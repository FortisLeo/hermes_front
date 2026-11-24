package com.rishitgoklani.hermes_front_mobile.domain.live

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

/** One function the model may call. [parametersJson] is an OpenAPI-style schema object, or null when the tool takes no arguments. */
data class LiveToolDecl(val name: String, val description: String, val parametersJson: String? = null, val nonBlocking: Boolean = false)

/** Everything fixed for one Live session. A session cannot change these once open; a reconnect passes [resumeHandle]. */
data class LiveConfig(
    val model: String,
    val voice: String,
    val systemInstruction: String,
    val tools: List<LiveToolDecl> = emptyList(),
    val googleSearch: Boolean = false,
    val transcribe: Boolean = true,
    val resumeHandle: String? = null,
    val silenceMs: Int = 700,
    val allowBargeIn: Boolean = true,
)

data class LiveCall(val id: String, val name: String, val args: JsonObject)
data class LiveResult(val id: String, val name: String, val response: JsonObject)

/** What a session reports back. Audio is raw 16-bit little-endian mono PCM at 24 kHz. */
sealed interface LiveEvent {
    data class AudioOut(val pcm: ByteArray) : LiveEvent
    /** A piece of what the user said (transcription arrives in fragments). */
    data class InputText(val text: String) : LiveEvent
    /** A piece of what the model said. */
    data class OutputText(val text: String) : LiveEvent
    data class ToolCall(val calls: List<LiveCall>) : LiveEvent
    /** The user spoke over a pending call; undo its side effects if possible. */
    data class ToolCancel(val ids: List<String>) : LiveEvent
    /** The user barged in: drop queued audio. */
    data object Interrupted : LiveEvent
    data object TurnComplete : LiveEvent
    /** The server will close soon; reconnect with the last [Resumable] handle. */
    data class GoAway(val timeLeftMs: Long) : LiveEvent
    data class Resumable(val handle: String) : LiveEvent
    data class Closed(val reason: String, val error: Boolean) : LiveEvent
}

/** Port: one bidirectional streaming session with the model. [events] is hot; collect it before [open]. */
interface LiveSession {
    val events: Flow<LiveEvent>
    /** Suspends until the server confirms the setup. Throws on a rejected key, model or config. */
    suspend fun open(config: LiveConfig)
    /** 16 kHz mono PCM16 from the microphone. */
    fun sendAudio(pcm: ByteArray)
    /** One JPEG frame (at most about 1 per second). */
    fun sendFrame(jpeg: ByteArray)
    fun sendText(text: String)
    /** Tells the server the microphone paused, so it can flush buffered speech. */
    fun audioStreamEnd()
    fun sendToolResponses(responses: List<LiveResult>)
    fun close()
}

/** Port: microphone as 16 kHz mono PCM16 chunks. Cold: collecting opens the mic, cancelling releases it. */
fun interface LiveAudioIn { fun chunks(): Flow<ByteArray> }

/** Port: streaming playback of 24 kHz mono PCM16. */
interface LiveAudioOut {
    fun play(pcm: ByteArray)
    /** Drops everything queued (barge-in, end of session). */
    fun flush()
    /** True while audio is queued or sounding. */
    val playing: StateFlow<Boolean>
    /** 0..1 loudness of what is sounding, for lip sync. */
    val level: StateFlow<Float>
}

/** Persisted settings for the Gemini backend. The key lives in DataStore only. */
data class LiveSettings(
    val enabled: Boolean = false,
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    val voice: String = "Kore",
    /** Mute the mic while the bot talks, so its own voice cannot interrupt it (no echo cancellation needed). */
    val halfDuplex: Boolean = true,
    val idleSeconds: Int = 25,
    /** Offer every phone tool and the skill guides (true) or only the essential tools (false: leaner, faster to set up). */
    val allTools: Boolean = true,
) {
    val ready: Boolean get() = enabled && apiKey.isNotBlank()
    companion object { const val DEFAULT_MODEL = "gemini-3.8-live" }
}

interface LiveSettingsRepository {
    val settings: Flow<LiveSettings>
    suspend fun save(settings: LiveSettings)
}
