package com.rishitgoklani.hermes_front_mobile.engine.vrm

import kotlin.math.abs
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
    /** Name of a VRMA clip to play (empty = none); the driver asks [PoseDriver.clipSource] for its bytes. */
    val clip: String = "",
    /** How strongly the mood shows (0..1), the reaction playing now ("" = none) and how strongly, what the bot is doing ("thinking", "listening", ...). */
    val moodIntensity: Float = 1f, val reaction: String = "", val reactionIntensity: Float = 1f, val activity: String = "",
    /** Effects to draw on the face: "blush", "tears", "sweatdrop", "angermark" are drawn by the engine, anything else is ignored here. */
    val effects: Set<String> = emptySet(),
)

/** Where VRMA clip files come from (the app implements it with a folder of .vrma files). Return null when the clip does not exist. */
fun interface ClipSource { fun load(name: String): ByteArray? }

/** Something that overwrites bones directly each frame (a VRMA clip). While [active] the driver leaves the bones to it. */
interface ClipPlayer {
    val active: Boolean
    fun advance(dt: Float, rig: Rig)
    /** Does the clip write [node]? The driver still applies gaze to bones the clip leaves alone. */
    fun drivesNode(node: Int): Boolean = true
    /** Lets the clip contribute expression weights after the rig's expressions were cleared. */
    fun applyExpressions(rig: Rig) {}
}

/**
 * Turns [DriverInput] into rig poses: blends toward the held pose, samples agent animations, adds breathing, gaze (head, neck and eyes), lip sync
 * and blinking, and plays VRMA clips. The only built-in stance is arms-down; every other motion comes from the agent.
 *
 * Names are resolved to rig nodes and expression indexes when the input's pose/animation changes, so [frame] itself allocates nothing.
 */
class PoseDriver(private val rig: Rig, private val random: () -> Float = { Math.random().toFloat() }) {
    companion object {
        private val REST = mapOf("leftUpperArm" to floatArrayOf(0f, 0f, 1.25f), "rightUpperArm" to floatArrayOf(0f, 0f, -1.25f))
        private val ZERO = floatArrayOf(0f, 0f, 0f)
        private val BASE_BONES = arrayOf("leftUpperArm", "rightUpperArm", "spine", "chest", "neck", "head", "leftEye", "rightEye")
        private const val R_NONE = 0; private const val R_SPINE = 1; private const val R_NECK = 2; private const val R_HEAD = 3; private const val R_EYE = 4
        fun ease(x: Float) = x * x * (3 - 2 * x)
    }

    /** Keyframes of one bone/offset/expression across an animation; [rest] is where it starts before its first keyframe. */
    private class Track(val comps: Int, val rest: FloatArray, val times: FloatArray, val vals: Array<FloatArray>) {
        fun sample(t: Float, out: FloatArray) {
            var pv = rest; var pt = 0f; var nv: FloatArray? = null; var nt = -1f
            for (i in times.indices) { if (times[i] <= t) { pt = times[i]; pv = vals[i] } else { nt = times[i]; nv = vals[i]; break } }
            if (nv == null) { for (k in 0 until comps) out[k] = if (k < pv.size) pv[k] else 0f; return }
            val f = if (nt == pt) 1f else ease((t - pt) / (nt - pt))
            for (k in 0 until comps) { val a = if (k < pv.size) pv[k] else 0f; val b = if (k < nv.size) nv[k] else 0f; out[k] = a + (b - a) * f }
        }
    }

    private class BoneSlot(val name: String, val node: Int, val role: Int) {
        val cur = (REST[name] ?: ZERO).copyOf(); val buf = FloatArray(3); var want: FloatArray = REST[name] ?: ZERO; var track: Track? = null
    }
    private class OffsetSlot(val node: Int, val humanoid: Boolean) { val cur = FloatArray(3); val buf = FloatArray(3); var want: FloatArray = ZERO; var track: Track? = null }
    private class ExprSlot(val name: String, val idx: Int) { var cur = 0f; var want = 0f; val buf = FloatArray(1); var track: Track? = null }

