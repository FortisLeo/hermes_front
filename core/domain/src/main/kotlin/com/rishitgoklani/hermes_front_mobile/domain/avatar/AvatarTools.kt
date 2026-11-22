package com.rishitgoklani.hermes_front_mobile.domain.avatar

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.floatOrNull

private const val AXES = "Rotations are euler [x, y, z] radians in the VRM's normalized humanoid space; all zero is the T-pose (arms straight out). " +
    "The avatar rests with arms down (leftUpperArm z=+1.25, rightUpperArm z=-1.25). Other axes: experiment and look at the screen. "

/** avatar_mode: show the 3D avatar or the eyes as the face. */
fun avatarModeCapability(avatar: AvatarController) = Capability(
    "avatar_mode", "Choose the face: mode=avatar (3D VRM avatar) or mode=eyes (the eyes face).",
    """{"type":"object","properties":{"mode":{"type":"string","enum":["avatar","eyes"]}},"required":["mode"]}""",
) { a ->
    when (a["mode"]?.jsonPrimitive?.contentOrNull) {
        "avatar" -> { avatar.setEnabled(true); ActionResult.completed("Face mode: avatar") }
        "eyes" -> { avatar.setEnabled(false); ActionResult.completed("Face mode: eyes") }
        else -> ActionResult.failed("mode must be avatar or eyes")
    }
}

/** avatar_pose: hold a pose the agent composes itself, bone by bone. */
fun avatarPoseCapability(avatar: AvatarController) = Capability(
    "avatar_pose", "Pose the 3D avatar yourself: bones = {boneName: [x,y,z]} for any VRM humanoid bone (hips, spine, chest, neck, head, left/right Shoulder|UpperArm|LowerArm|Hand|UpperLeg|LowerLeg|Foot|Toes, finger joints like leftIndexProximal), " +
        "expressions = {name: 0..1} (aa ih ou ee oh blink, plus any the model has), hips_offset = [x,y,z] metres, blend_ms (default 250). reset=true returns to rest. $AXES",
    """{"type":"object","properties":{"bones":{"type":"object"},"expressions":{"type":"object"},"hips_offset":{"type":"array","items":{"type":"number"}},"blend_ms":{"type":"integer"},"reset":{"type":"boolean"}}}""",
) { a ->
    if (a["reset"]?.jsonPrimitive?.contentOrNull == "true") { avatar.reset(); return@Capability ActionResult.completed("Avatar reset to rest") }
    val (p, err) = PoseParser.pose(a)
    if (p == null) return@Capability ActionResult.failed(err ?: "bad pose")
    if (p.isEmpty) return@Capability ActionResult.failed("give bones, expressions, hips_offset or reset=true")
    val blend = (a["blend_ms"]?.jsonPrimitive?.floatOrNull ?: 250f).toInt().coerceIn(0, 5000)
    avatar.setPose(p, blend); ActionResult.completed("Pose set (${p.bones.size} bones, ${p.expressions.size} expressions)")
}

/** avatar_animate: play a keyframe animation the agent writes. Any motion, no presets. */
fun avatarAnimateCapability(avatar: AvatarController) = Capability(
    "avatar_animate", "Play your own animation on the 3D avatar: keyframes = [{t_ms, bones:{name:[x,y,z]}, expressions:{}, hips_offset:[x,y,z]}] (up to 60 keyframes, 60 s), loop=true repeats. " +
        "Values ease between keyframes; a bone not set in a keyframe keeps interpolating between the ones that set it, and starts from rest. Stop with avatar_pose reset=true. $AXES",
    """{"type":"object","properties":{"keyframes":{"type":"array","items":{"type":"object"}},"loop":{"type":"boolean"}},"required":["keyframes"]}""",
) { a ->
    val (anim, err) = PoseParser.animation(a)
    if (anim == null) return@Capability ActionResult.failed(err ?: "bad animation")
    avatar.animate(anim); ActionResult.completed("Playing ${anim.keyframes.size} keyframes${if (anim.loop) " (looping)" else ""}")
}
