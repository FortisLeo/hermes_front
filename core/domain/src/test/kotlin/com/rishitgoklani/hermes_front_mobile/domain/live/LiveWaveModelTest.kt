package com.rishitgoklani.hermes_front_mobile.domain.live

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveWaveModelTest {
    private fun mode(active: Boolean = true, mic: Float = 0f) = WaveModel.mode(active, mic)

    @Test fun hiddenWhenNoConversationIsRunning() = assertEquals(WaveMode.Hidden, mode(active = false, mic = 1f))
    @Test fun userWhenTheMicIsLoud() = assertEquals(WaveMode.User, mode(mic = 0.3f))
    @Test fun roomNoiseDoesNotCountAsSpeech() = assertEquals(WaveMode.Idle, mode(mic = 0.03f))
    @Test fun quietStaysIdle() = assertEquals(WaveMode.Idle, mode())
    @Test fun theThresholdItselfCountsAsSpeech() = assertEquals(WaveMode.User, mode(mic = WaveModel.SPEECH_THRESHOLD))

    @Test fun historyScrollsAndDropsTheOldest() = assertEquals(listOf(0.2f, 0.3f, 0.4f), WaveModel.push(listOf(0.1f, 0.2f, 0.3f), 0.4f, 3))
    @Test fun historyClampsLevels() = assertEquals(listOf(1f, 0f), WaveModel.push(listOf(1f), -2f, 5).let { listOf(WaveModel.push(emptyList(), 7f, 5).single(), it.last()) })
    @Test fun historyStartsEmptyAndGrowsToTheLimit() { var b = emptyList<Float>(); repeat(10) { b = WaveModel.push(b, 0.5f, 4) }; assertEquals(4, b.size) }

    @Test fun silenceStillDrawsAThinBar() = assertEquals(0.12f, WaveModel.barHeight(0f), 0.0001f)
    @Test fun fullLevelFillsTheHeight() = assertEquals(1f, WaveModel.barHeight(1f), 0.0001f)
    @Test fun quietSoundIsLiftedAboveLinear() = assert(WaveModel.barHeight(0.25f) > 0.25f)
}
