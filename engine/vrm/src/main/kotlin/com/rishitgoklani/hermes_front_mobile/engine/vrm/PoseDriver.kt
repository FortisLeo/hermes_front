package com.rishitgoklani.hermes_front_mobile.engine.vrm

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/** What the agent wants: euler rotations (radians) of any bone/node, translation offsets of any node, expression weights, optional hips offset. */
class PoseSpec(
    val bones: Map<String, FloatArray> = emptyMap(), val expressions: Map<String, Float> = emptyMap(),
    val hips: FloatArray? = null, val offsets: Map<String, FloatArray> = emptyMap(),
)
class KeyframeSpec(val tMs: Long, val pose: PoseSpec)
class AnimationSpec(val keyframes: List<KeyframeSpec>, val loop: Boolean)

/** Everything that changes from frame to frame, in engine terms (no Android, no app types). */
class DriverInput(
    val mouth: Float = 0f, val gazeX: Float = 0f, val gazeY: Float = 0f, val mood: String = "", val blendMs: Int = 250,
    val pose: PoseSpec = PoseSpec(), val animation: AnimationSpec? = null,
)

/** Something that overwrites bones directly each frame (a VRMA clip). While [active] the driver leaves the bones to it. */
interface ClipPlayer { val active: Boolean; fun advance(dt: Float, rig: Rig) }

/**
 * Turns [DriverInput] into rig poses: blends toward the held pose, samples agent animations, adds breathing, gaze turn, lip sync and blinking.
 * The only built-in stance is arms-down; every other motion comes from the agent.
 */
class PoseDriver(private val rig: Rig, private val random: () -> Float = { Math.random().toFloat() }) {
    companion object {
        private val REST = mapOf("leftUpperArm" to floatArrayOf(0f, 0f, 1.25f), "rightUpperArm" to floatArrayOf(0f, 0f, -1.25f))
        private val MOODS = listOf("happy", "sad", "angry", "surprised", "relaxed", "neutral")
        private val ZERO = floatArrayOf(0f, 0f, 0f)
        fun ease(x: Float) = x * x * (3 - 2 * x)
    }

    private val curBones = HashMap<String, FloatArray>(); private val curPos = HashMap<String, FloatArray>(); private val curExpr = HashMap<String, Float>()
    private var curHips = floatArrayOf(0f, 0f, 0f); private var mouth = 0f; private var gx = 0f; private var gy = 0f
    private var blinkIn = 2f; private var blinkT = -1f; private var time = 0f
    private var lastAnim: AnimationSpec? = null; private var animStartMs = 0L
    var clip: ClipPlayer? = null
    /** Ids of rest-pose offsets already applied, so nodes the agent stops touching return to rest. */
    private val touched = HashSet<String>()

    /** Is anything still moving? The renderer uses this to drop to a low frame rate when the answer is no. */
    var moving = false; private set
    private var lastPoseKey: Any? = null; private var lastPoseChangeMs = -1_000_000L

    fun reset() { curBones.clear(); curPos.clear(); curExpr.clear(); curHips = floatArrayOf(0f, 0f, 0f); blinkT = -1f; touched.clear(); lastAnim = null; lastPoseKey = null }

