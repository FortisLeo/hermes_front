package com.rishitgoklani.hermes_front_mobile.engine.vrm

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Builds a tiny but complete VRM (skinned triangle, one morph target, hair chain) as GLB bytes for tests. */
object MiniVrm {
    private class Bin {
        val out = ByteArrayOutputStream(); val views = ArrayList<String>(); val accessors = ArrayList<String>()
        fun add(data: ByteArray, count: Int, comp: Int, type: String, target: Int? = null, norm: Boolean = false, extra: String = ""): Int {
            while (out.size() % 4 != 0) out.write(0)
            val off = out.size(); out.write(data)
            views += """{"buffer":0,"byteOffset":$off,"byteLength":${data.size}}"""
            accessors += """{"bufferView":${views.size - 1},"componentType":$comp,"count":$count,"type":"$type"${if (norm) ",\"normalized\":true" else ""}$extra}"""
            return accessors.size - 1
        }
    }
    private fun f(vararg v: Float) = ByteBuffer.allocate(v.size * 4).order(ByteOrder.LITTLE_ENDIAN).also { b -> v.forEach { b.putFloat(it) } }.array()
    private fun s(vararg v: Int) = ByteBuffer.allocate(v.size * 2).order(ByteOrder.LITTLE_ENDIAN).also { b -> v.forEach { b.putShort(it.toShort()) } }.array()

