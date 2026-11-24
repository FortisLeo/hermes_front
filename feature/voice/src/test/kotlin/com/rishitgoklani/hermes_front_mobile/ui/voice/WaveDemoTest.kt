package com.rishitgoklani.hermes_front_mobile.ui.voice

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WaveDemoTest {
    @Test fun theDemoAlternatesBetweenSpeechAndSilenceAndStaysInRange() = runTest {
        val d = WaveDemo(backgroundScope); var loud = false; var quiet = false
        repeat(160) {
            advanceTimeBy(50); runCurrent()
            val m = d.mic.value
            assertTrue(m in 0f..1f)
            if (m > 0.1f) loud = true; if (m == 0f) quiet = true
        }
        assertTrue(loud && quiet); assertTrue(d.active.value)
    }
}