    private val bones = ArrayList<BoneSlot>(); private val boneByName = HashMap<String, BoneSlot>()
    private val offsets = ArrayList<OffsetSlot>(); private val offsetByName = HashMap<String, OffsetSlot>()
    private val exprs = ArrayList<ExprSlot>(); private val exprByName = HashMap<String, ExprSlot>()
    private val hipsNode = rig.asset.humanoid["hips"] ?: -1
    private val hips = OffsetSlot(hipsNode, true)
    private val aaIdx = rig.expressionIndex("aa"); private val blinkIdx = rig.expressionIndex("blink")
    /** Mood, reaction and activity recipes, easing and the on-face effect fades; the renderer reads the effect weights from here. */
    val face = FaceMixer(rig)

    private var mouth = 0f; private var gx = 0f; private var gy = 0f
    /** Gaze after the mood's own bias is added (thinking looks up and aside, shy looks down). */
    private var egx = 0f; private var egy = 0f
    private var blinkIn = 2f; private var blinkT = -1f; private var time = 0f
    private var lastAnim: AnimationSpec? = null; private var animStartMs = 0L
    private var plan: AnimationSpec? = null; private var planDur = 0L; private var planLoop = false
    private var agentBlink = false; private var agentAa = false
    private var lastPoseKey: Any? = null; private var lastPoseChangeMs = -1_000_000L

    var clip: ClipPlayer? = null
    /** Set by the host so [DriverInput.clip] can be resolved. */
    var clipSource: ClipSource? = null
    private var clipName = ""; private var ownedClip: VrmaPlayer? = null
    private val clipCache = HashMap<String, VrmaClip?>()

    /** Is anything still moving? The renderer uses this to drop to a low frame rate when the answer is no. */
    var moving = false; private set
    /** Idle chest/spine sway. Turn it off so a still pose leaves the rig clean ([Rig.consumeDirty] false) and the renderer can stop drawing. */
    var breathing = true

    init { initBase() }

    private fun initBase() {
        for (n in BASE_BONES) boneSlot(n)
        if (hipsNode >= 0) { hips.cur.fill(0f); hips.want = ZERO; hips.track = null }
    }

    private fun roleOf(name: String) = when (name) { "spine", "chest" -> R_SPINE; "neck" -> R_NECK; "head" -> R_HEAD; "leftEye", "rightEye" -> R_EYE; else -> R_NONE }

    private fun boneSlot(name: String): BoneSlot? {
        boneByName[name]?.let { return it }
        val node = rig.nodeFor(name); if (node < 0) return null
        return BoneSlot(name, node, roleOf(name)).also { bones += it; boneByName[name] = it }
    }
    private fun offsetSlot(name: String): OffsetSlot? {
        offsetByName[name]?.let { return it }
        val node = rig.nodeFor(name); if (node < 0) return null
        return OffsetSlot(node, rig.isHumanoid(node)).also { offsets += it; offsetByName[name] = it }
    }
    private fun exprSlot(name: String): ExprSlot? {
        exprByName[name]?.let { return it }
        val idx = rig.expressionIndex(name); if (idx < 0) return null
        return ExprSlot(name, idx).also { exprs += it; exprByName[name] = it }
    }

    fun reset() {
        stopOwnedClip(); clipName = ""; clipCache.clear()
        bones.clear(); boneByName.clear(); offsets.clear(); offsetByName.clear(); exprs.clear(); exprByName.clear()
        blinkT = -1f; lastAnim = null; lastPoseKey = null; plan = null; face.reset()
        rig.resetPose(); initBase()
    }

    // ---- clips ----
    private fun stopOwnedClip() { ownedClip?.let { it.stop(); if (clip === it) clip = null }; ownedClip = null }

    private fun clipFor(name: String): VrmaClip? {
        if (clipCache.containsKey(name)) return clipCache[name]
        val parsed = try { clipSource?.load(name)?.let { VrmaParser.parse(it) } } catch (e: Exception) { null }
        clipCache[name] = parsed
        return parsed
    }

