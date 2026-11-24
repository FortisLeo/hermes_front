package com.rishitgoklani.hermes_front_mobile.engine.vrm

import com.rishitgoklani.hermes_front_mobile.engine.vrm.math.Mat4
import com.rishitgoklani.hermes_front_mobile.engine.vrm.math.Quat
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Hair, skirt and cloth physics (VRM spring bones) with a Verlet integrator, as in the VRM spec: inertia, stiffness toward the rest pose,
 * gravity, a bone-length constraint and push-out from sphere/capsule colliders. Allocation-free per step.
 */
class SpringSimulation(private val rig: Rig) {
    private val chains = rig.asset.springs
    private val colliders = rig.asset.colliders
    private val cur = chains.map { FloatArray(it.joints.size * 3) }; private val prev = chains.map { FloatArray(it.joints.size * 3) }
    private val boneLen = chains.map { c -> FloatArray(c.joints.size) { i -> val t = c.joints[i].tailLocal; sqrt(t[0] * t[0] + t[1] * t[1] + t[2] * t[2]) } }
    private val axis = chains.map { c -> Array(c.joints.size) { i -> val t = c.joints[i].tailLocal; val l = sqrt(t[0] * t[0] + t[1] * t[1] + t[2] * t[2]); if (l < 1e-6f) floatArrayOf(0f, -1f, 0f) else floatArrayOf(t[0] / l, t[1] / l, t[2] / l) } }
    private var primed = false
    private val v = FloatArray(3); private val head = FloatArray(3); private val next = FloatArray(3); private val c = FloatArray(3); private val c2 = FloatArray(3)
    private val q1 = FloatArray(4); private val q2 = FloatArray(4); private val q3 = FloatArray(4); private val pw = FloatArray(4)
    private val springNodes = chains.flatMap { ch -> ch.joints.map { it.node } }.toSet()
    private val trailing: IntArray = run {
        val out = ArrayList<Int>(); val set = HashSet<Int>()
        fun under(i: Int): Boolean { var p = rig.asset.nodes[i].parent; while (p >= 0) { if (p in springNodes) return true; p = rig.asset.nodes[p].parent }; return false }
        for (i in rig.asset.nodes.indices) if (i !in springNodes && under(i) && set.add(i)) out += i
        out.toIntArray()
    }
    val isEmpty get() = chains.isEmpty()

    private var quietFor = 0f; private var maxMove = 0f

    /**
     * True when no joint has moved more than [SETTLE_DISTANCE] per step for about a second (or there is nothing to simulate). The renderer can then skip
     * [step] and, together with [Rig.consumeDirty], stop redrawing. Any body motion that drags the hair breaks the quiet and clears this again.
     */
    val settled: Boolean get() = chains.isEmpty() || (primed && quietFor >= SETTLE_SECONDS)

    /** Forget all motion (after a model swap or a teleport) so the hair does not whip. */
    fun reset() { primed = false; quietFor = 0f }

    companion object { const val SETTLE_DISTANCE = 5e-5f; const val SETTLE_SECONDS = 1f }

    private fun prime() {
        for ((ci, ch) in chains.withIndex()) for ((ji, j) in ch.joints.withIndex()) {
            Mat4.transformPoint(rig.world, j.node * 16, j.tailLocal[0], j.tailLocal[1], j.tailLocal[2], v)
            for (k in 0..2) { cur[ci][ji * 3 + k] = v[k]; prev[ci][ji * 3 + k] = v[k] }
        }
        primed = true
    }

