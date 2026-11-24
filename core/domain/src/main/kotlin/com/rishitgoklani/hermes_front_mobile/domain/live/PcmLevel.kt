package com.rishitgoklani.hermes_front_mobile.domain.live

import kotlin.math.sqrt

/** Loudness of 16-bit little-endian PCM, used to open the avatar's mouth in time with the model's voice. */
object PcmLevel {
    /** Root-mean-square of [length] bytes starting at [offset], 0..1 of full scale. Empty or odd-sized input is handled. */
    fun rms(pcm: ByteArray, offset: Int = 0, length: Int = pcm.size - offset): Float {
        val samples = length / 2
        if (samples <= 0) return 0f
        var sum = 0.0
        for (i in 0 until samples) {
            val lo = pcm[offset + 2 * i].toInt() and 0xff
            val hi = pcm[offset + 2 * i + 1].toInt()
            val v = ((hi shl 8) or lo).toShort().toInt() / 32768.0
            sum += v * v
        }
        return sqrt(sum / samples).toFloat()
    }

    /** Speech sits around 0.05..0.3 RMS, so scale it to fill the 0..1 range of the mouth. */
    fun mouthOpenness(rms: Float, gain: Float = 4f): Float = (rms * gain).coerceIn(0f, 1f)
}

/** The prebuilt voices the model offers (Live accepts the same names as the speech models). */
object LiveVoices {
    val ALL = listOf("Zephyr", "Puck", "Charon", "Kore", "Fenrir", "Leda", "Orus", "Aoede", "Callirrhoe", "Autonoe", "Enceladus", "Iapetus", "Umbriel", "Algieba", "Despina",
        "Erinome", "Algenib", "Rasalgethi", "Laomedeia", "Achernar", "Alnilam", "Schedar", "Gacrux", "Pulcherrima", "Achird", "Zubenelgenubi", "Vindemiatrix", "Sadachbia", "Sadaltager", "Sulafat")
}
