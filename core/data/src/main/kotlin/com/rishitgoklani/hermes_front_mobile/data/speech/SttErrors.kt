package com.rishitgoklani.hermes_front_mobile.data.speech

import android.speech.SpeechRecognizer

/** Maps SpeechRecognizer error codes to short messages; NO_MATCH/timeout are "silence", not failures. */
object SttErrors {
    fun isSilence(code: Int) = code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
    fun message(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Recognizer client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission missing"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech network error"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
        SpeechRecognizer.ERROR_SERVER -> "Speech server error"
        else -> "Speech recognition error ($code)"
    }
}
