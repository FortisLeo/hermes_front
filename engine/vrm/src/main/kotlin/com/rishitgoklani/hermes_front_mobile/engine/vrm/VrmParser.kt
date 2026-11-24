package com.rishitgoklani.hermes_front_mobile.engine.vrm

import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.Accessors
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.Glb
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.Half
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.a
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.arr
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.b
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.f
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.floats
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.i
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.ints
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.o
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.obj
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.s
import com.rishitgoklani.hermes_front_mobile.engine.vrm.math.Mat4
import com.rishitgoklani.hermes_front_mobile.engine.vrm.math.Quat
import kotlinx.serialization.json.JsonObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class VrmFormatException(message: String) : Exception(message)

/** Turns a GLB/VRM into a compact [VrmAsset]. Pure Kotlin so the whole path is unit-tested on the JVM. */
object VrmParser {
    const val MAX_JOINTS = 255

    fun parse(glb: Glb): VrmAsset {
        val root = glb.json; val acc = Accessors(root, glb.bin)
        val ext = root.o("extensions"); val v0 = ext?.o("VRM"); val v1 = ext?.o("VRMC_vrm")
        if (v0 == null && v1 == null) throw VrmFormatException("Not a VRM model (no VRM extension)")
        val nodes = parseNodes(root)
        val roots = sceneRoots(root, nodes)
        val skins = root.a("skins").map { sk -> val s = sk.obj()!!; val joints = s.a("joints").ints(); if (joints.size > MAX_JOINTS) throw VrmFormatException("Skin has too many joints (${joints.size})")
            val ib = s.i("inverseBindMatrices")?.let { acc.floats(it) } ?: FloatArray(joints.size * 16).also { a -> for (j in joints.indices) Mat4.setIdentity(a, j * 16) }
            VrmSkin(joints, ib) }
        val images = root.a("images").map { im -> val o = im.obj(); ImageRef(o?.s("mimeType") ?: "image/png", o?.i("bufferView") ?: -1) }
        val materials = parseMaterials(root, v0, images.size)
        val bMin = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE); val bMax = floatArrayOf(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        val world = restWorldMatrices(nodes)
        val prims = ArrayList<VrmPrimitive>(); val targetCounts = HashMap<Int, Int>()
        for (n in nodes) if (n.mesh >= 0) {
            val mesh = root.a("meshes").getOrNull(n.mesh).obj() ?: continue
            for (p in mesh.a("primitives")) {
                val prim = parsePrimitive(p.obj() ?: continue, n, acc, materials.size, skins) ?: continue
                prims += prim; targetCounts[n.mesh] = max(targetCounts[n.mesh] ?: 0, prim.morphTargets.size)
                accumulateBounds(prim, if (prim.skinned) null else world, n.index, bMin, bMax)
            }
        }
        if (prims.isEmpty()) throw VrmFormatException("Model has no triangle meshes")
        val humanoid = parseHumanoid(v0, v1)
        if (humanoid["hips"] == null || humanoid["head"] == null) throw VrmFormatException("Model is missing the hips or head bone")
        val meta = LinkedHashMap<String, ExpressionMeta>()
        val expressions = parseExpressions(root, v0, v1, nodes, meta)
        val (chains, colliders) = if (v0 != null) SpringParser.parse(v0, v1, nodes) else SpringParser.parseV1(ext, nodes)
        return VrmAsset(v0 != null, nodes, roots, prims, materials, skins, humanoid, expressions, targetCounts, images, chains, colliders, bMin, bMax, meta) { view -> acc.viewBytes(view) }
    }

    // ---- nodes ----
    internal fun parseNodes(root: JsonObject): List<VrmNode> {
        val raw = root.a("nodes"); val parent = IntArray(raw.size) { -1 }
        raw.forEachIndexed { i, n -> n.obj()?.a("children")?.ints()?.forEach { c -> if (c in parent.indices) parent[c] = i } }
        return raw.mapIndexed { i, el ->
            val n = el.obj() ?: JsonObject(emptyMap()); val t = floatArrayOf(0f, 0f, 0f); val r = Quat.identity(); val s = floatArrayOf(1f, 1f, 1f)
            val m = n.a("matrix").floats()
            if (m.size == 16) decompose(m, t, r, s) else {
                n.a("translation").floats().takeIf { it.size == 3 }?.copyInto(t); n.a("rotation").floats().takeIf { it.size == 4 }?.copyInto(r); n.a("scale").floats().takeIf { it.size == 3 }?.copyInto(s)
                Quat.normalize(r)
            }
            VrmNode(i, n.s("name") ?: "node$i", parent[i], n.a("children").ints(), t, r, s, n.i("mesh") ?: -1, n.i("skin") ?: -1)
        }
    }

