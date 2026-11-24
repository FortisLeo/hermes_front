package com.rishitgoklani.hermes_front_mobile.domain.live

import kotlinx.coroutines.flow.StateFlow

/** What the on-screen wave needs from a live conversation: is one running, and how loud is the user right now. */
interface LiveActivity {
    val active: StateFlow<Boolean>
    /** 0..1 loudness of the microphone while it is open to the model; 0 while it is muted. */
    val micLevel: StateFlow<Float>
}

/** Who the wave is showing. */
enum class WaveMode { Hidden, User, Bot, Thinking, Idle }

/** Pure logic behind the wave indicator (kept out of the composable so it can be tested). */
object WaveModel {
    /** Above this mic level the user counts as speaking; speech is about 0.1..0.6 after scaling, room noise stays well below. */
    const val SPEECH_THRESHOLD = 0.08f
    const val BOT_THRESHOLD = 0.04f

    fun mode(active: Boolean, mic: Float, bot: Float, working: Boolean): WaveMode = when {
        !active -> WaveMode.Hidden
        bot >= BOT_THRESHOLD -> WaveMode.Bot
        mic >= SPEECH_THRESHOLD -> WaveMode.User
        working -> WaveMode.Thinking
        else -> WaveMode.Idle
    }

    /** Appends [level] to a scrolling histogram of at most [max] bars (oldest drops), clamped to 0..1. */
    fun push(bars: List<Float>, level: Float, max: Int): List<Float> = (bars + level.coerceIn(0f, 1f)).takeLast(max)

    /** A bar never vanishes (so the pill reads as a waveform) and quiet sound is lifted a little: sqrt curve between [minHeight] and 1. */
    fun barHeight(level: Float, minHeight: Float = 0.12f): Float = minHeight + (1f - minHeight) * kotlin.math.sqrt(level.coerceIn(0f, 1f))
}