    private fun updateClip(name: String) {
        clipName = name
        val had = ownedClip != null
        stopOwnedClip()
        if (name.isNotEmpty()) clipFor(name)?.let { c -> VrmaPlayer(c).also { ownedClip = it; clip = it } }
        if (had || ownedClip != null) rig.resetPose()          // leave no bone stuck in the previous clip's last pose
    }

    // ---- target binding: runs when the pose or animation changes, never per frame ----
    private fun track(kfs: List<KeyframeSpec>, rest: FloatArray, comps: Int, get: (PoseSpec) -> FloatArray?): Track? {
        val ts = ArrayList<Float>(); val vs = ArrayList<FloatArray>()
        for (kf in kfs) { val v = get(kf.pose) ?: continue; ts += kf.tMs.toFloat(); vs += v }
        if (ts.isEmpty()) return null
        return Track(comps, rest, ts.toFloatArray(), vs.toTypedArray())
    }

    private fun bind(pose: PoseSpec, anim: AnimationSpec?) {
        plan = anim
        for (s in bones) { s.track = null; s.want = REST[s.name] ?: ZERO }
        for (s in offsets) { s.track = null; s.want = ZERO }
        for (s in exprs) { s.track = null; s.want = 0f }
        hips.track = null; hips.want = ZERO
        if (anim == null) {
            for ((name, v) in pose.bones) boneSlot(name)?.want = v
            for ((name, v) in pose.offsets) offsetSlot(name)?.want = v
            for ((name, v) in pose.expressions) exprSlot(name)?.want = v
            hips.want = pose.hips ?: ZERO
            agentBlink = pose.expressions.containsKey("blink"); agentAa = pose.expressions.containsKey("aa")
            return
        }
        val kfs = anim.keyframes; planDur = kfs.last().tMs; planLoop = anim.loop
        val bn = HashSet<String>(); val on = HashSet<String>(); val en = HashSet<String>()
        kfs.forEach { bn += it.pose.bones.keys; on += it.pose.offsets.keys; en += it.pose.expressions.keys }
        for (n in bn) boneSlot(n)?.let { it.track = track(kfs, REST[n] ?: ZERO, 3) { p -> p.bones[n] } }
        for (n in on) offsetSlot(n)?.let { it.track = track(kfs, ZERO, 3) { p -> p.offsets[n] } }
        for (n in en) exprSlot(n)?.let { it.track = track(kfs, FloatArray(1), 1) { p -> p.expressions[n]?.let { v -> floatArrayOf(v) } } }
        if (kfs.any { it.pose.hips != null }) hips.track = track(kfs, ZERO, 3) { it.hips }
        agentBlink = "blink" in en; agentAa = "aa" in en
    }

