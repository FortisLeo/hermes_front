package com.rishitgoklani.hermes_front_mobile.engine.vrm

import com.rishitgoklani.hermes_front_mobile.engine.vrm.math.Mat4
import com.rishitgoklani.hermes_front_mobile.engine.vrm.math.Quat
import kotlin.math.abs

/**
 * The live skeleton of one model. Poses are written with VRM's "normalized bone" meaning (rotations in the T-pose world frame, so the same
 * numbers work on any model), then [update] computes world matrices. Nothing here allocates per frame.
 *
 * Frames. three-vrm's normalized bones live in the raw glTF frame of the file; VRM 0.x files face -Z there and are turned around only by the
 * scene (rotateVRM0), VRM 1.0 files face +Z. VRMA clips are authored in the VRM 1.0 frame and three-vrm-animation negates x and z for VRM 0.x.
 * The agent contract ("leftUpperArm z=+1.25 is arms down") is the VRM 0.x frame, so agent poses ([setBoneEuler], [setBoneOffset]) are
 * conjugated by a half turn about Y (x and z negated) on VRM 1.0 models and the same pose looks the same on both. Clip data ([setBoneQuat],
 * [setBonePosition]) is in the VRM 1.0 frame and is flipped on VRM 0.x models.
 */
class Rig(val asset: VrmAsset) {
    private val n = asset.nodes.size
    private val lt = FloatArray(n * 3); private val lr = FloatArray(n * 4); private val ls = FloatArray(n * 3)
    private val restT = FloatArray(n * 3); private val restR = FloatArray(n * 4)
    private val restWorldRot = FloatArray(n * 4)
    private val restWorldPos = FloatArray(n * 3)
    private val wq = FloatArray(n * 4)
    val world = FloatArray(n * 16)
    private val order: IntArray
    private val byName = HashMap<String, Int>()
    private val humanoidFlag = BooleanArray(n)
    /** Draw-space matrix of the whole model (VRM 0.x models face -Z and are turned around). */
    val modelMatrix: FloatArray = Mat4.identity()
    private val scaleTmp = FloatArray(3)
    private val tmpA = FloatArray(4); private val tmpB = FloatArray(4); private val tmpM = FloatArray(16); private val tmpV = FloatArray(3)
    private val qa = FloatArray(4); private val qrp = FloatArray(4); private val qrc = FloatArray(4)
    /** Agent poses are in the VRM 0.x frame, so VRM 1.0 models need them flipped. */
    private val flipAgent = !asset.isVrm0
    private var dirty = true

    /** World height of the hips at rest (used to scale clip hips motion to this model). */
    val restHipsHeight: Float

    /** Per glTF mesh, the current morph target weights (0..1). */
    val meshWeights: Map<Int, FloatArray> = asset.meshTargetCounts.mapValues { FloatArray(it.value) }
    val dirtyMeshes = HashSet<Int>()

    // ---- expression tables, built once ----
    private val exprNames: Array<String> = asset.expressions.keys.toTypedArray()
    private val exprIndex = HashMap<String, Int>().also { m -> exprNames.forEachIndexed { i, s -> m[s] = i } }
    private val exprRaw = FloatArray(exprNames.size); private val exprEff = FloatArray(exprNames.size)
    private val exprMeta = Array(exprNames.size) { asset.expressionMeta[exprNames[it]] ?: ExpressionMeta.NONE }
    private val isBlink = BooleanArray(exprNames.size) { exprNames[it] in BLINK }
    private val isLook = BooleanArray(exprNames.size) { exprNames[it] in LOOK }
    private val isMouth = BooleanArray(exprNames.size) { exprNames[it] in MOUTH }
    private val meshIds: IntArray = meshWeights.keys.sorted().toIntArray()
    private val meshW: Array<FloatArray> = Array(meshIds.size) { meshWeights.getValue(meshIds[it]) }
    private val bindExpr: Array<IntArray>; private val bindTarget: Array<IntArray>; private val bindWeight: Array<FloatArray>
    private val scratch = FloatArray((meshW.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1))

