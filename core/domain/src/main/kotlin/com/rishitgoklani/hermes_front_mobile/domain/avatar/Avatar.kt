package com.rishitgoklani.hermes_front_mobile.domain.avatar

import com.rishitgoklani.hermes_front_mobile.domain.face.Mood
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Rotation in radians (euler XYZ) in the VRM's normalized humanoid space, where zero is the T-pose. */
data class Vec3(val x: Float, val y: Float, val z: Float)

/** What the agent wants the body to look like: rotations of any bone or node of the model (humanoid or not), translation offsets of any node, any expression weights, and an optional hips offset (metres). */
data class AvatarPose(val bones: Map<String, Vec3> = emptyMap(), val expressions: Map<String, Float> = emptyMap(), val hips: Vec3? = null, val offsets: Map<String, Vec3> = emptyMap()) {
    val isEmpty get() = bones.isEmpty() && expressions.isEmpty() && hips == null && offsets.isEmpty()
}

/** A moment in an agent-authored animation. Bones missing from a keyframe keep interpolating between the keyframes that do set them. */
data class AvatarKeyframe(val tMs: Int, val pose: AvatarPose)
data class AvatarAnimation(val keyframes: List<AvatarKeyframe>, val loop: Boolean)

data class AvatarState(
    val enabled: Boolean = false, val mood: Mood = Mood.Neutral, val mouth: Float = 0f, val gazeX: Float = 0f, val gazeY: Float = 0f,
    val pose: AvatarPose = AvatarPose(), val blendMs: Int = 250, val animation: AvatarAnimation? = null, val clip: String = "",
)

/** Holds what the avatar should be doing; the viewer observes it. Face/mouth sources and the agent's tools feed it. */
class AvatarController {
    private val _state = MutableStateFlow(AvatarState())
    val state: StateFlow<AvatarState> = _state
    private val _nodes = MutableStateFlow<List<String>>(emptyList())
    /** Every bone/node name the loaded model has, reported by the viewer once the model is ready. */
    val nodes: StateFlow<List<String>> = _nodes
    fun setNodes(names: List<String>) { _nodes.value = names }
    fun setEnabled(v: Boolean) = _state.update { it.copy(enabled = v) }
    fun setMood(m: Mood) = _state.update { it.copy(mood = m) }
    fun setMouth(v: Float) = _state.update { it.copy(mouth = v) }
    fun look(x: Float?, y: Float?) = _state.update { it.copy(gazeX = x ?: 0f, gazeY = y ?: 0f) }
    fun setPose(p: AvatarPose, blendMs: Int) = _state.update { it.copy(pose = p, blendMs = blendMs, animation = null, clip = "") }
    fun animate(a: AvatarAnimation) = _state.update { it.copy(animation = a, clip = "") }
    fun reset() = _state.update { it.copy(pose = AvatarPose(), animation = null, clip = "") }
    fun playClip(name: String) = _state.update { it.copy(clip = name, animation = null) }
    fun stopClip() = _state.update { it.copy(clip = "") }
}