    /** Call after [Rig.update]. Advances the simulation by [dt] seconds and rewrites the affected world matrices. */
    fun step(dt: Float) {
        if (chains.isEmpty()) return
        if (!primed) prime()
        val d = dt.coerceIn(0.001f, 1f / 20f)
        maxMove = 0f
        for (ci in chains.indices) {
            val ch = chains[ci]
            for (ji in ch.joints.indices) {
                val j = ch.joints[ji]
                val node = j.node; val parent = rig.asset.nodes[node].parent
                rig.recomputeNode(node)
                rig.worldPosition(node, head)
                if (parent >= 0) rig.worldRotation(parent, pw) else { pw[0] = 0f; pw[1] = 0f; pw[2] = 0f; pw[3] = 1f }
                rig.restLocalRotation(node, q1)
                Quat.mul(pw, q1, q2)                          // q2 = parentWorldRot * restLocalRot
                val a = axis[ci][ji]
                Quat.rotate(q2, a[0], a[1], a[2], v)          // rest direction in world space
                val cu = cur[ci]; val pr = prev[ci]; val o = ji * 3
                val drag = 1f - j.dragForce.coerceIn(0f, 1f)
                for (k in 0..2) next[k] = cu[o + k] + (cu[o + k] - pr[o + k]) * drag + v[k] * j.stiffness * d + j.gravityDir[k] * j.gravityPower * d
                constrain(next, head, boneLen[ci][ji])
                for (col in ch.colliders) { if (collide(colliders[col], j.hitRadius, next)) constrain(next, head, boneLen[ci][ji]) }
                for (k in 0..2) { val mv = abs(next[k] - cu[o + k]); if (mv > maxMove) maxMove = mv; pr[o + k] = cu[o + k]; cu[o + k] = next[k] }
                // rotate the node so its axis points at the new tail
                for (k in 0..2) v[k] = next[k] - head[k]
                Quat.conjugate(q2, q3)
                Quat.rotate(q3, v[0], v[1], v[2], v)
                val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]); if (l < 1e-8f) continue
                fromUnitVectors(a, v[0] / l, v[1] / l, v[2] / l, q3)
                Quat.mul(q1, q3, q1)
                rig.setLocalRotation(node, q1); rig.recomputeNode(node)
            }
        }
        for (i in trailing) rig.recomputeNode(i)
        if (maxMove < SETTLE_DISTANCE) quietFor += dt.coerceAtLeast(0f) else quietFor = 0f
    }

    private fun constrain(p: FloatArray, head: FloatArray, len: Float) {
        var dx = p[0] - head[0]; var dy = p[1] - head[1]; var dz = p[2] - head[2]; val l = sqrt(dx * dx + dy * dy + dz * dz)
        if (l < 1e-8f) return; val k = len / l; dx *= k; dy *= k; dz *= k; p[0] = head[0] + dx; p[1] = head[1] + dy; p[2] = head[2] + dz
    }

    /** Pushes [p] out of the collider; returns true if it moved. */
    private fun collide(col: SphereCollider, hit: Float, p: FloatArray): Boolean {
        Mat4.transformPoint(rig.world, col.node * 16, col.offset[0], col.offset[1], col.offset[2], c)
        if (col.tail != null) {
            Mat4.transformPoint(rig.world, col.node * 16, col.tail[0], col.tail[1], col.tail[2], c2)
            val ex = c2[0] - c[0]; val ey = c2[1] - c[1]; val ez = c2[2] - c[2]; val el = ex * ex + ey * ey + ez * ez
            if (el > 1e-12f) { val t = (((p[0] - c[0]) * ex + (p[1] - c[1]) * ey + (p[2] - c[2]) * ez) / el).coerceIn(0f, 1f); c[0] += ex * t; c[1] += ey * t; c[2] += ez * t }
        }
        val dx = p[0] - c[0]; val dy = p[1] - c[1]; val dz = p[2] - c[2]; val dist = sqrt(dx * dx + dy * dy + dz * dz); val min = col.radius + hit
        if (dist >= min || dist < 1e-8f) return false
        val k = (min - dist) / dist; p[0] += dx * k; p[1] += dy * k; p[2] += dz * k; return true
    }

    private fun fromUnitVectors(a: FloatArray, bx: Float, by: Float, bz: Float, out: FloatArray) {
        val r = a[0] * bx + a[1] * by + a[2] * bz + 1f
        if (r < 1e-6f) { if (kotlin.math.abs(a[0]) > kotlin.math.abs(a[2])) { out[0] = -a[1]; out[1] = a[0]; out[2] = 0f; out[3] = 0f } else { out[0] = 0f; out[1] = -a[2]; out[2] = a[1]; out[3] = 0f } }
        else { out[0] = a[1] * bz - a[2] * by; out[1] = a[2] * bx - a[0] * bz; out[2] = a[0] * by - a[1] * bx; out[3] = r }
        Quat.normalize(out)
    }
}
