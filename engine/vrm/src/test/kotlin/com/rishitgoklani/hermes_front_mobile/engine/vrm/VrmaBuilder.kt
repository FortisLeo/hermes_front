package com.rishitgoklani.hermes_front_mobile.engine.vrm

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Builds a tiny synthetic .vrma: Root > hips(0,1,0) > spine(0,0.3,0) > head(0,0.4,0), plus an expression node. Animation 0 has
 * a spine rotation (identity -> [spineAngle] about Z over one second), a hips translation (rest y -> y + [hipsRise]) and an `aa` weight 0 -> 1.
 * [clipHipsY] is the hips rest height inside the clip, so tests can check the scaling to the model's hips height.
 */
object VrmaBuilder {
    private fun f(vararg v: Float) = ByteBuffer.allocate(v.size * 4).order(ByteOrder.LITTLE_ENDIAN).also { b -> v.forEach { b.putFloat(it) } }.array()

    fun build(spineAngle: Float = Math.PI.toFloat() / 2, hipsRise: Float = 0.5f, clipHipsY: Float = 1f, withExpression: Boolean = true, withHips: Boolean = true): ByteArray {
        val out = ByteArrayOutputStream(); val views = ArrayList<String>(); val accessors = ArrayList<String>()
        fun add(data: ByteArray, count: Int, type: String): Int {
            val off = out.size(); out.write(data)
            views += """{"buffer":0,"byteOffset":$off,"byteLength":${data.size}}"""
            accessors += """{"bufferView":${views.size - 1},"componentType":5126,"count":$count,"type":"$type"}"""
            return accessors.size - 1
        }
        val times = add(f(0f, 1f), 2, "SCALAR")
        val half = Math.sin(spineAngle / 2.0).toFloat(); val cosH = Math.cos(spineAngle / 2.0).toFloat()
        val rot = add(f(0f, 0f, 0f, 1f, 0f, 0f, half, cosH), 2, "VEC4")
        val pos = add(f(0f, clipHipsY, 0f, 0f, clipHipsY + hipsRise, 0f), 2, "VEC3")
        val expr = add(f(0f, 0f, 0f, 1f, 0f, 0f), 2, "VEC3")
        val channels = ArrayList<String>(); val samplers = ArrayList<String>()
        fun chan(node: Int, path: String, output: Int) { samplers += """{"input":$times,"output":$output,"interpolation":"LINEAR"}"""; channels += """{"sampler":${samplers.size - 1},"target":{"node":$node,"path":"$path"}}""" }
        chan(2, "rotation", rot)
        if (withHips) chan(1, "translation", pos)
        if (withExpression) chan(4, "translation", expr)
        val json = """{"asset":{"version":"2.0"},"extensionsUsed":["VRMC_vrm_animation"],"scene":0,"scenes":[{"nodes":[0,4]}],
          "nodes":[{"name":"Root","children":[1]},{"name":"hips","translation":[0,$clipHipsY,0],"children":[2]},{"name":"spine","translation":[0,0.3,0],"children":[3]},
                   {"name":"head","translation":[0,0.4,0]},{"name":"aa"}],
          "animations":[{"channels":[${channels.joinToString(",")}],"samplers":[${samplers.joinToString(",")}]}],
          "accessors":[${accessors.joinToString(",")}],"bufferViews":[${views.joinToString(",")}],"buffers":[{"byteLength":${out.size()}}],
          "extensions":{"VRMC_vrm_animation":{"specVersion":"1.0","humanoid":{"humanBones":{"hips":{"node":1},"spine":{"node":2},"head":{"node":3}}},
            "expressions":{"preset":{"aa":{"node":4}}}}}}"""
        return MiniVrm.glb(json, out.toByteArray())
    }
}
