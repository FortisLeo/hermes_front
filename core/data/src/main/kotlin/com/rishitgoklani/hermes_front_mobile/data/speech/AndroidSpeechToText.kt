package com.rishitgoklani.hermes_front_mobile.data.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.rishitgoklani.hermes_front_mobile.domain.SpeechToText
import com.rishitgoklani.hermes_front_mobile.domain.SttEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.IOException

/** Adapter over android.speech.SpeechRecognizer (must be driven from the main thread). */
class AndroidSpeechToText(private val context: Context) : SpeechToText {
    override fun listen(): Flow<SttEvent> = callbackFlow {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            close(IOException("No speech recognition service on this device")); return@callbackFlow
        }
        Log.i("DeskbotStt", "listen start")
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer.setRecognitionListener(object : RecognitionListener {
            private fun Bundle?.best() = this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            override fun onPartialResults(b: Bundle?) { b.best().takeIf { it.isNotEmpty() }?.let { trySend(SttEvent.Partial(it)) } }
            override fun onResults(b: Bundle?) {
                Log.i("DeskbotStt", "onResults ${b.best()}"); trySend(SttEvent.Final(b.best())); close() }
            override fun onError(code: Int) {
                Log.i("DeskbotStt", "onError $code")
                if (SttErrors.isSilence(code)) { trySend(SttEvent.Final("")); close() }
                else close(IOException(SttErrors.message(code)))
            }
            override fun onReadyForSpeech(p: Bundle?) { Log.i("DeskbotStt", "ready") }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, b: Bundle?) {}
        })
        recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        })
        awaitClose { recognizer.destroy() }
    }.flowOn(Dispatchers.Main)
}
