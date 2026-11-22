package com.rishitgoklani.hermes_front_mobile.data.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.rishitgoklani.hermes_front_mobile.domain.SpeechSynthesizer
import com.rishitgoklani.hermes_front_mobile.domain.face.MouthShaper
import com.rishitgoklani.hermes_front_mobile.domain.face.MouthSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicInteger

/** Adapter over android.speech.tts.TextToSpeech using QUEUE_ADD so sentences play back to back. */
class AndroidSpeechSynthesizer(context: Context) : SpeechSynthesizer, MouthSource {
    private val mouth = MutableStateFlow(0f)
    override val openness: StateFlow<Float> = mouth
    private val texts = ConcurrentHashMap<String, String>()
    private val ready = CompletableDeferred<Boolean>()
    private val pending = AtomicInteger(0)
    @Volatile private var drained = CompletableDeferred<Unit>().also { it.complete(Unit) }
    private val tts = TextToSpeech(context) { ready.complete(it == TextToSpeech.SUCCESS) }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onRangeStart(id: String?, start: Int, end: Int, frame: Int) {
                val t = texts[id] ?: return
                mouth.value = MouthShaper.peak(t.substring(start.coerceIn(0, t.length), end.coerceIn(0, t.length)))
            }
            override fun onDone(id: String?) { texts.remove(id); finished() }
            @Deprecated("Deprecated in Java") override fun onError(id: String?) { texts.remove(id); finished() }
            override fun onError(id: String?, code: Int) { texts.remove(id); finished() }
        })
    }

    private fun finished() { if (pending.decrementAndGet() <= 0) { pending.set(0); mouth.value = 0f; drained.complete(Unit) } }

    override fun enqueue(text: String) {
        if (pending.getAndIncrement() == 0) drained = CompletableDeferred()
        // Utterances queue inside the engine; before init completes TextToSpeech buffers them.
        val id = "u${System.nanoTime()}"; texts[id] = text
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, id)
    }

    fun isReady(): Boolean = ready.isCompleted && ready.getCompleted()

    override suspend fun awaitDone() { ready.await(); drained.await() }

    /** Persona voice: rate/pitch multipliers (1.0 = normal). */
    fun setVoice(rate: Float, pitch: Float) { tts.setSpeechRate(rate); tts.setPitch(pitch) }

    override fun stop() { tts.stop(); texts.clear(); pending.set(0); mouth.value = 0f; drained.complete(Unit) }
}
