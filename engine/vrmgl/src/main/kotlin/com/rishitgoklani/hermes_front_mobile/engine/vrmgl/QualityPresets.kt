package com.rishitgoklani.hermes_front_mobile.engine.vrmgl

/** What the user picked in Settings > Display quality. */
enum class QualityLevel { Auto, Low, High }

/** Pure mapping from the user's choice and the device to renderer switches, so it can be unit-tested without a GPU. */
object QualityPresets {
    private const val FOUR_GB = 4L * 1024 * 1024 * 1024

    fun resolve(level: QualityLevel, lowRamDevice: Boolean, totalMemBytes: Long): EngineConfig = when (level) {
        // Battery and heat first: no outline pass, no hair/cloth physics, 16-bit textures for opaque images.
        QualityLevel.Low -> EngineConfig(outlines = false, springs = false, rgb565 = true)
        // Everything on, full 8-bit textures (memory budgeting still applies, so a big model is shrunk before it is refused).
        QualityLevel.High -> EngineConfig(outlines = true, springs = true, rgb565 = false)
        QualityLevel.Auto -> EngineConfig(outlines = !lowRamDevice, springs = true, rgb565 = lowRamDevice || totalMemBytes < FOUR_GB)
    }
}
