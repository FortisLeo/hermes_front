package com.rishitgoklani.hermes_front_mobile.ui.face

import com.rishitgoklani.hermes_front_mobile.domain.BotState
import com.rishitgoklani.hermes_front_mobile.domain.face.Effect
import com.rishitgoklani.hermes_front_mobile.domain.face.ExpressionFallbacks
import com.rishitgoklani.hermes_front_mobile.domain.face.FaceState
import com.rishitgoklani.hermes_front_mobile.domain.face.Mood
import com.rishitgoklani.hermes_front_mobile.domain.face.Reaction

enum class EyeShape { Round, HappyArc, ClosedArc, Line }
enum class PupilShape { Circle, Heart, Star, Spiral }
enum class Motion { None, Nod, Shake, EyeRoll, LookAround, Bounce }

/** Pure description of how the default eyes look; the renderer only draws this. */
data class EyeStyle(
    val openness: Float, val pupilScale: Float, val lookX: Float, val lookY: Float, val lidSlant: Float,
    val colorHex: Long, val blinkEnabled: Boolean = true, val crossed: Boolean = false,
    val shape: EyeShape = EyeShape.Round, val pupil: PupilShape = PupilShape.Circle,
    val winkRight: Boolean = false, val asymmetry: Float = 0f, val motion: Motion = Motion.None,
    val effects: Set<Effect> = emptySet(), val sizeScale: Float = 1f, val blinkRateSec: Float = 4f, val roundness: Float = 0.4f,
)