    private fun sceneRoots(root: JsonObject, nodes: List<VrmNode>): IntArray {
        val scenes = root.a("scenes"); val sc = scenes.getOrNull(root.i("scene") ?: 0).obj()
        val declared = sc?.a("nodes")?.ints()?.filter { it in nodes.indices }
        return (declared?.takeIf { it.isNotEmpty() } ?: nodes.filter { it.parent < 0 }.map { it.index }).toIntArray()
    }

    internal fun decompose(m: FloatArray, t: FloatArray, q: FloatArray, s: FloatArray) {
        t[0] = m[12]; t[1] = m[13]; t[2] = m[14]
        s[0] = sqrt(m[0] * m[0] + m[1] * m[1] + m[2] * m[2]); s[1] = sqrt(m[4] * m[4] + m[5] * m[5] + m[6] * m[6]); s[2] = sqrt(m[8] * m[8] + m[9] * m[9] + m[10] * m[10])
        val a = if (s[0] == 0f) 1f else s[0]; val b = if (s[1] == 0f) 1f else s[1]; val c = if (s[2] == 0f) 1f else s[2]
        val r00 = m[0] / a; val r10 = m[1] / a; val r20 = m[2] / a; val r01 = m[4] / b; val r11 = m[5] / b; val r21 = m[6] / b; val r02 = m[8] / c; val r12 = m[9] / c; val r22 = m[10] / c
        val tr = r00 + r11 + r22
        if (tr > 0) { val k = sqrt(tr + 1f) * 2; q[3] = k / 4; q[0] = (r21 - r12) / k; q[1] = (r02 - r20) / k; q[2] = (r10 - r01) / k }
        else if (r00 > r11 && r00 > r22) { val k = sqrt(1f + r00 - r11 - r22) * 2; q[3] = (r21 - r12) / k; q[0] = k / 4; q[1] = (r01 + r10) / k; q[2] = (r02 + r20) / k }
        else if (r11 > r22) { val k = sqrt(1f + r11 - r00 - r22) * 2; q[3] = (r02 - r20) / k; q[0] = (r01 + r10) / k; q[1] = k / 4; q[2] = (r12 + r21) / k }
        else { val k = sqrt(1f + r22 - r00 - r11) * 2; q[3] = (r10 - r01) / k; q[0] = (r02 + r20) / k; q[1] = (r12 + r21) / k; q[2] = k / 4 }
        Quat.normalize(q)
    }

    /** World matrices of the rest pose, nodes in any order (parents resolved recursively). */
    fun restWorldMatrices(nodes: List<VrmNode>): FloatArray {
        val w = FloatArray(nodes.size * 16); val done = BooleanArray(nodes.size); val local = FloatArray(16)
        fun solve(i: Int) {
            if (done[i]) return; val n = nodes[i]; Mat4.compose(n.t, n.r, n.s, local)
            if (n.parent < 0) System.arraycopy(local, 0, w, i * 16, 16) else { solve(n.parent); Mat4.mul(w, n.parent * 16, local, 0, w, i * 16) }
            done[i] = true
        }
        for (i in nodes.indices) solve(i)
        return w
    }

    private fun accumulateBounds(p: VrmPrimitive, world: FloatArray?, node: Int, mn: FloatArray, mx: FloatArray) {
        val pos = p.positions.duplicate().order(ByteOrder.LITTLE_ENDIAN); val out = FloatArray(3)
        for (v in 0 until p.vertexCount) {
            val x = pos.getFloat(v * 12); val y = pos.getFloat(v * 12 + 4); val z = pos.getFloat(v * 12 + 8)
            if (world != null) Mat4.transformPoint(world, node * 16, x, y, z, out) else { out[0] = x; out[1] = y; out[2] = z }
            for (k in 0..2) { mn[k] = min(mn[k], out[k]); mx[k] = max(mx[k], out[k]) }
        }
    }