    private companion object {
        val BLINK = setOf("blink", "blinkLeft", "blinkRight"); val LOOK = setOf("lookLeft", "lookRight", "lookUp", "lookDown"); val MOUTH = setOf("aa", "ee", "ih", "oh", "ou")
    }

    init {
        val list = ArrayList<Int>(n); val seen = BooleanArray(n)
        fun visit(i: Int) { if (seen[i]) return; seen[i] = true; asset.nodes[i].parent.takeIf { it >= 0 }?.let(::visit); list += i }
        for (i in 0 until n) visit(i); order = list.toIntArray()
        for (nd in asset.nodes) { val i = nd.index; nd.t.copyInto(restT, i * 3); nd.r.copyInto(restR, i * 4); byName.putIfAbsent(nd.name, i) }
        for (v in asset.humanoid.values) if (v in 0 until n) humanoidFlag[v] = true
        for (i in order) {
            val p = asset.nodes[i].parent
            if (p < 0) System.arraycopy(restR, i * 4, restWorldRot, i * 4, 4) else { for (k in 0..3) tmpA[k] = restWorldRot[p * 4 + k]; for (k in 0..3) tmpB[k] = restR[i * 4 + k]; Quat.mul(tmpA, tmpB, tmpA); System.arraycopy(tmpA, 0, restWorldRot, i * 4, 4) }
        }
        val rw = VrmParser.restWorldMatrices(asset.nodes)
        for (i in 0 until n) { restWorldPos[i * 3] = rw[i * 16 + 12]; restWorldPos[i * 3 + 1] = rw[i * 16 + 13]; restWorldPos[i * 3 + 2] = rw[i * 16 + 14] }
        restHipsHeight = asset.humanoid["hips"]?.let { restWorldPos[it * 3 + 1] } ?: 0f
        if (asset.isVrm0) Quat.fromAxisAngle(0f, 1f, 0f, Math.PI.toFloat(), tmpA).also { Mat4.compose(floatArrayOf(0f, 0f, 0f), tmpA, floatArrayOf(1f, 1f, 1f), modelMatrix) }
        // expression -> per-mesh morph bind tables
        val ex = ArrayList<ArrayList<Int>>(); val tg = ArrayList<ArrayList<Int>>(); val wt = ArrayList<ArrayList<Float>>()
        for (m in meshIds.indices) { ex.add(ArrayList()); tg.add(ArrayList()); wt.add(ArrayList()) }
        exprNames.forEachIndexed { e, name ->
            for (b in asset.expressions.getValue(name)) {
                val mi = meshIds.indexOf(b.mesh); if (mi < 0 || b.index !in meshW[mi].indices) continue
                ex[mi].add(e); tg[mi].add(b.index); wt[mi].add(b.weight)
            }
        }
        bindExpr = Array(meshIds.size) { ex[it].toIntArray() }; bindTarget = Array(meshIds.size) { tg[it].toIntArray() }; bindWeight = Array(meshIds.size) { wt[it].toFloatArray() }
        resetPose(); update()
    }

    fun resetPose() {
        System.arraycopy(restT, 0, lt, 0, lt.size); System.arraycopy(restR, 0, lr, 0, lr.size)
        for (i in 0 until n) { ls[i * 3] = asset.nodes[i].s[0]; ls[i * 3 + 1] = asset.nodes[i].s[1]; ls[i * 3 + 2] = asset.nodes[i].s[2] }
        dirty = true
    }

    /** True if any local transform or morph weight changed since the last call (then clears it). The renderer redraws only when this or the spring sim says so. */
    fun consumeDirty(): Boolean { val d = dirty; dirty = false; return d }
    fun markDirty() { dirty = true }

    /** Resolves an agent-supplied name: humanoid bone first, then any node with that name. */
    fun nodeFor(name: String): Int = asset.humanoid[name] ?: byName[name] ?: -1
    fun isHumanoid(node: Int) = node in 0 until n && humanoidFlag[node]

