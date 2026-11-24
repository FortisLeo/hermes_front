package com.rishitgoklani.hermes_front_mobile.engine.vrm

import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.a
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.f
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.i
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.ints
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.o
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.obj
import kotlinx.serialization.json.JsonObject
import kotlin.math.sqrt

/** Reads hair/cloth physics settings from VRM 0.x (secondaryAnimation) and VRM 1.0 (VRMC_springBone) into one shape. */
object SpringParser {
    private fun vec(o: JsonObject?, flipZ: Boolean = false): FloatArray = floatArrayOf(o?.f("x") ?: 0f, o?.f("y") ?: 0f, (o?.f("z") ?: 0f) * (if (flipZ) -1f else 1f))

    /** Tip of [node] in its local space: first child's rest position, else a short stub continuing away from the parent. */
    private fun tailOf(node: VrmNode, nodes: List<VrmNode>): FloatArray {
        nodes.getOrNull(node.children.firstOrNull() ?: -1)?.let { return it.t.copyOf() }
        val l = sqrt(node.t[0] * node.t[0] + node.t[1] * node.t[1] + node.t[2] * node.t[2])
        return if (l < 1e-6f) floatArrayOf(0f, -0.07f, 0f) else floatArrayOf(node.t[0] / l * 0.07f, node.t[1] / l * 0.07f, node.t[2] / l * 0.07f)
    }

    fun parse(v0: JsonObject?, v1: JsonObject?, nodes: List<VrmNode>): Pair<List<SpringChain>, List<SphereCollider>> {
        val chains = ArrayList<SpringChain>(); val colliders = ArrayList<SphereCollider>()
        if (v0 != null) {
            val sec = v0.o("secondaryAnimation") ?: return chains to colliders
            val groupStart = ArrayList<IntArray>()
            sec.a("colliderGroups").forEach { g -> val o = g.obj(); val node = o?.i("node") ?: -1; val first = colliders.size
                o?.a("colliders")?.forEach { c -> val co = c.obj(); if (node in nodes.indices) colliders += SphereCollider(node, vec(co?.o("offset"), flipZ = true), co?.f("radius") ?: 0f) }
                groupStart += IntArray(colliders.size - first) { first + it } }
            sec.a("boneGroups").forEach { g ->
                val o = g.obj() ?: return@forEach
                val stiffness = o.f("stiffiness") ?: o.f("stiffness") ?: 1f; val power = o.f("gravityPower") ?: 0f; val dir = o.o("gravityDir")?.let { vec(it, flipZ = true) } ?: floatArrayOf(0f, -1f, 0f)
                val drag = o.f("dragForce") ?: 0.4f; val hit = o.f("hitRadius") ?: 0.02f; val center = o.i("center") ?: -1
                val cols = o.a("colliderGroups").ints().flatMap { gi -> groupStart.getOrNull(gi)?.toList().orEmpty() }.toIntArray()
                o.a("bones").ints().forEach { rootNode ->
                    val joints = ArrayList<SpringJoint>()
                    fun walk(i: Int) { val n = nodes.getOrNull(i) ?: return; joints += SpringJoint(i, tailOf(n, nodes), stiffness, power, dir, drag, hit); n.children.forEach(::walk) }
                    walk(rootNode); if (joints.isNotEmpty()) chains += SpringChain(joints, center, cols)
                }
            }
        }
        return chains to colliders
    }

    /** VRM 1.0: the spring extension sits next to VRMC_vrm in the glTF extensions. */
    fun parseV1(ext: JsonObject?, nodes: List<VrmNode>): Pair<List<SpringChain>, List<SphereCollider>> {
        val chains = ArrayList<SpringChain>(); val colliders = ArrayList<SphereCollider>()
        val sp = ext?.o("VRMC_springBone") ?: return chains to colliders
        sp.a("colliders").forEach { c ->
            val o = c.obj(); val node = o?.i("node") ?: -1; val shape = o?.o("shape"); if (node !in nodes.indices) { colliders += SphereCollider(0, floatArrayOf(0f, 0f, 0f), 0f); return@forEach }
            val sph = shape?.o("sphere"); val cap = shape?.o("capsule")
            fun v3(a: kotlinx.serialization.json.JsonArray?) = a?.let { arr -> FloatArray(3) { (arr.getOrNull(it) as? kotlinx.serialization.json.JsonPrimitive)?.content?.toFloatOrNull() ?: 0f } } ?: floatArrayOf(0f, 0f, 0f)
            colliders += if (cap != null) SphereCollider(node, v3(cap["offset"] as? kotlinx.serialization.json.JsonArray), cap.f("radius") ?: 0f, v3(cap["tail"] as? kotlinx.serialization.json.JsonArray))
            else SphereCollider(node, v3(sph?.get("offset") as? kotlinx.serialization.json.JsonArray), sph?.f("radius") ?: 0f)
        }
        val groups = sp.a("colliderGroups").map { g -> g.obj()?.a("colliders")?.ints() ?: IntArray(0) }
        sp.a("springs").forEach { s ->
            val o = s.obj() ?: return@forEach
            val cols = o.a("colliderGroups").ints().flatMap { gi -> groups.getOrNull(gi)?.toList().orEmpty() }.toIntArray()
            val list = o.a("joints").mapNotNull { j -> j.obj() }
            val joints = list.mapIndexedNotNull { idx, j ->
                val node = j.i("node") ?: return@mapIndexedNotNull null; val n = nodes.getOrNull(node) ?: return@mapIndexedNotNull null
                val next = list.getOrNull(idx + 1)?.i("node")?.let { nodes.getOrNull(it) }
                val tail = if (next != null && next.parent == node) next.t.copyOf() else tailOf(n, nodes)
                val gd = (j["gravityDir"] as? kotlinx.serialization.json.JsonArray)?.let { a -> FloatArray(3) { (a.getOrNull(it) as? kotlinx.serialization.json.JsonPrimitive)?.content?.toFloatOrNull() ?: 0f } } ?: floatArrayOf(0f, -1f, 0f)
                SpringJoint(node, tail, j.f("stiffness") ?: 1f, j.f("gravityPower") ?: 0f, gd, j.f("dragForce") ?: 0.5f, j.f("hitRadius") ?: 0f)
            }
            if (joints.isNotEmpty()) chains += SpringChain(joints, o.i("center") ?: -1, cols)
        }
        return chains to colliders
    }
}