    // ---- geometry ----
    private fun parsePrimitive(p: JsonObject, node: VrmNode, acc: Accessors, materialCount: Int, skins: List<VrmSkin>): VrmPrimitive? {
        if ((p.i("mode") ?: 4) != 4) return null
        val attrs = p.o("attributes") ?: return null; val posAcc = attrs.i("POSITION") ?: return null
        val pos = acc.floats(posAcc); val n = pos.size / 3; if (n == 0) return null
        val skinned = node.skin >= 0 && attrs.i("JOINTS_0") != null && attrs.i("WEIGHTS_0") != null
        if (skinned && node.skin !in skins.indices) return null
        val idx = p.i("indices")?.let { acc.ints(it) } ?: IntArray(n) { it }
        if (idx.size < 3 || idx.any { it < 0 || it >= n }) return null
        val stride = if (skinned) 16 else 8
        val a = ByteBuffer.allocateDirect(n * stride).order(ByteOrder.LITTLE_ENDIAN)
        val normals = attrs.i("NORMAL")?.let { acc.floats(it) }; val uvs = attrs.i("TEXCOORD_0")?.let { acc.floats(it) }
        val joints = if (skinned) acc.ints(attrs.i("JOINTS_0")!!) else null; val weights = if (skinned) acc.floats(attrs.i("WEIGHTS_0")!!) else null
        for (v in 0 until n) {
            val o = v * stride
            val nx = normals?.get(v * 3) ?: 0f; val ny = normals?.get(v * 3 + 1) ?: 1f; val nz = normals?.get(v * 3 + 2) ?: 0f
            a.put(o, snorm(nx)); a.put(o + 1, snorm(ny)); a.put(o + 2, snorm(nz)); a.put(o + 3, 0)
            a.putShort(o + 4, Half.fromFloat(uvs?.get(v * 2) ?: 0f)); a.putShort(o + 6, Half.fromFloat(uvs?.get(v * 2 + 1) ?: 0f))
            if (skinned) {
                val w = floatArrayOf(weights!![v * 4], weights[v * 4 + 1], weights[v * 4 + 2], weights[v * 4 + 3]); val sum = w.sum()
                val q = IntArray(4) { if (sum > 0f) Math.round(w[it] / sum * 255f) else if (it == 0) 255 else 0 }
                val diff = 255 - q.sum(); val big = q.indices.maxByOrNull { q[it] }!!; q[big] += diff
                for (k in 0..3) { a.put(o + 8 + k, joints!![v * 4 + k].coerceIn(0, 255).toByte()); a.put(o + 12 + k, q[k].coerceIn(0, 255).toByte()) }
            }
        }
        val posBuf = ByteBuffer.allocateDirect(pos.size * 4).order(ByteOrder.LITTLE_ENDIAN); posBuf.asFloatBuffer().put(pos)
        val asInt = n > 65535
        val ib = ByteBuffer.allocateDirect(idx.size * (if (asInt) 4 else 2)).order(ByteOrder.LITTLE_ENDIAN)
        if (asInt) ib.asIntBuffer().put(idx) else { val sb = ib.asShortBuffer(); for (v in idx) sb.put(v.toShort()) }
        val targets = p.a("targets").mapNotNull { t -> t.obj()?.i("POSITION")?.let { sparse(acc.floats(it)) } }
        val mesh = node.mesh
        return VrmPrimitive(mesh, node.index, if (skinned) node.skin else -1, (p.i("material") ?: -1).takeIf { it in 0 until materialCount } ?: -1, n,
            posBuf, a, stride, ib, idx.size, asInt, if (targets.isNotEmpty()) pos else null, targets)
    }

    private fun snorm(v: Float): Byte = Math.round(v.coerceIn(-1f, 1f) * 127f).toByte()

    private fun sparse(d: FloatArray): MorphTarget {
        val n = d.size / 3; var count = 0
        for (v in 0 until n) if (d[v * 3] != 0f || d[v * 3 + 1] != 0f || d[v * 3 + 2] != 0f) count++
        val verts = IntArray(count); val out = FloatArray(count * 3); var k = 0
        for (v in 0 until n) if (d[v * 3] != 0f || d[v * 3 + 1] != 0f || d[v * 3 + 2] != 0f) { verts[k] = v; out[k * 3] = d[v * 3]; out[k * 3 + 1] = d[v * 3 + 1]; out[k * 3 + 2] = d[v * 3 + 2]; k++ }
        return MorphTarget(verts, out)
    }

