package com.rishitgoklani.hermes_front_mobile.data.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SttErrorsTest {
    // Android constants: ERROR_NO_MATCH=7, ERROR_SPEECH_TIMEOUT=6, ERROR_AUDIO=3, ERROR_NETWORK=2
    @Test fun noMatchAndTimeoutAreSilence() { assertTrue(SttErrors.isSilence(7)); assertTrue(SttErrors.isSilence(6)) }
    @Test fun audioIsNotSilence() = assertFalse(SttErrors.isSilence(3))
    @Test fun messagesAreReadable() {
        assertEquals("Audio recording error", SttErrors.message(3))
        assertEquals("Speech network error", SttErrors.message(2))
        assertEquals("Speech recognition error (99)", SttErrors.message(99))
    }
}
