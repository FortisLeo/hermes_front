package com.rishitgoklani.hermes_front_mobile.data.capabilities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundMathTest {
    @Test fun silenceIsFloor() { assertEquals(-90.0, SoundMath.dbfs(ShortArray(100), 100), 0.0); assertEquals(-90.0, SoundMath.dbfs(ShortArray(0), 0), 0.0) }
    @Test fun fullScaleIsZeroDb() = assertEquals(0.0, SoundMath.dbfs(ShortArray(100) { Short.MAX_VALUE }, 100), 0.01)
    @Test fun halfAmplitudeIsAboutMinusSix() = assertEquals(-6.0, SoundMath.dbfs(ShortArray(100) { 16384 }, 100), 0.1)
    @Test fun labels() { assertEquals("loud", SoundMath.label(-10.0)); assertEquals("moderate", SoundMath.label(-30.0)); assertEquals("quiet", SoundMath.label(-50.0)); assertEquals("silent", SoundMath.label(-80.0)) }
    @Test fun louderSamplesGiveHigherDb() = assertTrue(SoundMath.dbfs(ShortArray(50) { 8000 }, 50) > SoundMath.dbfs(ShortArray(50) { 800 }, 50))
}
