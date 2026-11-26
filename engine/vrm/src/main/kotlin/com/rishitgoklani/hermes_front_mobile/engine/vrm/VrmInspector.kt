package com.rishitgoklani.hermes_front_mobile.engine.vrm

import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.Glb
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.a
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.f
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.floats
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.i
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.o
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.obj
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.s
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.b
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** A morph-target bind of an expression, with the names the runtime [VrmAsset] drops (it keeps only indexes). */
data class MorphBindInfo(val mesh: Int, val meshName: String, val target: Int, val targetName: String, val weight: Float)

/** An expression that changes a material instead of vertices (VRM 0.x `materialValues`, VRM 1.0 `materialColorBinds` / `textureTransformBinds`). The runtime asset ignores these. */
data class MaterialBindInfo(val material: Int, val materialName: String, val kind: String, val property: String, val value: List<Float>)

data class ExpressionInfo(
    val name: String, val preset: Boolean, val isBinary: Boolean,
    val morphBinds: List<MorphBindInfo>, val materialBinds: List<MaterialBindInfo>,
)

data class MeshInfo(val index: Int, val name: String, val nodes: List<Int>, val primitives: Int, val vertexCount: Int, val materials: List<Int>, val morphTargetNames: List<String>)
data class MaterialInfo(val index: Int, val name: String, val shader: String, val alphaMode: String)

/** A humanoid slot with where its node sits in the rest pose (model space, before the VRM 0.x half turn). */
data class BoneInfo(val humanoid: String, val node: Int, val nodeName: String, val restPosition: List<Float>)

data class LookAtInfo(val type: String, val offsetFromHead: List<Float>?)
data class MetaInfo(val name: String, val authors: List<String>, val license: String, val version: String)

/** One match from [VrmInfo.search]. [kind] is expression, morph, material, mesh or node. */
data class InfoHit(val kind: String, val name: String, val detail: String)

/**
 * Everything worth knowing about a model that the compact [VrmAsset] does not keep: names of meshes, morph targets, materials and
 * expressions, which expressions touch materials, look-at settings and the licence block. Built straight from the glTF JSON.
 */
data class VrmInfo(
    val isVrm0: Boolean, val meta: MetaInfo, val nodeCount: Int,
    val meshes: List<MeshInfo>, val materials: List<MaterialInfo>, val expressions: List<ExpressionInfo>,
    val bones: List<BoneInfo>, val lookAt: LookAtInfo?, val nodeNames: List<String>,
) {
    fun bone(humanoid: String): BoneInfo? = bones.firstOrNull { it.humanoid == humanoid }
    fun expression(name: String): ExpressionInfo? = expressions.firstOrNull { it.name == name }

    /** Case-insensitive name search across expressions, morph targets, materials, meshes and nodes (e.g. "blush", "cheek"). */
    fun search(vararg words: String): List<InfoHit> {
        val w = words.map { it.lowercase() }.filter { it.isNotEmpty() }
        fun hit(s: String) = w.any { it in s.lowercase() }
        val out = ArrayList<InfoHit>()
        for (e in expressions) if (hit(e.name)) out += InfoHit("expression", e.name, "${e.morphBinds.size} morph binds, ${e.materialBinds.size} material binds")
        for (m in meshes) {
            if (hit(m.name)) out += InfoHit("mesh", m.name, "${m.vertexCount} vertices, ${m.morphTargetNames.size} morph targets")
            m.morphTargetNames.forEachIndexed { t, n -> if (hit(n)) out += InfoHit("morph", n, "mesh ${m.index} '${m.name}' target $t") }
        }
        for (m in materials) if (hit(m.name)) out += InfoHit("material", m.name, "material ${m.index}, ${m.alphaMode}")
        for (n in nodeNames) if (hit(n)) out += InfoHit("node", n, "")
        return out
    }

    /** A compact multi-line report for logs and the debug screen. */
    fun summary(): String = buildString {
        append("VRM ${if (isVrm0) "0.x" else "1.0"} '${meta.name}' by ${meta.authors.joinToString().ifBlank { "?" }} licence=${meta.license.ifBlank { "?" }}\n")
        append("nodes=$nodeCount meshes=${meshes.size} materials=${materials.size} expressions=${expressions.size} bones=${bones.size}\n")
        append("lookAt=${lookAt?.type ?: "none"}\n")
        append("expressions: ${expressions.joinToString { e -> "${e.name}${if (e.preset) "" else "*"}[m${e.morphBinds.size}/x${e.materialBinds.size}]" }}\n")
        for (m in meshes) append("mesh ${m.index} '${m.name}' v=${m.vertexCount} targets=${m.morphTargetNames.size}: ${m.morphTargetNames.joinToString()}\n")
    }
}

