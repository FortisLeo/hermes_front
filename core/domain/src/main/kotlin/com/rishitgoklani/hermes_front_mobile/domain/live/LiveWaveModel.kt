package com.rishitgoklani.hermes_front_mobile.domain.live

import kotlinx.coroutines.flow.StateFlow

/** A short message for the user about the conversation itself (why it stopped, that it is reconnecting). [id] makes a repeat of the same text a new notice. */
data class LiveNotice(val id: Long, val text: String)

/** What the on-screen wave needs from a live conversation: is one running, how loud is the user right now, and anything the user should be told. */
interface LiveActivity {
    val active: StateFlow<Boolean>
    val notice: StateFlow<LiveNotice?>
    /** 0..1 loudness of the microphone while it is open to the model; 0 while it is muted. */
    val micLevel: StateFlow<Float>
}

/** What the wave shows: nothing, the user speaking, or a quiet microphone. Only the microphone is ever drawn, never the speaker. */
enum class WaveMode { Hidden, User, Idle }

/** Pure logic behind the wave indicator (kept out of the composable so it can be tested). */
object WaveModel {
    /** Above this mic level the user counts as speaking; speech is about 0.1..0.6 after scaling, room noise stays well below. */
    const val SPEECH_THRESHOLD = 0.08f

    fun mode(active: Boolean, mic: Float): WaveMode = when {
        !active -> WaveMode.Hidden
        mic >= SPEECH_THRESHOLD -> WaveMode.User
        else -> WaveMode.Idle
    }

    /** Appends [level] to a scrolling histogram of at most [max] bars (oldest drops), clamped to 0..1. */
    fun push(bars: List<Float>, level: Float, max: Int): List<Float> = (bars + level.coerceIn(0f, 1f)).takeLast(max)

    /** A bar never vanishes (so the pill reads as a waveform) and quiet sound is lifted a little: sqrt curve between [minHeight] and 1. */
    fun barHeight(level: Float, minHeight: Float = 0.12f): Float = minHeight + (1f - minHeight) * kotlin.math.sqrt(level.coerceIn(0f, 1f))
}