    // ---- materials ----
    private fun parseMaterials(root: JsonObject, v0: JsonObject?, imageCount: Int): List<VrmMaterial> {
        val textures = root.a("textures"); val v0props = v0?.a("materialProperties") ?: root.a("__none")
        fun image(texIndex: Int?): Int { val src = texIndex?.let { textures.getOrNull(it).obj()?.i("source") } ?: return -1; return if (src in 0 until imageCount) src else -1 }
        return root.a("materials").mapIndexed { i, el ->
            val m = el.obj() ?: JsonObject(emptyMap()); val pbr = m.o("pbrMetallicRoughness"); val ext = m.o("extensions"); val mt = ext?.o("KHR_materials_mtoon")
            val base = pbr?.a("baseColorFactor")?.floats()?.takeIf { it.size == 4 } ?: floatArrayOf(1f, 1f, 1f, 1f)
            var baseImg = image(pbr?.o("baseColorTexture")?.i("index")); var shadeImg = -1; var shade = floatArrayOf(base[0] * .8f, base[1] * .8f, base[2] * .8f)
            val emissive = m.a("emissiveFactor").floats().takeIf { it.size == 3 } ?: floatArrayOf(0f, 0f, 0f); var emImg = image(m.o("emissiveTexture")?.i("index"))
            var alpha = when (m.s("alphaMode")) { "MASK" -> AlphaMode.MASK; "BLEND" -> AlphaMode.BLEND; else -> AlphaMode.OPAQUE }; var cutoff = m.f("alphaCutoff") ?: 0.5f
            var double = m.b("doubleSided") ?: false; var shift = 0f; var toony = 0.9f; var outline = 0f; var outlineColor = floatArrayOf(0f, 0f, 0f); var unlit = ext?.get("KHR_materials_unlit") != null
            if (mt != null) {
                mt.a("shadeColorFactor").floats().takeIf { it.size >= 3 }?.let { shade = it.copyOf(3) }; shadeImg = image(mt.o("shadeMultiplyTexture")?.i("index"))
                shift = mt.f("shadingShiftFactor") ?: 0f; toony = mt.f("shadingToonyFactor") ?: 0.9f
                if (mt.s("outlineWidthMode").let { it != null && it != "none" }) outline = mt.f("outlineWidthFactor") ?: 0f
                mt.a("outlineColorFactor").floats().takeIf { it.size >= 3 }?.let { outlineColor = it.copyOf(3) }
            }
            val p0 = v0props.getOrNull(i).obj()
            if (p0 != null) {
                val shader = p0.s("shader").orEmpty(); val fl = p0.o("floatProperties"); val vec = p0.o("vectorProperties"); val tex = p0.o("textureProperties")
                fun vecOf(k: String) = vec?.a(k)?.floats()?.takeIf { it.size >= 3 }
                unlit = shader.contains("Unlit", true)
                // VRM 0.x colours are sRGB; everything downstream is linear (as VRM 1.0), so convert here exactly like three-vrm's V0 compat plugin (pow 2.2).
                vecOf("_Color")?.let { c -> base[0] = v0Linear(c[0]); base[1] = v0Linear(c[1]); base[2] = v0Linear(c[2]); if (c.size > 3) base[3] = c[3] }
                vecOf("_ShadeColor")?.let { shade = floatArrayOf(v0Linear(it[0]), v0Linear(it[1]), v0Linear(it[2])) } ?: run { shade = floatArrayOf(base[0] * .8f, base[1] * .8f, base[2] * .8f) }
                vecOf("_EmissionColor")?.let { emissive[0] = v0Linear(it[0]); emissive[1] = v0Linear(it[1]); emissive[2] = v0Linear(it[2]) }
                tex?.i("_MainTex")?.let { baseImg = image(it) }; tex?.i("_ShadeTexture")?.let { shadeImg = image(it) }; tex?.i("_EmissionMap")?.let { emImg = image(it) }
                fl?.f("_Cutoff")?.let { cutoff = it }; val mig = migrateV0Shade(fl?.f("_ShadeShift") ?: 0f, fl?.f("_ShadeToony") ?: 0.9f); shift = mig[0]; toony = mig[1]
                alpha = when ((fl?.f("_BlendMode") ?: -1f).toInt()) { 1 -> AlphaMode.MASK; 2, 3 -> AlphaMode.BLEND; 0 -> AlphaMode.OPAQUE; else -> alpha }
                if (shader.contains("Cutout", true)) alpha = AlphaMode.MASK
                double = (fl?.f("_CullMode") ?: 2f).toInt() == 0 || double
                if ((fl?.f("_OutlineWidthMode") ?: 0f) > 0f) outline = (fl?.f("_OutlineWidth") ?: 0f) * 0.01f
                vecOf("_OutlineColor")?.let { outlineColor = floatArrayOf(v0Linear(it[0]), v0Linear(it[1]), v0Linear(it[2])) }
            }
            VrmMaterial(m.s("name") ?: "material$i", base, shade, baseImg, shadeImg, emissive, emImg, alpha, cutoff, double, shift, toony, outline.coerceIn(0f, 0.02f), outlineColor, unlit)
        }
    }

