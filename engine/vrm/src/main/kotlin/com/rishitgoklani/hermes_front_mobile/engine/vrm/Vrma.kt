package com.rishitgoklani.hermes_front_mobile.engine.vrm

import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.Accessors
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.Glb
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.a
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.i
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.o
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.obj
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.s
import com.rishitgoklani.hermes_front_mobile.engine.vrm.math.Mat4
import com.rishitgoklani.hermes_front_mobile.engine.vrm.math.Quat

/** One animated channel: keyframe [times] (seconds) and [values] ([comps] floats per key). Quaternion tracks slerp; others lerp; [step] holds. */
class VrmaTrack(val name: String, val comps: Int, val times: FloatArray, val values: FloatArray, val step: Boolean) {
    val isQuat get() = comps == 4

    /** Samples at [t] into [out]; no allocation. */
    fun sample(t: Float, out: FloatArray, tmp: FloatArray) {
        val n = times.size
        if (n == 0) return
        if (n == 1 || t <= times[0]) { System.arraycopy(values, 0, out, 0, comps); return }
        if (t >= times[n - 1]) { System.arraycopy(values, (n - 1) * comps, out, 0, comps); return }
        var lo = 0; var hi = n - 1
        while (hi - lo > 1) { val mid = (lo + hi) ushr 1; if (times[mid] <= t) lo = mid else hi = mid }
        if (step) { System.arraycopy(values, lo * comps, out, 0, comps); return }
        val f = (t - times[lo]) / (times[hi] - times[lo])
        if (comps == 4) {
            for (k in 0..3) { out[k] = values[lo * 4 + k]; tmp[k] = values[hi * 4 + k] }
            Quat.slerp(out, tmp, f, out)
        } else for (k in 0 until comps) out[k] = values[lo * comps + k] + (values[hi * comps + k] - values[lo * comps + k]) * f
    }
}

/** A parsed VRMA animation: humanoid rotations (normalized, VRM 1.0 frame), optional hips motion and expression weights. */
class VrmaClip(
    val duration: Float, val rotations: List<VrmaTrack>, val hips: VrmaTrack?, val restHipsHeight: Float, val expressions: List<VrmaTrack>,
)

/** Reads a .vrma (GLB with the VRMC_vrm_animation extension), following three-vrm-animation's conversion to normalized bone space. */
object VrmaParser {
    fun parse(bytes: ByteArray): VrmaClip = parse(Glb.parse(bytes))

    fun parse(glb: Glb): VrmaClip {
        val root = glb.json
        val ext = root.o("extensions")?.o("VRMC_vrm_animation") ?: throw VrmFormatException("Not a VRMA animation (no VRMC_vrm_animation extension)")
        val acc = Accessors(root, glb.bin)
        val nodes = VrmParser.parseNodes(root)
        val worldM = VrmParser.restWorldMatrices(nodes)
        val boneByNode = HashMap<Int, String>(); val exprByNode = HashMap<Int, String>()
        ext.o("humanoid")?.o("humanBones")?.forEach { (name, v) -> v.obj()?.i("node")?.let { boneByNode[it] = name } }
        ext.o("expressions")?.let { ex -> for (group in arrayOf("preset", "custom")) ex.o(group)?.forEach { (name, v) -> v.obj()?.i("node")?.let { exprByNode[it] = name } } }
        val hipsNode = boneByNode.entries.firstOrNull { it.value == "hips" }?.key
        if (hipsNode == null || hipsNode !in nodes.indices) throw VrmFormatException("VRMA has no hips bone")

        val t = FloatArray(3); val s = FloatArray(3)
        fun worldRot(node: Int, out: FloatArray) {
            val m = FloatArray(16); System.arraycopy(worldM, node * 16, m, 0, 16); VrmParser.decompose(m, t, out, s)
        }
        val hipsParent = nodes[hipsNode].parent
        val hipsParentM = FloatArray(16).also { if (hipsParent >= 0) System.arraycopy(worldM, hipsParent * 16, it, 0, 16) else Mat4.setIdentity(it) }
        val hipsParentRot = FloatArray(4).also { if (hipsParent >= 0) worldRot(hipsParent, it) else it[3] = 1f }
        fun parentBoneRot(node: Int, out: FloatArray) {
            var p = nodes[node].parent
            while (p >= 0 && p !in boneByNode) p = nodes[p].parent
            if (p >= 0) worldRot(p, out) else System.arraycopy(hipsParentRot, 0, out, 0, 4)
        }

        val rotations = ArrayList<VrmaTrack>(); val expressions = ArrayList<VrmaTrack>(); var hips: VrmaTrack? = null; var duration = 0f
        val anim = root.a("animations").firstOrNull().obj() ?: throw VrmFormatException("VRMA has no animation")
        val samplers = anim.a("samplers")
        val q = FloatArray(4); val v = FloatArray(3)
        for (ch in anim.a("channels")) {
            val c = ch.obj() ?: continue; val target = c.o("target") ?: continue; val node = target.i("node") ?: continue; val path = target.s("path") ?: continue
            val sm = samplers.getOrNull(c.i("sampler") ?: -1).obj() ?: continue
            val times = acc.floats(sm.i("input") ?: continue); var values = acc.floats(sm.i("output") ?: continue)
            if (times.isEmpty()) continue
            val interp = sm.s("interpolation") ?: "LINEAR"
            val comps = if (path == "rotation") 4 else 3
            if (interp == "CUBICSPLINE") values = FloatArray(times.size * comps) { k -> values[(k / comps) * comps * 3 + comps + k % comps] }   // keep the values, drop the tangents
            if (values.size < times.size * comps) continue
            duration = maxOf(duration, times.last())
            val step = interp == "STEP"
            val bone = boneByNode[node]
            if (bone != null) {
                if (path == "translation") {
                    if (bone != "hips") continue          // only the hips may translate
                    val out = FloatArray(times.size * 3)
                    for (k in times.indices) { Mat4.transformPoint(hipsParentM, 0, values[k * 3], values[k * 3 + 1], values[k * 3 + 2], v); System.arraycopy(v, 0, out, k * 3, 3) }
                    hips = VrmaTrack(bone, 3, times, out, step)
                } else if (path == "rotation") {
                    // normalized = parentBoneRestWorld * local * boneRestWorld^-1
                    val rd = FloatArray(4); worldRot(node, rd); Quat.conjugate(rd, rd)
                    val vp = FloatArray(4); parentBoneRot(node, vp)
                    val out = FloatArray(times.size * 4)
                    for (k in times.indices) { for (j in 0..3) q[j] = values[k * 4 + j]; Quat.mul(vp, q, q); Quat.mul(q, rd, q); Quat.normalize(q); System.arraycopy(q, 0, out, k * 4, 4) }
                    rotations += VrmaTrack(bone, 4, times, out, step)
                }
                continue
            }
            val expr = exprByNode[node]
            if (expr != null && path == "translation") {
                expressions += VrmaTrack(expr, 1, times, FloatArray(times.size) { values[it * 3] }, step)    // the weight rides on translation.x
            }
        }
        if (rotations.isEmpty() && hips == null && expressions.isEmpty()) throw VrmFormatException("VRMA has no usable animation channels")
        return VrmaClip(duration, rotations, hips, worldM[hipsNode * 16 + 13], expressions)
    }
}