    /** [arms] adds leftUpperArm/leftHand/rightUpperArm/rightHand/leftEye/rightEye (nodes 7..12) in a T-pose, the character's left at +X for VRM 1.0 and -X for VRM 0.x.
     *  [expressions1] replaces the VRM 1.0 expression block; [v0Groups] replaces the VRM 0.x blend shape groups; [shade] gives the VRM 0.x _ShadeShift/_ShadeToony. */
    fun build(vrm1: Boolean = false, withSprings: Boolean = true, arms: Boolean = false, expressions1: String? = null, v0Groups: String? = null, shade: Pair<Float, Float>? = null, targets: Int = 1, secondPrimitive: Boolean = false, meshExtras: String = ""): ByteArray {
        val bin = Bin()
        val pos = bin.add(f(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f), 3, 5126, "VEC3")
        val nrm = bin.add(f(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f), 3, 5126, "VEC3")
        val uv = bin.add(f(0f, 0f, 1f, 0f, 0f, 1f), 3, 5126, "VEC2")
        val jts = bin.add(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), 3, 5121, "VEC4")
        val wts = bin.add(f(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f), 3, 5126, "VEC4")
        val idx = bin.add(s(0, 1, 2, 0), 3, 5123, "SCALAR")
        val morph = bin.add(f(0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f), 3, 5126, "VEC3")
        val ib = bin.add(f(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, -1f, 0f, 1f), 1, 5126, "MAT4")
        val side = if (vrm1) 1 else -1
        val armNodes = if (!arms) "" else """,
          {"name":"leftUpperArm","translation":[${0.2f * side},0.1,0],"children":[8]},{"name":"leftHand","translation":[${0.3f * side},0,0]},
          {"name":"rightUpperArm","translation":[${-0.2f * side},0.1,0],"children":[10]},{"name":"rightHand","translation":[${-0.3f * side},0,0]},
          {"name":"leftEye","translation":[${0.03f * side},0.05,${0.08f * side}]},{"name":"rightEye","translation":[${-0.03f * side},0.05,${0.08f * side}]}"""
        val spineKids = if (arms) "[3,7,9]" else "[3]"
        val armBones0 = if (arms) """,{"bone":"leftUpperArm","node":7},{"bone":"leftHand","node":8},{"bone":"rightUpperArm","node":9},{"bone":"rightHand","node":10},{"bone":"leftEye","node":11},{"bone":"rightEye","node":12}""" else ""
        val armBones1 = if (arms) ""","leftUpperArm":{"node":7},"leftHand":{"node":8},"rightUpperArm":{"node":9},"rightHand":{"node":10},"leftEye":{"node":11},"rightEye":{"node":12}""" else ""
        val nodes = """[
          {"name":"Root","children":[1]},
          {"name":"hips","translation":[0,1,0],"children":[2]},
          {"name":"spine","translation":[0,0.3,0],"children":$spineKids},
          {"name":"head","translation":[0,0.4,0],"children":${if (arms) "[4,11,12]" else "[4]"}},
          {"name":"hair","translation":[0,0.1,0],"children":[5]},
          {"name":"hairTip","translation":[0,-0.2,0]},
          {"name":"Body","mesh":0,"skin":0}$armNodes]"""
        val ext = if (!vrm1) """"extensions":{"VRM":{"exporterVersion":"test","humanoid":{"humanBones":[{"bone":"hips","node":1},{"bone":"spine","node":2},{"bone":"head","node":3}$armBones0]},
          "blendShapeMaster":{"blendShapeGroups":[${v0Groups ?: """{"name":"A","presetName":"a","binds":[{"mesh":0,"index":0,"weight":100}]},{"name":"Smirk","presetName":"unknown","binds":[{"mesh":0,"index":0,"weight":50}]}"""}]},
          "materialProperties":[{"shader":"VRM/MToon","floatProperties":{"_BlendMode":0,"_CullMode":2,"_ShadeShift":${shade?.first ?: 0.1f},${if (shade != null) "\"_ShadeToony\":${shade.second}," else ""}"_OutlineWidthMode":1,"_OutlineWidth":0.2},"vectorProperties":{"_Color":[1,0,0,1],"_ShadeColor":[0.5,0,0,1]},"textureProperties":{}}],
          "secondaryAnimation":{"colliderGroups":[],"boneGroups":[${if (withSprings) """{"stiffiness":0.5,"gravityPower":1,"gravityDir":{"x":0,"y":-1,"z":0},"dragForce":0.4,"hitRadius":0.02,"bones":[4],"colliderGroups":[]}""" else ""}]}}}"""
        else """"extensions":{"VRMC_vrm":{"specVersion":"1.0","humanoid":{"humanBones":{"hips":{"node":1},"spine":{"node":2},"head":{"node":3}$armBones1}},
          ${expressions1 ?: """"expressions":{"preset":{"happy":{"morphTargetBinds":[{"node":6,"index":0,"weight":1.0}]}},"custom":{"wink":{"morphTargetBinds":[{"node":6,"index":0,"weight":0.5}]}}}"""}},
          "VRMC_springBone":{"specVersion":"1.0","colliders":[{"node":1,"shape":{"sphere":{"offset":[0,0,0],"radius":0.1}}}],"colliderGroups":[{"colliders":[0]}],
          "springs":[{"joints":[{"node":4,"stiffness":1,"gravityPower":0.5,"dragForce":0.4,"hitRadius":0.01},{"node":5}],"colliderGroups":[0]}]}}"""
        val matExt = if (vrm1) """,{"extensions":{"KHR_materials_mtoon":{"shadeColorFactor":[0.1,0.2,0.3],"shadingShiftFactor":0.2,"outlineWidthMode":"worldCoordinates","outlineWidthFactor":0.003}}}""" else ""
        val prim = """{"attributes":{"POSITION":$pos,"NORMAL":$nrm,"TEXCOORD_0":$uv,"JOINTS_0":$jts,"WEIGHTS_0":$wts},"indices":$idx,"material":0,"targets":[${(1..targets).joinToString(",") { """{"POSITION":$morph}""" }}]}"""
        val json = """{"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":[0,6]}],"nodes":$nodes,
          "meshes":[{"primitives":[$prim${if (secondPrimitive) ",$prim" else ""}]$meshExtras}],
          "skins":[{"joints":[1],"inverseBindMatrices":$ib}],
          "materials":[{"name":"skin","pbrMetallicRoughness":{"baseColorFactor":[1,1,1,1]},"doubleSided":true}${if (vrm1) matExt.substring(0, 0) else ""}],
          "accessors":[${bin.accessors.joinToString(",")}],"bufferViews":[${bin.views.joinToString(",")}],"buffers":[{"byteLength":${bin.out.size()}}],$ext}"""
        return glb(if (vrm1) json.replace("""{"name":"skin","pbrMetallicRoughness":{"baseColorFactor":[1,1,1,1]},"doubleSided":true}""", """{"name":"skin","pbrMetallicRoughness":{"baseColorFactor":[1,1,1,1]},"extensions":{"KHR_materials_mtoon":{"shadeColorFactor":[0.1,0.2,0.3],"shadingShiftFactor":0.2,"outlineWidthMode":"worldCoordinates","outlineWidthFactor":0.003}}}""") else json, bin.out.toByteArray())
    }

    fun glb(json: String, bin: ByteArray): ByteArray {
        fun pad(b: ByteArray, p: Byte) = b + ByteArray((4 - b.size % 4) % 4) { p }
        val j = pad(json.toByteArray(), 0x20); val b = pad(bin, 0)
        val out = ByteBuffer.allocate(12 + 8 + j.size + 8 + b.size).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(0x46546C67).putInt(2).putInt(12 + 8 + j.size + 8 + b.size)
        out.putInt(j.size).putInt(0x4E4F534A).put(j); out.putInt(b.size).putInt(0x004E4942).put(b)
        return out.array()
    }
}
