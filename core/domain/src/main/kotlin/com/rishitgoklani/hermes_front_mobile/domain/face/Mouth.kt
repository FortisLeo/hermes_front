package com.rishitgoklani.hermes_front_mobile.domain.face

import kotlinx.coroutines.flow.StateFlow

/** Port: how open the mouth is (0 closed .. 1 wide) while the bot speaks. Implemented by the TTS adapter. */
interface MouthSource {
    val openness: StateFlow<Float>
}

/** Pure text-to-mouth mapping used for lip sync; TTS exposes no audio level, so word progress drives it. */
object MouthShaper {
    private fun weight(c: Char): Float = when (c.lowercaseChar()) {
        'a' -> 1f; 'o' -> 0.85f; 'e' -> 0.65f; 'u' -> 0.55f; 'i' -> 0.45f; 'y' -> 0.3f
        'm', 'b', 'p' -> 0f
        else -> 0.2f
    }

    /** Peak openness for a spoken word: its widest vowel, softened for long words. Blank gives 0. */
    fun peak(word: String): Float {
        val letters = word.filter { it.isLetter() }
        if (letters.isEmpty()) return 0f
        val widest = letters.maxOf { weight(it) }
        return (widest * (if (letters.length > 8) 0.85f else 1f)).coerceIn(0f, 1f)
    }
}
