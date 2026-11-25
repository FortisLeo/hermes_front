package com.rishitgoklani.hermes_front_mobile.ui.avatar

import com.rishitgoklani.hermes_front_mobile.domain.avatar.AvatarAnimation
import com.rishitgoklani.hermes_front_mobile.domain.avatar.AvatarPose
import com.rishitgoklani.hermes_front_mobile.domain.avatar.AvatarState
import com.rishitgoklani.hermes_front_mobile.domain.avatar.Vec3
import com.rishitgoklani.hermes_front_mobile.domain.face.Effect
import com.rishitgoklani.hermes_front_mobile.engine.vrm.AnimationSpec
import com.rishitgoklani.hermes_front_mobile.engine.vrm.DriverInput
import com.rishitgoklani.hermes_front_mobile.engine.vrm.KeyframeSpec
import com.rishitgoklani.hermes_front_mobile.engine.vrm.PoseSpec

/** App state -> engine input. Pure, so it is unit-tested. */
object EngineInputMapper {
    private fun v(x: Vec3) = floatArrayOf(x.x, x.y, x.z)
    fun pose(p: AvatarPose) = PoseSpec(p.bones.mapValues { v(it.value) }, p.expressions, p.hips?.let(::v), p.offsets.mapValues { v(it.value) })
    fun animation(a: AvatarAnimation?) = a?.let { an -> AnimationSpec(an.keyframes.map { KeyframeSpec(it.tMs.toLong(), pose(it.pose)) }, an.loop) }
    /**
     * [effects] are the on-face effects to show right now and [activity] what the bot is doing (a `BotState` name such as "thinking");
     * both come from the screen, which combines the face state with the bot state.
     */
    fun input(s: AvatarState, effects: Set<Effect> = emptySet(), activity: String = "") = DriverInput(
        s.mouth, s.gazeX, s.gazeY, s.mood.name.lowercase(), s.blendMs, pose(s.pose), animation(s.animation), s.clip,
        moodIntensity = s.moodIntensity, reaction = s.reaction?.name?.lowercase().orEmpty(), reactionIntensity = s.reactionIntensity,
        activity = activity.lowercase(), effects = effects.mapTo(HashSet()) { it.name.lowercase() },
    )

    /** The effects the engine draws on the 3D face itself; the 2D overlay must skip these or they would show twice. */
    val engineEffects: Set<Effect> = setOf(Effect.Blush, Effect.Tears, Effect.SweatDrop, Effect.AngerMark)
}