    fun frame(dt: Float, nowMs: Long, input: DriverInput) {
        time += dt
        if (input.animation !== lastAnim) { lastAnim = input.animation; animStartMs = nowMs }
        if (input.pose !== lastPoseKey) { lastPoseKey = input.pose; lastPoseChangeMs = nowMs }
        val k = 1 - 0.001.pow(dt.toDouble()).toFloat()
        val kb = if (input.blendMs <= 0) 1f else 1 - exp(-dt / (input.blendMs / 1000f / 3f))
        mouth += (input.mouth - mouth) * k; gx += (input.gazeX - gx) * k; gy += (input.gazeY - gy) * k
        val anim = input.animation?.takeIf { it.keyframes.isNotEmpty() }
        val sampled = anim?.let { sample(it, nowMs - animStartMs) }
        val direct = sampled != null
        val tBones = sampled?.bones ?: input.pose.bones; val tExpr = sampled?.expressions ?: input.pose.expressions
        val tHips = if (direct) sampled!!.hips else input.pose.hips; val tPos = sampled?.offsets ?: input.pose.offsets
        val clipOn = clip?.active == true
        moving = anim != null || clipOn || mouth > 0.02f || input.mouth > 0.02f || (nowMs - lastPoseChangeMs) < input.blendMs + 200

        if (!clipOn) {
            val breathe = sin(time * 1.6f) * 0.012f
            val names = HashSet<String>(); names += REST.keys; names += curBones.keys; names += tBones.keys
            for (n in names) {
                val want = tBones[n] ?: REST[n] ?: ZERO
                val c = curBones.getOrPut(n) { (REST[n] ?: ZERO).copyOf() }
                for (i in 0..2) c[i] = if (direct) want[i] else c[i] + (want[i] - c[i]) * kb
                applyBone(n, c[0], c[1], c[2], breathe)
            }
            for (n in arrayOf("spine", "chest", "neck", "head")) if (n !in names) applyBone(n, 0f, 0f, 0f, breathe)
            val pnames = HashSet<String>(); pnames += curPos.keys; pnames += tPos.keys
            for (n in pnames) {
                val want = tPos[n] ?: ZERO; val c = curPos.getOrPut(n) { FloatArray(3) }
                for (i in 0..2) c[i] = if (direct) want[i] else c[i] + (want[i] - c[i]) * kb
                val node = rig.nodeFor(n); if (node >= 0) rig.setNodeTranslationOffset(node, c[0], c[1], c[2])
            }
            rig.asset.humanoid["hips"]?.let { hips ->
                val w = tHips ?: ZERO
                curHips = FloatArray(3) { i -> if (direct) w[i] else curHips[i] + (w[i] - curHips[i]) * kb }
                rig.setNodeTranslationOffset(hips, curHips[0], curHips[1], curHips[2])
            }
        } else clip?.advance(dt, rig)

        // Expressions
        rig.clearExpressions()
        val en = HashSet<String>(); en += curExpr.keys; en += tExpr.keys
        for (n in en) { val want = tExpr[n] ?: 0f; val c = curExpr[n] ?: 0f; val v = if (direct) want else c + (want - c) * kb; curExpr[n] = v; rig.setExpression(n, v) }
        if (!tExpr.containsKey("aa")) rig.setExpression("aa", mouth.coerceIn(0f, 1f))
        if (!tExpr.containsKey("blink")) {
            blinkIn -= dt
            if (blinkIn <= 0f && blinkT < 0f) blinkT = 0f
            if (blinkT >= 0f) {
                blinkT += dt; rig.setExpression("blink", sin(minOf(blinkT / 0.18f, 1f) * Math.PI.toFloat()))
                if (blinkT > 0.18f) { blinkT = -1f; blinkIn = 2f + random() * 3f }
            }
        }
        if (input.mood.isNotEmpty()) for (m in MOODS) if (rig.hasExpression(m)) rig.setExpression(m, if (m == input.mood) 0.8f else 0f)
        rig.applyExpressions()
    }

    private fun applyBone(n: String, x0: Float, y0: Float, z: Float, breathe: Float) {
        var x = x0; var y = y0
        if (n == "spine" || n == "chest") x += breathe
        if (n == "head") { x += gy * 0.15f; y += -gx * 0.3f }
        if (n == "neck") { x += gy * 0.1f; y += -gx * 0.2f }
        val node = rig.nodeFor(n); if (node < 0) return
        if (rig.isHumanoid(node)) rig.setBoneEuler(node, x, y, z) else rig.setNodeEulerOffset(node, x, y, z)
    }

    private class Sampled(val bones: Map<String, FloatArray>, val expressions: Map<String, Float>, val hips: FloatArray?, val offsets: Map<String, FloatArray>)

    /** Per bone/expression, ease between the keyframes that set it; before its first keyframe it starts from rest. */
    private fun sample(anim: AnimationSpec, tIn: Long): Sampled {
        val kfs = anim.keyframes; val dur = kfs.last().tMs; var t = tIn.toFloat()
        t = if (anim.loop && dur > 0) t % dur else minOf(t, dur.toFloat())
        fun track(rest: FloatArray, get: (PoseSpec) -> FloatArray?): FloatArray {
            var pt = 0f; var pv = rest; var nt = -1f; var nv: FloatArray? = null
            for (kf in kfs) { val v = get(kf.pose) ?: continue; if (kf.tMs <= t) { pt = kf.tMs.toFloat(); pv = v } else { nt = kf.tMs.toFloat(); nv = v; break } }
            if (nv == null) return pv
            val f = if (nt == pt) 1f else ease((t - pt) / (nt - pt)); return FloatArray(pv.size) { pv[it] + (nv[it] - pv[it]) * f }
        }
        val bones = HashMap<String, FloatArray>(); val offs = HashMap<String, FloatArray>(); val expr = HashMap<String, Float>()
        kfs.forEach { it.pose.bones.keys.forEach { n -> if (n !in bones) bones[n] = track(REST[n] ?: ZERO) { p -> p.bones[n] } } }
        kfs.forEach { it.pose.offsets.keys.forEach { n -> if (n !in offs) offs[n] = track(ZERO) { p -> p.offsets[n] } } }
        kfs.forEach { it.pose.expressions.keys.forEach { n -> if (n !in expr) expr[n] = track(floatArrayOf(0f)) { p -> p.expressions[n]?.let { floatArrayOf(it) } }[0] } }
        val hips = if (kfs.any { it.pose.hips != null }) track(ZERO) { it.hips } else null
        return Sampled(bones, expr, hips, offs)
    }
}
