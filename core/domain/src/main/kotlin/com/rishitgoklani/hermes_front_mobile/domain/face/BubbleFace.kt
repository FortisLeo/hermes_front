package com.rishitgoklani.hermes_front_mobile.domain.face

/** Tiny face for the floating bubble (#52): how open the eyes are and whether they are drawn as happy arcs. */
data class BubbleFace(val openness: Float, val arcs: Boolean, val colorHex: Long = 0xFF7FDBFF) {
    companion object {
        fun from(state: FaceState): BubbleFace = when (state.mood) {
            Mood.Happy, Mood.Love, Mood.Proud -> BubbleFace(1f, true)
            Mood.Sleepy, Mood.Bored -> BubbleFace(0.25f, false)
            Mood.Sad, Mood.Worried -> BubbleFace(0.7f, false, 0xFF9BB8FF)
            Mood.Angry, Mood.Disgusted -> BubbleFace(0.55f, false, 0xFFFF8A80)
            Mood.Surprised, Mood.Scared -> BubbleFace(1f, false, 0xFFFFF59D)
            else -> BubbleFace(0.9f, false)
        }
    }
}
