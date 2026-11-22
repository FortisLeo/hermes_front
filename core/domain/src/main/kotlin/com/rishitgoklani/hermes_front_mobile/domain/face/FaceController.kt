package com.rishitgoklani.hermes_front_mobile.domain.face

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** User/persona adjustable eye parameters; null = use the state default. */
data class EyeTweaks(
    val colorHex: Long? = null, val openness: Float? = null, val sizeScale: Float = 1f,
    val blinkRateSec: Float? = null, val pupilScale: Float? = null, val roundness: Float = 0.4f,
)

data class FaceState(
    val mood: Mood = Mood.Neutral, val moodIntensity: Float = 1f,
    val reaction: Reaction? = null, val reactionIntensity: Float = 1f,
    val lookX: Float? = null, val lookY: Float? = null,
    val tweaks: EyeTweaks = EyeTweaks(),
)

/** Single owner of face state. Reactions auto-clear after [reactionMs]; moods persist until changed. */
class FaceController(private val scope: CoroutineScope, private val reactionMs: Long = 2000) {
    private val _state = MutableStateFlow(FaceState())
    val state: StateFlow<FaceState> = _state
    private var reactionJob: Job? = null

    fun setMood(mood: Mood, intensity: Float = 1f) = _state.update { it.copy(mood = mood, moodIntensity = intensity.coerceIn(0f, 1f)) }

    fun react(reaction: Reaction, intensity: Float = 1f, durationMs: Long = reactionMs) {
        reactionJob?.cancel()
        _state.update { it.copy(reaction = reaction, reactionIntensity = intensity.coerceIn(0f, 1f)) }
        reactionJob = scope.launch { delay(durationMs); _state.update { it.copy(reaction = null) } }
    }

    fun apply(expr: Expr) = when (expr) {
        is Expr.MoodExpr -> setMood(expr.mood, expr.intensity)
        is Expr.ReactionExpr -> react(expr.reaction, expr.intensity)
    }

    fun look(x: Float?, y: Float?) = _state.update { it.copy(lookX = x?.coerceIn(-1f, 1f), lookY = y?.coerceIn(-1f, 1f)) }

    fun tweak(change: (EyeTweaks) -> EyeTweaks) = _state.update { it.copy(tweaks = change(it.tweaks)) }
    fun resetTweaks() = _state.update { it.copy(tweaks = EyeTweaks()) }
}
