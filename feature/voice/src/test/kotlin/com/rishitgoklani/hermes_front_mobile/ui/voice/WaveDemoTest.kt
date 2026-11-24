package com.rishitgoklani.hermes_front_mobile.ui.voice

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WaveDemoTest {
    @Test fun theDemoAlternatesBetweenTheUserAndTheBotAndStaysInRange() = runTest {
        val d = WaveDemo(backgroundScope); var userSeen = false; var botSeen = false; var overlap = false
        repeat(160) {
            advanceTimeBy(50); runCurrent()
            val m = d.mic.value; val b = d.bot.value
            assertTrue(m in 0f..1f && b in 0f..1f)
            if (m > 0.1f) userSeen = true; if (b > 0.05f) botSeen = true; if (m > 0f && b > 0f) overlap = true
        }
        assertTrue(userSeen && botSeen); assertEquals(false, overlap); assertTrue(d.active.value)
    }
}
