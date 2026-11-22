package com.rishitgoklani.hermes_front_mobile.domain.avatar

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.face.Mood
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Body animations the 3D avatar can play. Procedural, so any humanoid VRM works without clips. */
enum class AvatarAction { Idle, Wave, Dance, Cheer, Bow, Nod }

data class AvatarState(val mood: Mood = Mood.Neutral, val mouth: Float = 0f, val gazeX: Float = 0f, val gazeY: Float = 0f, val action: AvatarAction = AvatarAction.Idle)

/** Body language per mood: head tilt/nod (radians), arm raise (0 down .. 1 up), slouch (0..1), bounce speed. */
data class MoodPose(val headTilt: Float, val headNod: Float, val armRaise: Float, val slouch: Float, val bounce: Float)

object AvatarMapper {
    fun pose(mood: Mood): MoodPose = when (mood) {
        Mood.Happy, Mood.Proud, Mood.Love -> MoodPose(0.08f, -0.05f, 0.25f, 0f, 1.0f)
        Mood.Sad, Mood.Bored, Mood.Sleepy -> MoodPose(0.05f, 0.25f, 0f, 0.7f, 0f)
        Mood.Angry, Mood.Disgusted -> MoodPose(0f, 0.12f, 0.15f, 0f, 0.3f)
        Mood.Surprised, Mood.Scared -> MoodPose(0f, -0.12f, 0.5f, 0f, 0.6f)
        Mood.Confused, Mood.Worried -> MoodPose(0.2f, 0.05f, 0.05f, 0.2f, 0f)
        Mood.Shy -> MoodPose(0.15f, 0.2f, 0f, 0.3f, 0f)
        else -> MoodPose(0f, 0f, 0f, 0f, 0.2f)
    }

    /** One JSON message for the viewer page; mouth is clamped 0..1 and gaze -1..1. */
    fun toJson(s: AvatarState): String {
        val p = pose(s.mood)
        fun f(v: Float) = "%.3f".format(java.util.Locale.ROOT, v)
        return """{"mood":"${s.mood.name.lowercase()}","mouth":${f(s.mouth.coerceIn(0f, 1f))},"gx":${f(s.gazeX.coerceIn(-1f, 1f))},"gy":${f(s.gazeY.coerceIn(-1f, 1f))},"action":"${s.action.name.lowercase()}","tilt":${f(p.headTilt)},"nod":${f(p.headNod)},"arms":${f(p.armRaise)},"slouch":${f(p.slouch)},"bounce":${f(p.bounce)}}"""
    }
}

/** Holds what the avatar should be doing; the viewer observes it. Face/mouth sources feed it from the app wiring. */
class AvatarController {
    private val _state = MutableStateFlow(AvatarState())
    val state: StateFlow<AvatarState> = _state
    fun setMood(m: Mood) = _state.update { it.copy(mood = m) }
    fun setMouth(v: Float) = _state.update { it.copy(mouth = v) }
    fun look(x: Float?, y: Float?) = _state.update { it.copy(gazeX = x ?: 0f, gazeY = y ?: 0f) }
    fun act(a: AvatarAction) = _state.update { it.copy(action = a) }
}

fun avatarCapability(avatar: AvatarController) = Capability(
    "avatar_action", "Make the 3D avatar do a body action: wave, dance, cheer, bow, nod or idle (stops the action).",
    """{"type":"object","properties":{"action":{"type":"string","enum":["idle","wave","dance","cheer","bow","nod"]}},"required":["action"]}""",
) { a ->
    val name = a["action"]?.jsonPrimitive?.contentOrNull ?: return@Capability ActionResult.failed("action required")
    val act = AvatarAction.entries.firstOrNull { it.name.equals(name, true) } ?: return@Capability ActionResult.failed("unknown action '$name'")
    avatar.act(act); ActionResult.completed(if (act == AvatarAction.Idle) "Avatar idle" else "Avatar ${act.name.lowercase()}")
}
