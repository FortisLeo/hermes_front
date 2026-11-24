package com.rishitgoklani.hermes_front_mobile.ui.avatar

import com.rishitgoklani.hermes_front_mobile.domain.avatar.AvatarAnimation
import com.rishitgoklani.hermes_front_mobile.domain.avatar.AvatarPose
import com.rishitgoklani.hermes_front_mobile.domain.avatar.AvatarState
import com.rishitgoklani.hermes_front_mobile.domain.avatar.Vec3
import com.rishitgoklani.hermes_front_mobile.engine.vrm.AnimationSpec
import com.rishitgoklani.hermes_front_mobile.engine.vrm.DriverInput
import com.rishitgoklani.hermes_front_mobile.engine.vrm.KeyframeSpec
import com.rishitgoklani.hermes_front_mobile.engine.vrm.PoseSpec

/** App state -> engine input. Pure, so it is unit-tested. */
object EngineInputMapper {
    private fun v(x: Vec3) = floatArrayOf(x.x, x.y, x.z)
    fun pose(p: AvatarPose) = PoseSpec(p.bones.mapValues { v(it.value) }, p.expressions, p.hips?.let(::v), p.offsets.mapValues { v(it.value) })
    fun animation(a: AvatarAnimation?) = a?.let { an -> AnimationSpec(an.keyframes.map { KeyframeSpec(it.tMs.toLong(), pose(it.pose)) }, an.loop) }
    fun input(s: AvatarState) = DriverInput(s.mouth, s.gazeX, s.gazeY, s.mood.name.lowercase(), s.blendMs, pose(s.pose), animation(s.animation), s.clip)
}

