package com.rishitgoklani.hermes_front_mobile.engine.vrmgl

import org.junit.Assert.assertEquals
import org.junit.Test

class QualityPresetsTest {
    private val gb = 1024L * 1024 * 1024

    @Test fun lowTurnsOffOutlinesAndPhysicsAndUsesSixteenBitTextures() =
        assertEquals(EngineConfig(outlines = false, springs = false, rgb565 = true), QualityPresets.resolve(QualityLevel.Low, false, 8 * gb))

    @Test fun highTurnsEverythingOnEvenOnASmallPhone() =
        assertEquals(EngineConfig(outlines = true, springs = true, rgb565 = false, compressTextures = false), QualityPresets.resolve(QualityLevel.High, true, 2 * gb))

    @Test fun autoOnA3GbPhoneKeepsOutlinesButUsesSixteenBitTextures() =
        assertEquals(EngineConfig(outlines = true, springs = true, rgb565 = true), QualityPresets.resolve(QualityLevel.Auto, false, 3 * gb + gb / 2))

    @Test fun autoOnABigPhoneUsesFullTextures() =
        assertEquals(EngineConfig(outlines = true, springs = true, rgb565 = false), QualityPresets.resolve(QualityLevel.Auto, false, 8 * gb))

    @Test fun autoOnALowRamDeviceDropsOutlines() =
        assertEquals(EngineConfig(outlines = false, springs = true, rgb565 = true), QualityPresets.resolve(QualityLevel.Auto, true, 2 * gb))

    @Test fun onlyHighQualitySkipsTheCompressedTextureCache() {
        val gb = 1024L * 1024 * 1024
        assertEquals(false, QualityPresets.resolve(QualityLevel.High, false, 8 * gb).compressTextures)
        assertEquals(true, QualityPresets.resolve(QualityLevel.Auto, false, 8 * gb).compressTextures)
        assertEquals(true, QualityPresets.resolve(QualityLevel.Low, true, 2 * gb).compressTextures)
    }
}