object EyeStyles {
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)

    /** Base look for the robot's current activity (always applies). */
    fun forState(state: BotState): EyeStyle = when (state) {
        BotState.Idle -> EyeStyle(0.9f, 1f, 0f, 0f, 0f, 0xFF7FDBFF)
        BotState.Listening -> EyeStyle(1f, 1.1f, 0f, 0f, 0f, 0xFF7CFFB2)
        BotState.Thinking -> EyeStyle(0.75f, 0.9f, 0.5f, -0.6f, 0.2f, 0xFFFFD166)
        BotState.Speaking -> EyeStyle(0.95f, 1f, 0f, 0.05f, 0f, 0xFF7FDBFF, motion = Motion.Bounce)
        BotState.Working -> EyeStyle(0.6f, 0.8f, 0f, 0.4f, 0.4f, 0xFFB388FF)
        BotState.Error -> EyeStyle(1f, 1f, 0f, 0f, 0f, 0xFFFF5C5C, blinkEnabled = false, crossed = true)
    }

    private data class MoodLook(val openness: Float, val pupil: Float, val lookY: Float, val slant: Float, val shape: EyeShape = EyeShape.Round,
                                val pupilShape: PupilShape = PupilShape.Circle, val asym: Float = 0f, val tint: Long? = null)

    private fun moodLook(m: Mood): MoodLook = when (m) {
        Mood.Neutral -> MoodLook(0.9f, 1f, 0f, 0f)
        Mood.Happy -> MoodLook(0.75f, 1f, 0f, 0f, EyeShape.HappyArc)
        Mood.Sad -> MoodLook(0.75f, 1f, 0.5f, -0.7f)
        Mood.Angry -> MoodLook(0.7f, 0.7f, 0f, 0.8f, tint = 0xFFFF6B6B)
        Mood.Surprised -> MoodLook(1f, 0.6f, 0f, 0f)
        Mood.Worried -> MoodLook(0.85f, 0.9f, 0.2f, -0.4f)
        Mood.Scared -> MoodLook(1f, 0.5f, 0f, -0.2f)
        Mood.Confused -> MoodLook(0.85f, 1f, -0.1f, 0f, asym = 0.35f)
        Mood.Bored -> MoodLook(0.5f, 0.9f, 0.1f, 0f)
        Mood.Sleepy -> MoodLook(0.3f, 0.9f, 0.2f, 0f)
        Mood.Shy -> MoodLook(0.8f, 1f, 0.6f, 0f)
        Mood.Proud -> MoodLook(0.85f, 1f, -0.25f, 0f)
        Mood.Love -> MoodLook(0.9f, 1f, 0f, 0f, pupilShape = PupilShape.Heart)
        Mood.Focused -> MoodLook(0.6f, 0.9f, 0f, 0.3f)
        Mood.Disgusted -> MoodLook(0.5f, 0.8f, 0.1f, 0.5f)
    }

    /** Combine bot state (activity) with the face mood/reaction and persona/user tweaks. */
    fun compute(bot: BotState, face: FaceState): EyeStyle {
        var s = forState(bot)
        if (!s.crossed) {
            val ml = moodLook(face.mood); val k = face.moodIntensity
            s = s.copy(
                openness = lerp(s.openness, if (bot == BotState.Listening) maxOf(ml.openness, 0.95f) else ml.openness, k), pupilScale = lerp(s.pupilScale, ml.pupil, k),
                lookY = if (bot == BotState.Thinking) s.lookY else lerp(s.lookY, ml.lookY, k), lidSlant = if (bot == BotState.Thinking) s.lidSlant else lerp(s.lidSlant, ml.slant, k),
                shape = if (k >= 0.5f) ml.shape else s.shape, pupil = if (k >= 0.5f) ml.pupilShape else s.pupil, asymmetry = ml.asym * k,
                colorHex = ml.tint ?: s.colorHex, effects = if (k >= 0.4f) ExpressionFallbacks.forMood(face.mood) else emptySet(),
            )
            face.reaction?.let { s = applyReaction(s, it, face.reactionIntensity) }
            face.lookX?.let { s = s.copy(lookX = it) }; face.lookY?.let { s = s.copy(lookY = it) }
        }
        val t = face.tweaks
        return s.copy(
            colorHex = t.colorHex ?: s.colorHex, openness = (t.openness?.let { o -> s.openness * o / 0.9f } ?: s.openness).coerceIn(0.05f, 1.2f),
            sizeScale = t.sizeScale, blinkRateSec = t.blinkRateSec ?: s.blinkRateSec, pupilScale = t.pupilScale ?: s.pupilScale, roundness = t.roundness,
        )
    }

    private fun applyReaction(base: EyeStyle, r: Reaction, intensity: Float): EyeStyle {
        val look = ExpressionFallbacks.forReaction(r)
        var s = base.copy(effects = base.effects + look.effects)
        val ml = moodLook(look.mood)
        if (r != Reaction.Wink && r != Reaction.Nod && r != Reaction.ShakeNo && r != Reaction.Blink && r != Reaction.Glance && r != Reaction.LookAround) {
            s = s.copy(openness = lerp(s.openness, ml.openness, intensity), lookY = lerp(s.lookY, ml.lookY, intensity), lidSlant = lerp(s.lidSlant, ml.slant, intensity),
                shape = if (look.closedArcs) EyeShape.ClosedArc else ml.shape)
        }
        return when (r) {
            Reaction.Wink -> s.copy(winkRight = true, shape = if (s.shape == EyeShape.Round) EyeShape.Round else s.shape)
            Reaction.Nod -> s.copy(motion = Motion.Nod); Reaction.ShakeNo -> s.copy(motion = Motion.Shake)
            Reaction.Blink -> s.copy(openness = 0.05f); Reaction.EyeRoll -> s.copy(motion = Motion.EyeRoll)
            Reaction.LookAround -> s.copy(motion = Motion.LookAround); Reaction.Glance -> s.copy(lookX = 0.7f, lookY = 0f)
            Reaction.HeartEyes -> s.copy(pupil = PupilShape.Heart); Reaction.StarEyes -> s.copy(pupil = PupilShape.Star)
            Reaction.Dizzy -> s.copy(pupil = PupilShape.Spiral); Reaction.Laugh, Reaction.Celebrate, Reaction.Giggle -> s.copy(motion = Motion.Bounce)
            Reaction.Yawn -> s.copy(openness = 0.2f); Reaction.Startle -> s.copy(openness = 1.1f, pupilScale = 0.5f)
            else -> s
        }
    }
}
