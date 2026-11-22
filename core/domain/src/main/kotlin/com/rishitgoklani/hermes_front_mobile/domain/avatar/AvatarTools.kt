package com.rishitgoklani.hermes_front_mobile.domain.avatar

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.floatOrNull

private const val AXES = "Humanoid bones use euler [x, y, z] radians in the VRM's normalized space; all zero is the T-pose (arms straight out); the avatar rests with arms down (leftUpperArm z=+1.25, rightUpperArm z=-1.25). " +
    "Any other node of the model (ears, tail, hair roots, props) can be rotated by name too, as an offset from its own rest rotation, and moved with offsets={name:[x,y,z]} metres. Call avatar_bones to list every name. Other axes: experiment and look at the screen. "

private fun unknown(avatar: AvatarController, p: AvatarPose): String? {
    val known = avatar.nodes.value.toSet(); if (known.isEmpty()) return null
    val bad = (p.bones.keys + p.offsets.keys).filter { it !in known }
    return if (bad.isEmpty()) null else "unknown bone/node ${bad.joinToString { "'$it'" }} (call avatar_bones for the model's names)"
}

/** avatar_bones: every bone and node the loaded model has, so the agent can move any of them. */
fun avatarBonesCapability(avatar: AvatarController) = Capability(
    "avatar_bones", "List every bone/node name of the loaded 3D avatar model (humanoid bones and extra nodes), so you can pose any of them with avatar_pose / avatar_animate.",
) {
    val n = avatar.nodes.value
    if (n.isEmpty()) ActionResult.failed("Model not ready: switch to avatar mode (avatar_mode) and try again")
    else ActionResult.completed(n.joinToString(", "), kotlinx.serialization.json.buildJsonObject { put("count", kotlinx.serialization.json.JsonPrimitive(n.size)) })
}

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
    "avatar_pose", "Pose the 3D avatar yourself: bones = {boneName: [x,y,z]} for ANY bone or node of the model (VRM humanoid names like hips, spine, head, leftUpperArm, rightLowerLeg, leftIndexProximal, or any other node from avatar_bones), " +
        "expressions = {name: 0..1} (aa ih ou ee oh blink, plus any the model has), offsets = {node: [x,y,z]} metres, hips_offset = [x,y,z] metres, blend_ms (default 250). reset=true returns to rest. $AXES",
    """{"type":"object","properties":{"bones":{"type":"object"},"expressions":{"type":"object"},"offsets":{"type":"object"},"hips_offset":{"type":"array","items":{"type":"number"}},"blend_ms":{"type":"integer"},"reset":{"type":"boolean"}}}""",
) { a ->
    if (a["reset"]?.jsonPrimitive?.contentOrNull == "true") { avatar.reset(); return@Capability ActionResult.completed("Avatar reset to rest") }
    val (p, err) = PoseParser.pose(a)
    if (p == null) return@Capability ActionResult.failed(err ?: "bad pose")
    if (p.isEmpty) return@Capability ActionResult.failed("give bones, expressions, hips_offset or reset=true")
    unknown(avatar, p)?.let { return@Capability ActionResult.failed(it) }
    val blend = (a["blend_ms"]?.jsonPrimitive?.floatOrNull ?: 250f).toInt().coerceIn(0, 5000)
    avatar.setPose(p, blend); ActionResult.completed("Pose set (${p.bones.size} bones, ${p.expressions.size} expressions)")
}

/** avatar_animate: play a keyframe animation the agent writes. Any motion, no presets. */
fun avatarAnimateCapability(avatar: AvatarController) = Capability(
    "avatar_animate", "Play your own animation on the 3D avatar: keyframes = [{t_ms, bones:{name:[x,y,z]}, expressions:{}, offsets:{}, hips_offset:[x,y,z]}] (up to 60 keyframes, 60 s), loop=true repeats. " +
        "Values ease between keyframes; a bone not set in a keyframe keeps interpolating between the ones that set it, and starts from rest. Stop with avatar_pose reset=true. $AXES",
    """{"type":"object","properties":{"keyframes":{"type":"array","items":{"type":"object"}},"loop":{"type":"boolean"}},"required":["keyframes"]}""",
) { a ->
    val (anim, err) = PoseParser.animation(a)
    if (anim == null) return@Capability ActionResult.failed(err ?: "bad animation")
    anim.keyframes.firstNotNullOfOrNull { unknown(avatar, it.pose) }?.let { return@Capability ActionResult.failed(it) }
    avatar.animate(anim); ActionResult.completed("Playing ${anim.keyframes.size} keyframes${if (anim.loop) " (looping)" else ""}")
}