    private fun setLocalRot(node: Int, q: FloatArray) {
        val o = node * 4
        if (abs(lr[o] - q[0]) > 1e-6f || abs(lr[o + 1] - q[1]) > 1e-6f || abs(lr[o + 2] - q[2]) > 1e-6f || abs(lr[o + 3] - q[3]) > 1e-6f) { System.arraycopy(q, 0, lr, o, 4); dirty = true }
    }
    private fun setLocalPos(node: Int, x: Float, y: Float, z: Float) {
        val o = node * 3
        if (abs(lt[o] - x) > 1e-7f || abs(lt[o + 1] - y) > 1e-7f || abs(lt[o + 2] - z) > 1e-7f) { lt[o] = x; lt[o + 1] = y; lt[o + 2] = z; dirty = true }
    }

    /** [qa] holds a rotation in the raw normalized frame; stores local = Rp^-1 * q * Rp * restLocal for [node]. */
    private fun commitNormalized(node: Int) {
        val p = asset.nodes[node].parent
        if (p >= 0) System.arraycopy(restWorldRot, p * 4, qrp, 0, 4) else { qrp[0] = 0f; qrp[1] = 0f; qrp[2] = 0f; qrp[3] = 1f }
        Quat.conjugate(qrp, qrc); Quat.mul(qrc, qa, qa); Quat.mul(qa, qrp, qa)
        for (k in 0..3) qrp[k] = restR[node * 4 + k]; Quat.mul(qa, qrp, qa)
        setLocalRot(node, qa)
    }

    /** Agent pose: normalized-bone rotation (euler XYZ, radians, VRM 0.x frame, see class doc) of [node]. */
    fun setBoneEuler(node: Int, x: Float, y: Float, z: Float) {
        if (node !in 0 until n) return
        Quat.fromEuler(x, y, z, qa)
        if (flipAgent) { qa[0] = -qa[0]; qa[2] = -qa[2] }
        commitNormalized(node)
    }

    /** Clip pose: normalized-bone rotation as a quaternion in the VRM 1.0 frame (VRMA convention); flipped for VRM 0.x models. */
    fun setBoneQuat(node: Int, x: Float, y: Float, z: Float, w: Float) {
        if (node !in 0 until n) return
        qa[0] = if (asset.isVrm0) -x else x; qa[1] = y; qa[2] = if (asset.isVrm0) -z else z; qa[3] = w
        commitNormalized(node)
    }

    /** Moves [node] by a world-aligned offset (raw normalized frame) given in metres; converted into the node's local space. */
    private fun commitOffset(node: Int, wx: Float, wy: Float, wz: Float) {
        val p = asset.nodes[node].parent
        if (p >= 0) { qrp[0] = -restWorldRot[p * 4]; qrp[1] = -restWorldRot[p * 4 + 1]; qrp[2] = -restWorldRot[p * 4 + 2]; qrp[3] = restWorldRot[p * 4 + 3]; Quat.rotate(qrp, wx, wy, wz, tmpV) }
        else { tmpV[0] = wx; tmpV[1] = wy; tmpV[2] = wz }
        setLocalPos(node, restT[node * 3] + tmpV[0], restT[node * 3 + 1] + tmpV[1], restT[node * 3 + 2] + tmpV[2])
    }

    /** Agent pose: offset (metres, VRM 0.x frame) of a humanoid [node] from its rest position. */
    fun setBoneOffset(node: Int, x: Float, y: Float, z: Float) {
        if (node !in 0 until n) return
        if (flipAgent) commitOffset(node, -x, y, -z) else commitOffset(node, x, y, z)
    }

    /** Clip pose: absolute normalized position (VRM 1.0 frame, already scaled to this model) of [node]; flipped for VRM 0.x. */
    fun setBonePosition(node: Int, x: Float, y: Float, z: Float) {
        if (node !in 0 until n) return
        val rx = if (asset.isVrm0) -x else x; val rz = if (asset.isVrm0) -z else z
        commitOffset(node, rx - restWorldPos[node * 3], y - restWorldPos[node * 3 + 1], rz - restWorldPos[node * 3 + 2])
    }

