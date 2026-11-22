package com.rishitgoklani.hermes_front_mobile.data.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.rishitgoklani.hermes_front_mobile.domain.SpeechSynthesizer
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicInteger

/** Adapter over android.speech.tts.TextToSpeech using QUEUE_ADD so sentences play back to back. */
class AndroidSpeechSynthesizer(context: Context) : SpeechSynthesizer {
    private val ready = CompletableDeferred<Boolean>()
    private val pending = AtomicInteger(0)
    @Volatile private var drained = CompletableDeferred<Unit>().also { it.complete(Unit) }
    private val tts = TextToSpeech(context) { ready.complete(it == TextToSpeech.SUCCESS) }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) = finished()
            @Deprecated("Deprecated in Java") override fun onError(id: String?) = finished()
            override fun onError(id: String?, code: Int) = finished()
        })
    }

    private fun finished() { if (pending.decrementAndGet() <= 0) { pending.set(0); drained.complete(Unit) } }

    override fun enqueue(text: String) {
        if (pending.getAndIncrement() == 0) drained = CompletableDeferred()
        // Utterances queue inside the engine; before init completes TextToSpeech buffers them.
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "u${System.nanoTime()}")
    }

    fun isReady(): Boolean = ready.isCompleted && ready.getCompleted()

    override suspend fun awaitDone() { ready.await(); drained.await() }

    /** Persona voice: rate/pitch multipliers (1.0 = normal). */
    fun setVoice(rate: Float, pitch: Float) { tts.setSpeechRate(rate); tts.setPitch(pitch) }

    override fun stop() { tts.stop(); pending.set(0); drained.complete(Unit) }
}
