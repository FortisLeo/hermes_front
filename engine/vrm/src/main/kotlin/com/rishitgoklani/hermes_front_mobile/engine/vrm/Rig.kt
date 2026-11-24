package com.rishitgoklani.hermes_front_mobile.engine.vrm

import com.rishitgoklani.hermes_front_mobile.engine.vrm.math.Mat4
import com.rishitgoklani.hermes_front_mobile.engine.vrm.math.Quat

/**
 * The live skeleton of one model. Poses are written with VRM's "normalized bone" meaning (rotations in the T-pose world frame, so the same
 * numbers work on any model), then [update] computes world matrices. Nothing here allocates per frame.
 */
class Rig(val asset: VrmAsset) {
    private val n = asset.nodes.size
    private val lt = FloatArray(n * 3); private val lr = FloatArray(n * 4); private val ls = FloatArray(n * 3)
    private val restT = FloatArray(n * 3); private val restR = FloatArray(n * 4)
    private val restWorldRot = FloatArray(n * 4)
    private val wq = FloatArray(n * 4)
    val world = FloatArray(n * 16)
    private val order: IntArray
    private val byName = HashMap<String, Int>()
    /** Draw-space matrix of the whole model (VRM 0.x models face -Z and are turned around). */
    val modelMatrix: FloatArray = Mat4.identity()
    private val scaleTmp = FloatArray(3)
    private val tmpA = FloatArray(4); private val tmpB = FloatArray(4); private val tmpM = FloatArray(16); private val tmpV = FloatArray(3)

    /** Per glTF mesh, the current morph target weights (0..1). */
    val meshWeights: Map<Int, FloatArray> = asset.meshTargetCounts.mapValues { FloatArray(it.value) }
    private val exprValues = HashMap<String, Float>()
    val dirtyMeshes = HashSet<Int>()

    init {
        val list = ArrayList<Int>(n); val seen = BooleanArray(n)
        fun visit(i: Int) { if (seen[i]) return; seen[i] = true; asset.nodes[i].parent.takeIf { it >= 0 }?.let(::visit); list += i }
        for (i in 0 until n) visit(i); order = list.toIntArray()
        for (nd in asset.nodes) { val i = nd.index; nd.t.copyInto(restT, i * 3); nd.r.copyInto(restR, i * 4); byName.putIfAbsent(nd.name, i) }
        for (i in order) {
            val p = asset.nodes[i].parent
            if (p < 0) System.arraycopy(restR, i * 4, restWorldRot, i * 4, 4) else { for (k in 0..3) tmpA[k] = restWorldRot[p * 4 + k]; for (k in 0..3) tmpB[k] = restR[i * 4 + k]; Quat.mul(tmpA, tmpB, tmpA); System.arraycopy(tmpA, 0, restWorldRot, i * 4, 4) }
        }
        if (asset.isVrm0) Quat.fromAxisAngle(0f, 1f, 0f, Math.PI.toFloat(), tmpA).also { Mat4.compose(floatArrayOf(0f, 0f, 0f), tmpA, floatArrayOf(1f, 1f, 1f), modelMatrix) }
        resetPose(); update()
    }

    fun resetPose() {
        System.arraycopy(restT, 0, lt, 0, lt.size); System.arraycopy(restR, 0, lr, 0, lr.size)
        for (i in 0 until n) { ls[i * 3] = asset.nodes[i].s[0]; ls[i * 3 + 1] = asset.nodes[i].s[1]; ls[i * 3 + 2] = asset.nodes[i].s[2] }
    }

    /** Resolves an agent-supplied name: humanoid bone first, then any node with that name. */
    fun nodeFor(name: String): Int = asset.humanoid[name] ?: byName[name] ?: -1
    fun isHumanoid(node: Int) = asset.humanoid.containsValue(node)