    /** Node rotation as an offset from its own rest rotation (for nodes that are not humanoid bones). */
    fun setNodeEulerOffset(node: Int, x: Float, y: Float, z: Float) {
        if (node !in 0 until n) return
        Quat.fromEuler(x, y, z, tmpB); for (k in 0..3) tmpA[k] = restR[node * 4 + k]; Quat.mul(tmpA, tmpB, tmpA); setLocalRot(node, tmpA)
    }

    fun setNodeTranslationOffset(node: Int, x: Float, y: Float, z: Float) {
        if (node !in 0 until n) return
        setLocalPos(node, restT[node * 3] + x, restT[node * 3 + 1] + y, restT[node * 3 + 2] + z)
    }

    fun restTranslationY(node: Int) = restT[node * 3 + 1]

    /** Computes every world matrix (and world rotation) from the local transforms. */
    fun update() {
        for (i in order) {
            val o = i * 16; val q = tmpA; for (k in 0..3) q[k] = lr[i * 4 + k]
            tmpV[0] = lt[i * 3]; tmpV[1] = lt[i * 3 + 1]; tmpV[2] = lt[i * 3 + 2]
            val p = asset.nodes[i].parent
            if (p < 0) { Mat4.compose(tmpV, q, scaleOf(i), world, o); System.arraycopy(q, 0, wq, i * 4, 4) }
            else {
                Mat4.compose(tmpV, q, scaleOf(i), tmpM); Mat4.mul(world, p * 16, tmpM, 0, world, o)
                for (k in 0..3) tmpB[k] = wq[p * 4 + k]; Quat.mul(tmpB, q, tmpB); System.arraycopy(tmpB, 0, wq, i * 4, 4)
            }
        }
    }
    private fun scaleOf(i: Int): FloatArray { scaleTmp[0] = ls[i * 3]; scaleTmp[1] = ls[i * 3 + 1]; scaleTmp[2] = ls[i * 3 + 2]; return scaleTmp }

    fun worldPosition(node: Int, out: FloatArray) { out[0] = world[node * 16 + 12]; out[1] = world[node * 16 + 13]; out[2] = world[node * 16 + 14] }
    fun worldRotation(node: Int, out: FloatArray) { System.arraycopy(wq, node * 4, out, 0, 4) }
    /** World rotation of [node] in the rest pose; world * inverse(rest) is how far the node has turned from rest. */
    fun restWorldRotation(node: Int, out: FloatArray) { System.arraycopy(restWorldRot, node * 4, out, 0, 4) }

    /** Writes the skinning matrices (world * inverseBind) of skin [skin] into [out], 16 floats per joint. */
    fun jointMatrices(skin: Int, out: FloatArray) {
        val s = asset.skins[skin]
        for (j in s.joints.indices) Mat4.mul(world, s.joints[j] * 16, s.inverseBind, j * 16, out, j * 16)
    }

    // ---- expressions ----
    fun hasExpression(name: String) = exprIndex.containsKey(name)
    /** Index for the allocation-free [setExpression] overload, or -1 when the model has no such expression. */
    fun expressionIndex(name: String): Int = exprIndex[name] ?: -1
    fun setExpression(name: String, value: Float) { val i = exprIndex[name] ?: return; exprRaw[i] = value.coerceIn(0f, 1f) }
    fun setExpression(index: Int, value: Float) { if (index in exprRaw.indices) exprRaw[index] = if (value < 0f) 0f else if (value > 1f) 1f else value }
    fun clearExpressions() { java.util.Arrays.fill(exprRaw, 0f) }
    /** Raises expression [index] to at least [value] (never lowers it), so layers on top do not wipe what the agent or a clip set. */
    fun raiseExpression(index: Int, value: Float) { if (index in exprRaw.indices) { val v = if (value > 1f) 1f else value; if (v > exprRaw[index]) exprRaw[index] = v } }
    /** Every expression name the model has (presets and custom ones). */
    fun expressionNames(): List<String> = exprNames.asList()

