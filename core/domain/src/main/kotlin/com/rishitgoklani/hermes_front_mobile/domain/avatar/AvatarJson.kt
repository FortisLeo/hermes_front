package com.rishitgoklani.hermes_front_mobile.domain.avatar

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** The 55 VRM humanoid bones the agent may rotate. */
object HumanoidBones {
    val names: Set<String> = buildSet {
        addAll(listOf("hips", "spine", "chest", "upperChest", "neck", "head", "leftEye", "rightEye", "jaw"))
        for (s in listOf("left", "right")) {
            addAll(listOf("Shoulder", "UpperArm", "LowerArm", "Hand", "UpperLeg", "LowerLeg", "Foot", "Toes").map { s + it })
            for (f in listOf("Thumb", "Index", "Middle", "Ring", "Little")) for (j in listOf("Proximal", "Intermediate", "Distal")) add("$s$f$j")
        }
    }
}

/** Validates and converts the agent's pose/animation JSON. Errors are messages, never silent drops. */
object PoseParser {
    const val MAX_KEYFRAMES = 60
    const val MAX_DURATION_MS = 60_000
    private const val MAX_ANGLE = 3.2f

    fun pose(o: JsonObject): Pair<AvatarPose?, String?> {
        val bones = mutableMapOf<String, Vec3>()
        (o["bones"] as? JsonObject)?.forEach { (name, v) ->
            if (name !in HumanoidBones.names) return null to "unknown bone '$name' (use VRM humanoid names such as hips, spine, head, leftUpperArm, rightLowerLeg, leftIndexProximal)"
            val a = v as? JsonArray; val f = a?.map { it.jsonPrimitive.floatOrNull }
            if (f == null || f.size != 3 || f.any { it == null }) return null to "bone '$name' needs [x, y, z] radians"
            if (f.any { kotlin.math.abs(it!!) > MAX_ANGLE }) return null to "bone '$name': angles must be within ±$MAX_ANGLE radians"
            bones[name] = Vec3(f[0]!!, f[1]!!, f[2]!!)
        }
        val expr = mutableMapOf<String, Float>()
        (o["expressions"] as? JsonObject)?.forEach { (name, v) ->
            if (!name.matches(Regex("[A-Za-z0-9_]{1,32}"))) return null to "bad expression name '$name'"
            expr[name] = (v.jsonPrimitive.floatOrNull ?: return null to "expression '$name' needs a number 0..1").coerceIn(0f, 1f)
        }
        val hips = (o["hips_offset"] as? JsonArray)?.let { a ->
            val f = a.map { it.jsonPrimitive.floatOrNull }
            if (f.size != 3 || f.any { it == null || kotlin.math.abs(it) > 1.5f }) return null to "hips_offset needs [x, y, z] metres within ±1.5"
            Vec3(f[0]!!, f[1]!!, f[2]!!)
        }
        return AvatarPose(bones, expr, hips) to null
    }

    fun animation(o: JsonObject): Pair<AvatarAnimation?, String?> {
        val arr = o["keyframes"] as? JsonArray ?: return null to "keyframes must be an array"
        if (arr.isEmpty()) return null to "need at least one keyframe"
        if (arr.size > MAX_KEYFRAMES) return null to "at most $MAX_KEYFRAMES keyframes"
        val kfs = arr.mapIndexed { i, el ->
            val ko = el as? JsonObject ?: return null to "keyframe $i is not an object"
            val t = ko["t_ms"]?.jsonPrimitive?.floatOrNull ?: return null to "keyframe $i needs t_ms"
            if (t < 0 || t > MAX_DURATION_MS) return null to "keyframe $i: t_ms must be 0..$MAX_DURATION_MS"
            val (p, err) = pose(ko); if (p == null) return null to "keyframe $i: $err"
            AvatarKeyframe(t.toInt(), p)
        }.sortedBy { it.tMs }
        return AvatarAnimation(kfs, o["loop"]?.jsonPrimitive?.contentOrNull == "true") to null
    }

    private fun vec(v: Vec3) = buildJsonArray { add(JsonPrimitive(v.x)); add(JsonPrimitive(v.y)); add(JsonPrimitive(v.z)) }
    private fun poseJson(p: AvatarPose) = buildJsonObject {
        put("bones", buildJsonObject { p.bones.forEach { (k, v) -> put(k, vec(v)) } })
        put("expr", buildJsonObject { p.expressions.forEach { (k, v) -> put(k, v) } })
        put("hips", p.hips?.let { vec(it) } ?: JsonNull)
    }

    /** One JSON message for the viewer page. Mouth is clamped 0..1, gaze -1..1. */
    fun toJson(s: AvatarState): String = buildJsonObject {
        put("enabled", s.enabled); put("mouth", s.mouth.coerceIn(0f, 1f)); put("gx", s.gazeX.coerceIn(-1f, 1f)); put("gy", s.gazeY.coerceIn(-1f, 1f))
        put("clip", s.clip.filter { it.isLetterOrDigit() || it in "-_" }); put("blend", s.blendMs)
        put("pose", poseJson(s.pose))
        put("anim", s.animation?.let { a -> buildJsonObject { put("loop", a.loop); put("keyframes", JsonArray(a.keyframes.map { k -> buildJsonObject { put("t", k.tMs); put("pose", poseJson(k.pose)) } })) } } ?: JsonNull)
    }.toString()
}