    /** VRM 0.x sRGB component -> linear, the same pow(c, 2.2) three-vrm's VRMMaterialsV0CompatPlugin uses (the renderer's pow(1/2.2) undoes it). */
    internal fun v0Linear(c: Float): Float = Math.pow(c.toDouble(), 2.2).toFloat()

    /** VRM 0.x `_ShadeShift`/`_ShadeToony` -> VRM 1.0 (shadingShiftFactor, shadingToonyFactor), verbatim from three-vrm's VRMMaterialsV0CompatPlugin. */
    internal fun migrateV0Shade(shadeShift: Float, shadeToony: Float): FloatArray {
        val toony = shadeToony + (1f - shadeToony) * (0.5f + 0.5f * shadeShift)      // MathUtils.lerp(toony, 1, 0.5 + 0.5 * shift)
        return floatArrayOf(-shadeShift - (1f - toony), toony)
    }

    // ---- humanoid & expressions ----
    private fun parseHumanoid(v0: JsonObject?, v1: JsonObject?): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        val renameThumbs = v0?.o("humanoid")?.a("humanBones")?.any { it.obj()?.s("bone")?.endsWith("ThumbIntermediate") == true } == true
        v0?.o("humanoid")?.a("humanBones")?.forEach { b -> val o = b.obj() ?: return@forEach; val raw = o.s("bone"); val node = o.i("node")
            val name = if (renameThumbs && raw != null) raw.replace("ThumbProximal", "ThumbMetacarpal").replace("ThumbIntermediate", "ThumbProximal") else raw   // three-vrm's VRM0 -> VRM1 thumb names
            if (name != null && node != null) out[name] = node }
        v1?.o("humanoid")?.o("humanBones")?.forEach { (name, v) -> v.obj()?.i("node")?.let { out[name] = it } }
        return out
    }

    private val V0_PRESETS = mapOf("neutral" to "neutral", "a" to "aa", "i" to "ih", "u" to "ou", "e" to "ee", "o" to "oh", "blink" to "blink", "joy" to "happy", "angry" to "angry",
        "sorrow" to "sad", "fun" to "relaxed", "blink_l" to "blinkLeft", "blink_r" to "blinkRight", "lookup" to "lookUp", "lookdown" to "lookDown", "lookleft" to "lookLeft", "lookright" to "lookRight")

    private fun parseExpressions(root: JsonObject, v0: JsonObject?, v1: JsonObject?, nodes: List<VrmNode>, meta: MutableMap<String, ExpressionMeta>): Map<String, List<ExpressionBind>> {
        val out = LinkedHashMap<String, List<ExpressionBind>>()
        v0?.o("blendShapeMaster")?.a("blendShapeGroups")?.forEach { g ->
            val o = g.obj() ?: return@forEach
            val binds = o.a("binds").mapNotNull { b -> val bo = b.obj() ?: return@mapNotNull null; val mesh = bo.i("mesh") ?: return@mapNotNull null; val index = bo.i("index") ?: return@mapNotNull null
                ExpressionBind(mesh, index, (bo.f("weight") ?: 100f) / 100f) }
            if (binds.isEmpty()) return@forEach
            val preset = o.s("presetName")?.lowercase()?.let { V0_PRESETS[it] }; val name = o.s("name")
            val m = ExpressionMeta(isBinary = o.b("isBinary") ?: false)
            if (preset != null) { out[preset] = binds; meta[preset] = m }
            if (name != null && name.isNotBlank() && !out.containsKey(name)) { out[name] = binds; meta[name] = m }
        }
        v1?.o("expressions")?.let { ex ->
            fun add(name: String, e: JsonObject) {
                val binds = e.a("morphTargetBinds").mapNotNull { b -> val bo = b.obj() ?: return@mapNotNull null; val node = bo.i("node") ?: return@mapNotNull null; val index = bo.i("index") ?: return@mapNotNull null
                    val mesh = nodes.getOrNull(node)?.mesh ?: -1; if (mesh < 0) null else ExpressionBind(mesh, index, bo.f("weight") ?: 1f) }
                if (binds.isNotEmpty()) { out[name] = binds; meta[name] = ExpressionMeta(e.b("isBinary") ?: false, e.s("overrideBlink") ?: "none", e.s("overrideLookAt") ?: "none", e.s("overrideMouth") ?: "none") }
            }
            ex.o("preset")?.forEach { (name, e) -> e.obj()?.let { add(name, it) } }
            ex.o("custom")?.forEach { (name, e) -> e.obj()?.let { add(name, it) } }
        }
        return out
    }
}