/** Reads a [VrmInfo] from a parsed GLB. Pure Kotlin; works on the JSON only, so it is cheap and needs no binary data. */
object VrmInspector {
    fun inspect(glb: Glb): VrmInfo {
        val root = glb.json; val ext = root.o("extensions"); val v0 = ext?.o("VRM"); val v1 = ext?.o("VRMC_vrm")
        val nodes = VrmParser.parseNodes(root)
        val rest = VrmParser.restWorldMatrices(nodes)
        val accessors = root.a("accessors")
        val meshNodes = HashMap<Int, MutableList<Int>>(); for (n in nodes) if (n.mesh >= 0) meshNodes.getOrPut(n.mesh) { ArrayList() } += n.index

        val materialsJson = root.a("materials")
        val v0Props = v0?.a("materialProperties")
        val materials = materialsJson.mapIndexed { i, el ->
            val m = el.obj(); val name = m?.s("name") ?: "material$i"
            val shader = v0Props?.firstOrNull { it.obj()?.s("name") == name }?.obj()?.s("shader")
                ?: if (m?.o("extensions")?.containsKey("KHR_materials_mtoon") == true) "MToon" else "glTF"
            MaterialInfo(i, name, shader, m?.s("alphaMode") ?: "OPAQUE")
        }
        fun materialName(i: Int) = materials.getOrNull(i)?.name ?: "material$i"

        val meshes = root.a("meshes").mapIndexed { i, el ->
            val m = el.obj() ?: JsonObject(emptyMap()); val prims = m.a("primitives").mapNotNull { it.obj() }
            val verts = prims.sumOf { p -> p.o("attributes")?.i("POSITION")?.let { accessors.getOrNull(it).obj()?.i("count") } ?: 0 }
            val names = VrmParser.morphTargetNames(root)[i].orEmpty()
            val count = maxOf(names.size, prims.maxOfOrNull { it.a("targets").size } ?: 0)
            MeshInfo(i, m.s("name") ?: "mesh$i", meshNodes[i].orEmpty(), prims.size, verts, prims.mapNotNull { it.i("material") }.distinct(),
                List(count) { t -> names.getOrNull(t)?.takeIf { it.isNotBlank() } ?: "target$t" })
        }
        fun meshOf(i: Int) = meshes.getOrNull(i)
        fun bind(mesh: Int, target: Int, weight: Float) = MorphBindInfo(mesh, meshOf(mesh)?.name ?: "mesh$mesh", target, meshOf(mesh)?.morphTargetNames?.getOrNull(target) ?: "target$target", weight)

        val expressions = ArrayList<ExpressionInfo>()
        v0?.o("blendShapeMaster")?.a("blendShapeGroups")?.forEach { g ->
            val o = g.obj() ?: return@forEach
            val morph = o.a("binds").mapNotNull { b -> val bo = b.obj() ?: return@mapNotNull null; val mesh = bo.i("mesh") ?: return@mapNotNull null; val idx = bo.i("index") ?: return@mapNotNull null
                bind(mesh, idx, (bo.f("weight") ?: 100f) / 100f) }
            val mats = o.a("materialValues").mapNotNull { mv -> val mo = mv.obj() ?: return@mapNotNull null
                val name = mo.s("materialName") ?: return@mapNotNull null; val idx = materials.indexOfFirst { it.name == name }
                MaterialBindInfo(idx, name, "value", mo.s("propertyName") ?: "", mo.a("targetValue").floats().toList()) }
            val preset = o.s("presetName")?.lowercase().let { it != null && it != "unknown" }
            expressions += ExpressionInfo(o.s("name") ?: o.s("presetName") ?: "expression${expressions.size}", preset, o.b("isBinary") ?: false, morph, mats)
        }
        v1?.o("expressions")?.let { ex ->
            fun add(name: String, e: JsonObject, preset: Boolean) {
                val morph = e.a("morphTargetBinds").mapNotNull { b -> val bo = b.obj() ?: return@mapNotNull null; val node = bo.i("node") ?: return@mapNotNull null; val idx = bo.i("index") ?: return@mapNotNull null
                    val mesh = nodes.getOrNull(node)?.mesh ?: -1; if (mesh < 0) null else bind(mesh, idx, bo.f("weight") ?: 1f) }
                val mats = e.a("materialColorBinds").mapNotNull { b -> val bo = b.obj() ?: return@mapNotNull null; val m = bo.i("material") ?: return@mapNotNull null
                    MaterialBindInfo(m, materialName(m), "color", bo.s("type") ?: "", bo.a("targetValue").floats().toList()) } +
                    e.a("textureTransformBinds").mapNotNull { b -> val bo = b.obj() ?: return@mapNotNull null; val m = bo.i("material") ?: return@mapNotNull null
                        MaterialBindInfo(m, materialName(m), "textureTransform", "", bo.a("scale").floats().toList() + bo.a("offset").floats().toList()) }
                expressions += ExpressionInfo(name, preset, e.b("isBinary") ?: false, morph, mats)
            }
            ex.o("preset")?.forEach { (name, e) -> e.obj()?.let { add(name, it, true) } }
            ex.o("custom")?.forEach { (name, e) -> e.obj()?.let { add(name, it, false) } }
        }

        val humanoid = LinkedHashMap<String, Int>()
        v0?.o("humanoid")?.a("humanBones")?.forEach { b -> val o = b.obj() ?: return@forEach; val bone = o.s("bone"); val node = o.i("node"); if (bone != null && node != null) humanoid[bone] = node }
        v1?.o("humanoid")?.o("humanBones")?.forEach { (name, v) -> v.obj()?.i("node")?.let { humanoid[name] = it } }
        val bones = humanoid.mapNotNull { (name, node) ->
            if (node !in nodes.indices) null else BoneInfo(name, node, nodes[node].name, listOf(rest[node * 16 + 12], rest[node * 16 + 13], rest[node * 16 + 14]))
        }

        return VrmInfo(
            isVrm0 = v0 != null, meta = meta(v0, v1), nodeCount = nodes.size, meshes = meshes, materials = materials, expressions = expressions,
            bones = bones, lookAt = lookAt(v0, v1), nodeNames = nodes.map { it.name },
        )
    }

    private fun meta(v0: JsonObject?, v1: JsonObject?): MetaInfo {
        val m1 = v1?.o("meta"); val m0 = v0?.o("meta")
        if (m1 != null) return MetaInfo(m1.s("name") ?: "", m1.a("authors").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }, m1.s("licenseUrl") ?: "", m1.s("version") ?: "")
        return MetaInfo(m0?.s("title") ?: "", listOfNotNull(m0?.s("author")), m0?.s("licenseName") ?: "", m0?.s("version") ?: "")
    }

    private fun lookAt(v0: JsonObject?, v1: JsonObject?): LookAtInfo? {
        v1?.o("lookAt")?.let { return LookAtInfo(it.s("type") ?: "bone", it.a("offsetFromHeadBone").floats().takeIf { o -> o.size == 3 }?.toList()) }
        val fp = v0?.o("firstPerson") ?: return null
        val o = fp.o("firstPersonBoneOffset")?.let { listOf(it.f("x") ?: 0f, it.f("y") ?: 0f, it.f("z") ?: 0f) }
        return LookAtInfo(fp.s("lookAtTypeName")?.lowercase() ?: "bone", o)
    }
}
