package com.rishitgoklani.hermes_front_mobile.testing

import com.rishitgoklani.hermes_front_mobile.domain.live.LiveAudioIn
import com.rishitgoklani.hermes_front_mobile.domain.live.LiveAudioOut
import com.rishitgoklani.hermes_front_mobile.domain.live.LiveConfig
import com.rishitgoklani.hermes_front_mobile.domain.live.LiveEvent
import com.rishitgoklani.hermes_front_mobile.domain.live.LiveResult
import com.rishitgoklani.hermes_front_mobile.domain.live.LiveSession
import com.rishitgoklani.hermes_front_mobile.domain.live.LiveSettings
import com.rishitgoklani.hermes_front_mobile.domain.live.LiveSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Scriptable live session: tests [emit] server events and read back what the app sent. */
class FakeLiveSession : LiveSession {
    private val server = MutableSharedFlow<LiveEvent>(extraBufferCapacity = 256)
    override val events: Flow<LiveEvent> = server
    val opened = mutableListOf<LiveConfig>()
    var openError: Throwable? = null
    val audio = mutableListOf<ByteArray>()
    val frames = mutableListOf<ByteArray>()
    val texts = mutableListOf<String>()
    val toolResponses = mutableListOf<LiveResult>()
    var closes = 0
    suspend fun emit(vararg e: LiveEvent) = e.forEach { server.emit(it) }
    override suspend fun open(config: LiveConfig) { openError?.let { throw it }; opened += config }
    override fun sendAudio(pcm: ByteArray) { audio += pcm }
    override fun sendFrame(jpeg: ByteArray) { frames += jpeg }
    override fun sendText(text: String) { texts += text }
    override fun audioStreamEnd() {}
    override fun sendToolResponses(responses: List<LiveResult>) { toolResponses += responses }
    override fun close() { closes++ }
}

/** Microphone fed by the test through [mic]. */
class FakeLiveAudioIn : LiveAudioIn {
    val mic = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    override fun chunks(): Flow<ByteArray> = mic
}

class FakeLiveAudioOut : LiveAudioOut {
    val played = mutableListOf<ByteArray>()
    var flushes = 0
    private val _playing = MutableStateFlow(false)
    override val playing: StateFlow<Boolean> = _playing
    private val _level = MutableStateFlow(0f)
    override val level: StateFlow<Float> = _level
    fun setPlaying(v: Boolean) { _playing.value = v }
    override fun play(pcm: ByteArray) { played += pcm }
    override fun flush() { flushes++ }
}

class FakeLiveSettings(initial: LiveSettings = LiveSettings(enabled = true, apiKey = "test-key")) : LiveSettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<LiveSettings> = state
    override suspend fun save(settings: LiveSettings) { state.value = settings }
}