    private fun overrideAmount(mode: String, w: Float) = if (mode == "block") (if (w > 0f) 1f else 0f) else if (mode == "blend") w else 0f

    /**
     * Folds the expression values into morph weights; meshes whose weights changed (by more than 4e-3) are listed in [dirtyMeshes]. Follows three-vrm:
     * binary expressions snap to 0/1, and an active expression with overrideBlink/overrideLookAt/overrideMouth ("block" or "blend") scales the
     * blink, look and mouth expressions down.
     */
    fun applyExpressions() {
        var blink = 1f; var look = 1f; var mouth = 1f
        for (e in exprRaw.indices) { val w = exprRaw[e]; if (w <= 0f) continue; val m = exprMeta[e]
            blink -= overrideAmount(m.overrideBlink, w); look -= overrideAmount(m.overrideLookAt, w); mouth -= overrideAmount(m.overrideMouth, w) }
        if (blink < 0f) blink = 0f; if (look < 0f) look = 0f; if (mouth < 0f) mouth = 0f
        for (e in exprRaw.indices) {
            var v = exprRaw[e]; if (exprMeta[e].isBinary) v = if (v <= 0.5f) 0f else 1f
            if (isBlink[e]) v *= blink; if (isLook[e]) v *= look; if (isMouth[e]) v *= mouth
            exprEff[e] = v
        }
        for (mi in meshIds.indices) {
            val w = meshW[mi]; val sc = scratch; for (k in w.indices) sc[k] = 0f
            val be = bindExpr[mi]; val bt = bindTarget[mi]; val bw = bindWeight[mi]
            for (b in be.indices) { val v = exprEff[be[b]]; if (v > 0f) sc[bt[b]] += v * bw[b] }
            var changed = false
            for (k in w.indices) {
                val f = if (sc[k] < 0f) 0f else if (sc[k] > 1f) 1f else sc[k]
                if (abs(f - w[k]) > 4e-3f || (f == 0f && w[k] != 0f)) { w[k] = f; changed = true }
            }
            if (changed) { dirtyMeshes += meshIds[mi]; dirty = true }
        }
    }

    fun nodeNames(): List<String> {
        val out = LinkedHashSet<String>()
        asset.humanoid.keys.forEach { out += it }
        asset.nodes.forEach { nd -> if (nd.name.isNotBlank() && !Regex("^(Scene|Armature|Root)\$", RegexOption.IGNORE_CASE).matches(nd.name) && !nd.name.startsWith("Normalized_")) out += nd.name }
        return out.toList()
    }

    // ---- hooks for spring physics ----
    internal fun localRotation(i: Int, out: FloatArray) { System.arraycopy(lr, i * 4, out, 0, 4) }
    internal fun setLocalRotation(i: Int, q: FloatArray) { setLocalRot(i, q) }
    internal fun restLocalRotation(i: Int, out: FloatArray) { System.arraycopy(restR, i * 4, out, 0, 4) }
    internal fun recomputeNode(i: Int) {
        val p = asset.nodes[i].parent; val q = tmpA; for (k in 0..3) q[k] = lr[i * 4 + k]
        tmpV[0] = lt[i * 3]; tmpV[1] = lt[i * 3 + 1]; tmpV[2] = lt[i * 3 + 2]
        Mat4.compose(tmpV, q, scaleOf(i), tmpM)
        if (p < 0) { System.arraycopy(tmpM, 0, world, i * 16, 16); System.arraycopy(q, 0, wq, i * 4, 4) }
        else { Mat4.mul(world, p * 16, tmpM, 0, world, i * 16); for (k in 0..3) tmpB[k] = wq[p * 4 + k]; Quat.mul(tmpB, q, tmpB); System.arraycopy(tmpB, 0, wq, i * 4, 4) }
    }
}