/** Plays a [VrmaClip] on a [Rig], looped. Resolves bone and expression names once per rig; sampling allocates nothing. */
class VrmaPlayer(val clip: VrmaClip, private val loop: Boolean = true) : ClipPlayer {
    override var active = true; private set
    private var time = 0f
    private var bound: Rig? = null
    private var rotNodes = IntArray(0); private var hipsNode = -1; private var hipsScale = 1f
    private var exprIdx = IntArray(0); private val exprVal = FloatArray(clip.expressions.size)
    private val q = FloatArray(4); private val tmp = FloatArray(4); private val v = FloatArray(3)
    private var driven = BooleanArray(0)

    fun stop() { active = false }
    fun restart() { time = 0f; active = true }
    val position get() = time

    private fun bind(rig: Rig) {
        bound = rig
        rotNodes = IntArray(clip.rotations.size) { rig.asset.humanoid[clip.rotations[it].name] ?: -1 }
        hipsNode = if (clip.hips != null) rig.asset.humanoid["hips"] ?: -1 else -1
        hipsScale = if (clip.restHipsHeight > 1e-6f && rig.restHipsHeight > 1e-6f) rig.restHipsHeight / clip.restHipsHeight else 1f
        exprIdx = IntArray(clip.expressions.size) { rig.expressionIndex(clip.expressions[it].name) }
        driven = BooleanArray(rig.asset.nodes.size).also { s -> rotNodes.forEach { if (it >= 0) s[it] = true }; if (hipsNode >= 0) s[hipsNode] = true }
    }

    override fun drivesNode(node: Int) = active && node in driven.indices && driven[node]

    override fun advance(dt: Float, rig: Rig) {
        if (!active) return
        if (bound !== rig) bind(rig)
        time += dt
        if (clip.duration > 0f) { if (loop) time %= clip.duration else if (time >= clip.duration) { time = clip.duration } }
        for (i in rotNodes.indices) { val node = rotNodes[i]; if (node < 0) continue
            clip.rotations[i].sample(time, q, tmp); rig.setBoneQuat(node, q[0], q[1], q[2], q[3]) }
        clip.hips?.let { h -> if (hipsNode >= 0) { h.sample(time, v, tmp); rig.setBonePosition(hipsNode, v[0] * hipsScale, v[1] * hipsScale, v[2] * hipsScale) } }
        for (i in exprVal.indices) { clip.expressions[i].sample(time, v, tmp); exprVal[i] = v[0] }
    }

    override fun applyExpressions(rig: Rig) {
        if (!active || bound !== rig) return
        for (i in exprIdx.indices) if (exprIdx[i] >= 0) rig.setExpression(exprIdx[i], exprVal[i])
    }
}