    /** Normalized-bone rotation (euler XYZ, radians) of [node]; see class doc. local = Rp^-1 * q * Rp * restLocal. */
    fun setBoneEuler(node: Int, x: Float, y: Float, z: Float) {
        if (node !in 0 until n) return
        val q = FloatArray(4); val rp = FloatArray(4); val rpc = FloatArray(4)
        Quat.fromEuler(x, y, z, q)
        val p = asset.nodes[node].parent
        if (p >= 0) { System.arraycopy(restWorldRot, p * 4, rp, 0, 4) } else { rp[3] = 1f }
        Quat.conjugate(rp, rpc); Quat.mul(rpc, q, q); Quat.mul(q, rp, q)
        System.arraycopy(restR, node * 4, rp, 0, 4); Quat.mul(q, rp, q)
        System.arraycopy(q, 0, lr, node * 4, 4)
    }

    /** Node rotation as an offset from its own rest rotation (for nodes that are not humanoid bones). */
    fun setNodeEulerOffset(node: Int, x: Float, y: Float, z: Float) {
        if (node !in 0 until n) return
        Quat.fromEuler(x, y, z, tmpB); for (k in 0..3) tmpA[k] = restR[node * 4 + k]; Quat.mul(tmpA, tmpB, tmpA); System.arraycopy(tmpA, 0, lr, node * 4, 4)
    }

    fun setNodeTranslationOffset(node: Int, x: Float, y: Float, z: Float) {
        if (node !in 0 until n) return
        lt[node * 3] = restT[node * 3] + x; lt[node * 3 + 1] = restT[node * 3 + 1] + y; lt[node * 3 + 2] = restT[node * 3 + 2] + z
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

    /** Writes the skinning matrices (world * inverseBind) of skin [skin] into [out], 16 floats per joint. */
    fun jointMatrices(skin: Int, out: FloatArray) {
        val s = asset.skins[skin]
        for (j in s.joints.indices) Mat4.mul(world, s.joints[j] * 16, s.inverseBind, j * 16, out, j * 16)
    }

    // ---- expressions ----
    fun hasExpression(name: String) = asset.expressions.containsKey(name)
    fun setExpression(name: String, value: Float) { if (asset.expressions.containsKey(name)) exprValues[name] = value.coerceIn(0f, 1f) }
    fun clearExpressions() { exprValues.clear() }

    /** Folds the expression values into morph weights; meshes whose weights changed are listed in [dirtyMeshes]. */
    fun applyExpressions() {
        for ((mesh, w) in meshWeights) {
            val fresh = FloatArray(w.size)
            for ((name, v) in exprValues) if (v > 0f) asset.expressions[name]?.forEach { b -> if (b.mesh == mesh && b.index in fresh.indices) fresh[b.index] += v * b.weight }
            var changed = false
            for (k in w.indices) { val f = fresh[k].coerceIn(0f, 1f); if (kotlin.math.abs(f - w[k]) > 1e-3f) { w[k] = f; changed = true } }
            if (changed) dirtyMeshes += mesh
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
    internal fun setLocalRotation(i: Int, q: FloatArray) { System.arraycopy(q, 0, lr, i * 4, 4) }
    internal fun restLocalRotation(i: Int, out: FloatArray) { System.arraycopy(restR, i * 4, out, 0, 4) }
    internal fun recomputeNode(i: Int) {
        val p = asset.nodes[i].parent; val q = tmpA; for (k in 0..3) q[k] = lr[i * 4 + k]
        tmpV[0] = lt[i * 3]; tmpV[1] = lt[i * 3 + 1]; tmpV[2] = lt[i * 3 + 2]
        Mat4.compose(tmpV, q, scaleOf(i), tmpM)
        if (p < 0) { System.arraycopy(tmpM, 0, world, i * 16, 16); System.arraycopy(q, 0, wq, i * 4, 4) }
        else { Mat4.mul(world, p * 16, tmpM, 0, world, i * 16); for (k in 0..3) tmpB[k] = wq[p * 4 + k]; Quat.mul(tmpB, q, tmpB); System.arraycopy(tmpB, 0, wq, i * 4, 4) }
    }
}
