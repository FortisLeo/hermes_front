package com.rishitgoklani.hermes_front_mobile.data.wake

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.rishitgoklani.hermes_front_mobile.domain.voice.KeywordSpotter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import org.vosk.Model
import org.vosk.Recognizer
import java.io.IOException
import kotlin.coroutines.coroutineContext

/** Offline keyword spotting with Vosk (Apache-2.0): restricted grammar, 16 kHz mono mic stream, releases the mic on completion. */
class VoskKeywordSpotter(private val models: ModelStore) : KeywordSpotter {
    @Volatile private var model: Model? = null

    private suspend fun model(): Model = model ?: synchronized(this) { model }.let { it ?: Model(models.ensure().path).also { m -> model = m } }

    @SuppressLint("MissingPermission")
    override fun listen(keywords: List<String>): Flow<String> = flow {
        val m = model()
        val rate = 16_000
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, 8000))
        if (record.state != AudioRecord.STATE_INITIALIZED) { record.release(); throw IOException("microphone unavailable") }
        val recognizer = Recognizer(m, rate.toFloat(), KeywordMatcher.grammar(keywords))
        try {
            record.startRecording()
            val buf = ByteArray(4000)
            while (coroutineContext.isActive) {
                val n = record.read(buf, 0, buf.size)
                if (n < 0) throw IOException("microphone read failed ($n)")
                val result = if (recognizer.acceptWaveForm(buf, n)) recognizer.result else recognizer.partialResult
                KeywordMatcher.match(KeywordMatcher.textOf(result), keywords)?.let { emit(it); return@flow }
            }
        } finally {
            runCatching { record.stop() }; record.release(); recognizer.close()
        }
    }.flowOn(Dispatchers.IO)
}
