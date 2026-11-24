package com.rishitgoklani.hermes_front_mobile.data.live

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import com.rishitgoklani.hermes_front_mobile.domain.live.LiveAudioIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.IOException

/**
 * Microphone as 16 kHz mono PCM16 in 40 ms chunks. Uses the voice-communication source so the system's echo cancellation and noise suppression apply,
 * and switches the audio effects on explicitly where the device offers them. The mic is open only while the flow is collected.
 */
class AudioRecordLiveInput(private val chunkMs: Int = 40) : LiveAudioIn {
    override fun chunks(): Flow<ByteArray> = flow {
        val rate = 16_000
        val chunkBytes = rate * 2 * chunkMs / 1000
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, chunkBytes * 4))
        if (record.state != AudioRecord.STATE_INITIALIZED) { record.release(); throw IOException("microphone unavailable") }
        val effects = listOfNotNull(
            if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(record.audioSessionId) else null,
            if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(record.audioSessionId) else null,
            if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(record.audioSessionId) else null,
        ).onEach { it.enabled = true }
        try {
            record.startRecording()
            val buf = ByteArray(chunkBytes)
            while (currentCoroutineContext().isActive) {
                val n = record.read(buf, 0, buf.size)
                if (n < 0) throw IOException("microphone read failed ($n)")
                if (n > 0) emit(buf.copyOf(n))
            }
        } finally {
            effects.forEach { runCatching { it.release() } }
            runCatching { record.stop() }; record.release()
        }
    }.flowOn(Dispatchers.IO)
}