    fun frame(dt: Float, nowMs: Long, input: DriverInput) {
        time += dt
        var rebind = false
        if (input.animation !== lastAnim) { lastAnim = input.animation; animStartMs = nowMs; rebind = true }
        if (input.pose !== lastPoseKey) { lastPoseKey = input.pose; lastPoseChangeMs = nowMs; rebind = true }
        val anim = input.animation?.takeIf { it.keyframes.isNotEmpty() }
        if (rebind) bind(input.pose, anim)
        if (input.clip != clipName) updateClip(input.clip)

        val k = 1 - 0.001.pow(dt.toDouble()).toFloat()
        val kb = if (input.blendMs <= 0) 1f else 1 - exp(-dt / (input.blendMs / 1000f / 3f))
        mouth += (input.mouth - mouth) * k; gx += (input.gazeX - gx) * k; gy += (input.gazeY - gy) * k
        face.update(dt, input); egx = gx + face.gazeX; egy = gy + face.gazeY
        val direct = anim != null
        var at = 0f
        if (direct) { val t = (nowMs - animStartMs); at = (if (planLoop && planDur > 0) t % planDur else minOf(t, planDur)).toFloat() }
        val clipPlayer = clip; val clipOn = clipPlayer?.active == true
        moving = direct || clipOn || mouth > 0.02f || input.mouth > 0.02f || (nowMs - lastPoseChangeMs) < input.blendMs + 200 ||
            blinkT >= 0f || abs(gx - input.gazeX) > 0.003f || abs(gy - input.gazeY) > 0.003f || face.busy

        if (!clipOn) {
            val breathe = if (breathing) sin(time * 1.6f) * 0.012f else 0f
            for (i in bones.indices) {
                val s = bones[i]; val tr = s.track
                val want = if (direct && tr != null) { tr.sample(at, s.buf); s.buf } else s.want
                val c = s.cur
                for (j in 0..2) c[j] = if (direct) want[j] else c[j] + (want[j] - c[j]) * kb
                applyBone(s, breathe)
            }
            for (i in offsets.indices) {
                val s = offsets[i]; val tr = s.track
                val want = if (direct && tr != null) { tr.sample(at, s.buf); s.buf } else s.want
                val c = s.cur
                for (j in 0..2) c[j] = if (direct) want[j] else c[j] + (want[j] - c[j]) * kb
                if (s.humanoid) rig.setBoneOffset(s.node, c[0], c[1], c[2]) else rig.setNodeTranslationOffset(s.node, c[0], c[1], c[2])
            }
            if (hipsNode >= 0) {
                val tr = hips.track
                val want = if (direct && tr != null) { tr.sample(at, hips.buf); hips.buf } else hips.want
                val c = hips.cur
                for (j in 0..2) c[j] = if (direct) want[j] else c[j] + (want[j] - c[j]) * kb
                rig.setBoneOffset(hipsNode, c[0], c[1], c[2])
            }
        } else {
            clipPlayer!!.advance(dt, rig)
            for (i in bones.indices) { val s = bones[i]; if (s.role == R_EYE && !clipPlayer.drivesNode(s.node)) rig.setBoneEuler(s.node, egy * 0.15f, -egx * 0.25f, 0f) }
        }

        // Expressions: clip weights first, then the agent's on top, then lip sync, blink and mood.
        rig.clearExpressions()
        if (clipOn) clipPlayer!!.applyExpressions(rig)
        for (i in exprs.indices) {
            val s = exprs[i]; val tr = s.track
            val want = if (direct && tr != null) { tr.sample(at, s.buf); s.buf[0] } else s.want
            s.cur = if (direct) want else s.cur + (want - s.cur) * kb
            if (s.cur != 0f || want != 0f) rig.setExpression(s.idx, s.cur)
        }
        if (!agentAa && (mouth > 0.001f || !clipOn)) rig.setExpression(aaIdx, mouth.coerceIn(0f, 1f))
        if (!agentBlink) {
            blinkIn -= dt
            if (blinkIn <= 0f && blinkT < 0f) blinkT = 0f
            if (blinkT >= 0f) {
                blinkT += dt; rig.setExpression(blinkIdx, sin(minOf(blinkT / 0.18f, 1f) * Math.PI.toFloat()))
                if (blinkT > 0.18f) { blinkT = -1f; blinkIn = 2f + random() * 3f }
            }
        }
        face.apply()
        rig.applyExpressions()
    }

    private fun applyBone(s: BoneSlot, breathe: Float) {
        val c = s.cur; var x = c[0]; var y = c[1]; var z = c[2]
        val neck = if (boneByName.containsKey("neck")) 0.35f else 0f   // the neck takes a share of the head motion, the head the rest
        when (s.role) {
            R_SPINE -> x += breathe + face.spineX * 0.5f
            R_NECK -> { x += egy * 0.1f + face.headX * neck; y += -egx * 0.2f + face.headY * neck; z += face.headZ * neck }
            R_HEAD -> { x += egy * 0.15f + face.headX * (1f - neck); y += -egx * 0.3f + face.headY * (1f - neck); z += face.headZ * (1f - neck) }
            R_EYE -> { x += egy * 0.15f; y += -egx * 0.25f }     // eyes lead the head a little: small angles on top of the head turn
        }
        if (rig.isHumanoid(s.node)) rig.setBoneEuler(s.node, x, y, z) else rig.setNodeEulerOffset(s.node, x, y, z)
    }
}
