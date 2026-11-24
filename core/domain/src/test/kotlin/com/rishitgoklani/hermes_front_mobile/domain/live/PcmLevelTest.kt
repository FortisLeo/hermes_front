package com.rishitgoklani.hermes_front_mobile.domain.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmLevelTest {
    private fun pcm(vararg samples: Int) = ByteArray(samples.size * 2).also { b -> samples.forEachIndexed { i, s -> b[2 * i] = (s and 0xff).toByte(); b[2 * i + 1] = (s shr 8).toByte() } }

    @Test fun silenceIsZero() = assertEquals(0f, PcmLevel.rms(pcm(0, 0, 0, 0)), 0f)
    @Test fun emptyAndSingleByteInputAreZero() { assertEquals(0f, PcmLevel.rms(ByteArray(0)), 0f); assertEquals(0f, PcmLevel.rms(ByteArray(1)), 0f) }
    @Test fun fullScaleIsAboutOne() = assertEquals(1f, PcmLevel.rms(pcm(32767, -32768, 32767, -32768)), 0.01f)
    @Test fun negativeSamplesCountTheSameAsPositive() = assertEquals(PcmLevel.rms(pcm(1000, 1000)), PcmLevel.rms(pcm(-1000, -1000)), 0.0001f)
    @Test fun halfScaleIsAboutHalf() = assertEquals(0.5f, PcmLevel.rms(pcm(16384, 16384)), 0.01f)
    @Test fun offsetAndLengthSelectAWindow() = assertEquals(0f, PcmLevel.rms(pcm(30000, 0, 0), offset = 2, length = 4), 0f)
    @Test fun mouthScalesSpeechUpAndClamps() { assertEquals(0.4f, PcmLevel.mouthOpenness(0.1f), 0.001f); assertEquals(1f, PcmLevel.mouthOpenness(0.9f), 0f) }
    @Test fun thereAreThirtyUniqueVoices() { assertEquals(30, LiveVoices.ALL.size); assertTrue(LiveVoices.ALL.toSet().size == 30 && "Kore" in LiveVoices.ALL) }
}
