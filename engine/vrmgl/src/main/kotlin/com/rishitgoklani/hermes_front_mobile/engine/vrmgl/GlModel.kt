package com.rishitgoklani.hermes_front_mobile.engine.vrmgl

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLUtils
import android.os.Trace
import com.rishitgoklani.hermes_front_mobile.engine.vrm.AlphaMode
import com.rishitgoklani.hermes_front_mobile.engine.vrm.DrawItem
import com.rishitgoklani.hermes_front_mobile.engine.vrm.FaceFxLayout
import com.rishitgoklani.hermes_front_mobile.engine.vrm.Pass
import com.rishitgoklani.hermes_front_mobile.engine.vrm.PoseDriver
import com.rishitgoklani.hermes_front_mobile.engine.vrm.RenderPlan
import com.rishitgoklani.hermes_front_mobile.engine.vrm.Rig
import com.rishitgoklani.hermes_front_mobile.engine.vrm.SpringSimulation
import com.rishitgoklani.hermes_front_mobile.engine.vrm.VrmAsset
import com.rishitgoklani.hermes_front_mobile.engine.vrm.VrmMaterial
import com.rishitgoklani.hermes_front_mobile.engine.vrm.math.Mat4
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** Quality switches chosen per device. [compressTextures]: use the ETC2 texture cache when it exists (see [ModelCache]). */
data class EngineConfig(val outlines: Boolean = true, val springs: Boolean = true, val rgb565: Boolean = true, val compressTextures: Boolean = true)

/**
 * A model on the GPU plus its live rig, driver and physics. Create and release on the GL thread only.
 * Shader variants come from [programs]; pass a shared [ProgramCache] to reuse compiled variants across models (it is then not released with the model).
 */
internal class GlModel(val asset: VrmAsset, private val config: EngineConfig, sharedPrograms: ProgramCache? = null) {
    val rig = Rig(asset); val driver = PoseDriver(rig); val springs = SpringSimulation(rig)
    /** Where blush, tears, sweat and the anger mark sit this frame; the view's [FaceFxRenderer] draws them. */
    val faceFx = FaceFxLayout(rig)
    val plan: List<DrawItem> = RenderPlan.build(asset, config.outlines)
    val maxJoints = asset.skins.maxOfOrNull { it.joints.size } ?: 0
    private val ownsPrograms = sharedPrograms == null
    private val programs = sharedPrograms ?: ProgramCache()

    /** Two position buffers (and VAOs) for morphing primitives: one is drawn while the other is written, so the driver never stalls on an in-flight buffer. */
    private class PosGroup(val bufs: IntArray) { var cur = 0 }
    private class Gpu(val vao: IntArray, val group: PosGroup, val attr: Int, val ibo: Int)
    /** Primitives of one mesh usually read the same packed vertex arrays; the parser hands out the same buffer object and the GPU copy is made once. */
    private val posGroups = java.util.IdentityHashMap<ByteBuffer, PosGroup>(); private val attrBufs = java.util.IdentityHashMap<ByteBuffer, Int>()
    private val gpu = ArrayList<Gpu>(); private val textures = IntArray(asset.images.size)
    private val skinRows = asset.skins.map { FloatArray(it.joints.size * 12) }
    private val skinStamps = LongArray(asset.skins.size) { -1L }
    /** Bones go into a float texture when the GPU's uniform space cannot hold them (some Adreno report only 256 vectors). */
    private val boneTexMode: Boolean = run { val v = IntArray(1); GLES30.glGetIntegerv(GLES30.GL_MAX_VERTEX_UNIFORM_VECTORS, v, 0); maxJoints * 3 + 24 > v[0] || FORCE_BONE_TEX }
    private val boneTex = IntArray(asset.skins.size); private val boneTexStamp = LongArray(asset.skins.size) { -1L }
    private val boneBuf: java.nio.FloatBuffer = java.nio.ByteBuffer.allocateDirect(maxJoints.coerceAtLeast(1) * 48).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
    private val programJoints get() = if (boneTexMode) -maxJoints else maxJoints
    private var texVersion = 0
    private val materials = asset.materials.map { MaterialMath.compile(it) }
    private val morphPrims: IntArray
    private val morphRange = arrayOfNulls<IntArray>(asset.primitives.size)
    private val morphScratch: FloatBuffer
    private val morphWork: FloatArray
    private val nodeModel = FloatArray(16)

    // per-plan-item program cache, valid for one texVersion
    private val itemProgram = arrayOfNulls<GlProgram>(plan.size)
    private val itemFlags = IntArray(plan.size)
    private var itemVersion = -1

    // GL state tracked within one draw
    private var curProgram: GlProgram? = null; private var curVao = -1; private var blendState = -1; private var cullState = -1
    private val boundTex = IntArray(3); private var activeUnit = -1

    private var pendingBytes = 0
    /** Counters of the most recent [draw] (bytes include morph uploads since the previous draw). */
    var lastDrawStats = DrawStats(); private set

    init {
        Trace.beginSection("GlModel.init")
        try {
            val morphIdx = ArrayList<Int>()
            val seenMorph = java.util.IdentityHashMap<ByteBuffer, Boolean>()
            for ((i, p) in asset.primitives.withIndex()) if (p.morphBase != null && seenMorph.put(p.positions, true) == null) { morphIdx += i; morphRange[i] = MorphMath.touchedRange(p.morphTargets) }
            morphPrims = morphIdx.toIntArray()
            val maxSpan = morphPrims.maxOfOrNull { val r = morphRange[it]; if (r == null) 0 else r[1] - r[0] + 1 } ?: 0
            morphScratch = ByteBuffer.allocateDirect(maxSpan * 12).order(ByteOrder.nativeOrder()).asFloatBuffer(); morphWork = FloatArray(maxSpan * 3)
            for (p in asset.primitives) gpu += upload(p)
            GLES30.glBindVertexArray(0)
            GlErrors.check("model upload")
        } finally { Trace.endSection() }
    }

    private fun upload(p: com.rishitgoklani.hermes_front_mobile.engine.vrm.VrmPrimitive): Gpu {
        // Unbind the previous primitive's VAO: element-array binds below would otherwise overwrite that VAO's index buffer.
        GLES30.glBindVertexArray(0)
        val morph = p.morphBase != null; val copies = if (morph) 2 else 1
        val group = posGroups.getOrPut(p.positions) {
            val bufs = IntArray(copies); GLES30.glGenBuffers(copies, bufs, 0)
            for (b in bufs) { p.positions.position(0); GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, b)
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, p.vertexCount * 12, p.positions, if (morph) GLES30.GL_DYNAMIC_DRAW else GLES30.GL_STATIC_DRAW) }
            PosGroup(bufs)
        }
        val posBufs = group.bufs
        val attr = attrBufs.getOrPut(p.attributes) {
            val id = IntArray(1); GLES30.glGenBuffers(1, id, 0); p.attributes.position(0); GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, id[0])
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, p.vertexCount * p.attributeStride, p.attributes, GLES30.GL_STATIC_DRAW); id[0]
        }
        val iboId = IntArray(1); GLES30.glGenBuffers(1, iboId, 0); val ibo = iboId[0]
        p.indices.position(0); GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, p.indices.remaining(), p.indices, GLES30.GL_STATIC_DRAW)
        val vaos = IntArray(copies); GLES30.glGenVertexArrays(copies, vaos, 0)
        for (c in 0 until copies) {
            GLES30.glBindVertexArray(vaos[c])
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, posBufs[c]); GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, attr)
            GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 4, GLES30.GL_BYTE, true, p.attributeStride, 0)
            GLES30.glEnableVertexAttribArray(2); GLES30.glVertexAttribPointer(2, 2, GLES30.GL_HALF_FLOAT, false, p.attributeStride, 4)
            if (p.skinned) {
                GLES30.glEnableVertexAttribArray(3); GLES30.glVertexAttribIPointer(3, 4, GLES30.GL_UNSIGNED_BYTE, p.attributeStride, 8)
                GLES30.glEnableVertexAttribArray(4); GLES30.glVertexAttribPointer(4, 4, GLES30.GL_UNSIGNED_BYTE, true, p.attributeStride, 12)
            }
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo)
        }
        return Gpu(vaos, group, attr, ibo)
    }

    /**
     * Upload one decoded image into immutable storage (RGB565 stays 565, everything else RGBA8) and build mipmaps.
     * The caller recycles the bitmap right after.
     */
    fun uploadTexture(image: Int, bmp: Bitmap) {
        if (textures[image] != 0) GLES30.glDeleteTextures(1, intArrayOf(textures[image]), 0)
        val id = IntArray(1); GLES30.glGenTextures(1, id, 0); textures[image] = id[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id[0])
        val internal = if (bmp.config == Bitmap.Config.RGB_565) GLES30.GL_RGB565 else GLES30.GL_RGBA8
        GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, TextureMath.levels(bmp.width, bmp.height), internal, bmp.width, bmp.height)
        GLUtils.texSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, bmp); GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_NEAREST); GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT); GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
        GlErrors.check("texture upload $image")
        pendingBytes += bmp.byteCount; texVersion++
    }

    /**
     * Upload one image already compressed for the GPU ([format] is an ETC2 format), mip levels from the largest down ([levels] =
     * width, height, bytes). No decode and no mipmap generation: the levels come straight from the texture cache.
     */
    fun uploadCompressed(image: Int, format: Int, levels: List<Triple<Int, Int, ByteBuffer>>) {
        if (textures[image] != 0) GLES30.glDeleteTextures(1, intArrayOf(textures[image]), 0)
        val id = IntArray(1); GLES30.glGenTextures(1, id, 0); textures[image] = id[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id[0])
        GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, levels.size, format, levels[0].first, levels[0].second)
        var bytes = 0
        for ((i, l) in levels.withIndex()) { GLES30.glCompressedTexSubImage2D(GLES30.GL_TEXTURE_2D, i, 0, 0, l.first, l.second, format, l.third.remaining(), l.third); bytes += l.third.remaining() }
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_NEAREST); GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT); GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
        GlErrors.check("compressed texture upload $image")
        pendingBytes += bytes; texVersion++
    }

    /** Compiles every shader variant this model will draw with (call after the textures are uploaded) so the first frame does not hitch. */
    fun prewarm() { resolvePrograms() }

    /**
     * Re-blends morph targets for meshes whose expression weights changed. Only the vertex range the primitive's targets can touch is
     * recomputed and uploaded, into the buffer that is not currently being drawn. Returns whether anything was uploaded.
     */
    fun updateMorphs(): Boolean {
        if (rig.dirtyMeshes.isEmpty()) return false
        Trace.beginSection("GlModel.morph")
        var any = false
        try {
            for (i in morphPrims) {
                val p = asset.primitives[i]; if (p.mesh !in rig.dirtyMeshes) continue
                val range = morphRange[i] ?: continue; val w = rig.meshWeights[p.mesh] ?: continue
                val base = p.morphBase ?: continue
                val lo = range[0]; val n = (range[1] - lo + 1) * 3
                System.arraycopy(base, lo * 3, morphWork, 0, n)
                for ((t, target) in p.morphTargets.withIndex()) { val wt = w.getOrElse(t) { 0f }; if (wt == 0f) continue
                    for (k in target.vertices.indices) { val v = (target.vertices[k] - lo) * 3
                        morphWork[v] += target.deltas[k * 3] * wt; morphWork[v + 1] += target.deltas[k * 3 + 1] * wt; morphWork[v + 2] += target.deltas[k * 3 + 2] * wt } }
                morphScratch.clear(); morphScratch.put(morphWork, 0, n); morphScratch.flip()
                val g = gpu[i]; val next = 1 - g.group.cur
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, g.group.bufs[next]); GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, lo * 12, n * 4, morphScratch)
                g.group.cur = next; pendingBytes += n * 4; any = true
            }
            rig.dirtyMeshes.clear()
        } finally { Trace.endSection() }
        return any
    }

    /** Skin matrices straight into 3 row vectors per joint (a 3x4 matrix, the form the vertex shader reads); no intermediate 4x4. */
    private fun prepareSkins() {
        Trace.beginSection("GlModel.skins")
        for (s in asset.skins.indices) { val skin = asset.skins[s]; SkinMath.packRows(rig.world, skin.joints, skin.inverseBind, skinRows[s]); skinStamps[s] = programs.counters.nextStamp() }
        Trace.endSection()
    }

    /** Uploads one skin's bone rows (3 RGBA32F texels per joint, one row) when they changed; returns the bytes sent. */
    private fun uploadBoneTexture(skin: Int, stamp: Long): Int {
        val joints = asset.skins[skin].joints.size
        if (boneTex[skin] == 0) {
            val id = IntArray(1); GLES30.glGenTextures(1, id, 0); boneTex[skin] = id[0]
            GLES30.glActiveTexture(GLES30.GL_TEXTURE3); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id[0])
            GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, 1, GLES30.GL_RGBA32F, joints * 3, 1)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST); GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE); GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        }
        if (boneTexStamp[skin] == stamp) return 0
        boneTexStamp[skin] = stamp
        boneBuf.clear(); boneBuf.put(skinRows[skin], 0, joints * 12); boneBuf.position(0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE3); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, boneTex[skin])
        GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, joints * 3, 1, GLES30.GL_RGBA, GLES30.GL_FLOAT, boneBuf)
        return joints * 48
    }

    private fun resolvePrograms() {
        for ((idx, item) in plan.withIndex()) {
            val p = asset.primitives[item.primitive]; val mat = material(p.material)
            var flags = mat.staticFlags
            if (item.pass == Pass.OUTLINE) flags = (flags and ShaderSources.FLAG_MASK) or ShaderSources.FLAG_OUTLINE
            if (tex(mat.baseImage) != 0) flags = flags or ShaderSources.FLAG_BASE_TEX
            if (tex(mat.shadeImage) != 0) flags = flags or ShaderSources.FLAG_SHADE_TEX
            if (tex(mat.emissiveImage) != 0) flags = flags or ShaderSources.FLAG_EMISSIVE_TEX
            flags = ShaderSources.normalize(flags); itemFlags[idx] = flags
            itemProgram[idx] = programs.get(flags, p.skinned, programJoints)
        }
        itemVersion = texVersion
    }

    /**
     * Draws the plan. [poseChanged] = false skips recomputing and re-uploading skin matrices (use when the rig world matrices
     * are unchanged since the previous draw). The first draw always computes them.
     */
    fun draw(vp: FloatArray, light: FloatArray, poseChanged: Boolean = true) {
        Trace.beginSection("GlModel.draw")
        try {
            val uniformsBefore = programs.counters.uniformCalls
            if (poseChanged || skinStamps.any { it < 0 }) prepareSkins()
            if (itemVersion != texVersion) resolvePrograms()
            curProgram = null; curVao = -1; blendState = -1; cullState = -1; activeUnit = -1; boundTex.fill(-1)
            GLES30.glDisable(GLES30.GL_BLEND); GLES30.glDepthMask(true); blendState = 0
            var calls = 0; var bytes = pendingBytes; pendingBytes = 0
            for ((idx, item) in plan.withIndex()) {
                val p = asset.primitives[item.primitive]; val mat = material(p.material)
                val prog = itemProgram[idx]!!; val flags = itemFlags[idx]
                if (prog !== curProgram) { prog.use(); curProgram = prog }
                prog.setMat4(ShaderSources.U_VP, vp); prog.setVec3(ShaderSources.U_LIGHT, light)
                val outline = item.pass == Pass.OUTLINE
                if (p.skinned) {
                    prog.setMat4(ShaderSources.U_MODEL, rig.modelMatrix)
                    val stamp = skinStamps[p.skin]
                    if (boneTexMode) { bytes += uploadBoneTexture(p.skin, stamp); GLES30.glActiveTexture(GLES30.GL_TEXTURE3); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, boneTex[p.skin]); activeUnit = -1; boundTex.fill(-1) }
                    else if (prog.boneStamp != stamp) { val j = asset.skins[p.skin].joints.size; prog.setBones(skinRows[p.skin], j); prog.boneStamp = stamp; bytes += j * 48 }
                } else { Mat4.mul(rig.modelMatrix, 0, rig.world, p.node * 16, nodeModel, 0); prog.setMat4(ShaderSources.U_MODEL, nodeModel) }
                if (outline) { prog.setVec3(ShaderSources.U_OUTLINE_COLOR, mat.outlineColor); prog.setFloat(ShaderSources.U_OUTLINE, mat.outlineWidth) }
                else {
                    prog.setVec4(ShaderSources.U_BASE, mat.base); prog.setVec3(ShaderSources.U_SHADE, mat.shade); prog.setVec3(ShaderSources.U_EMISSIVE, mat.emissive)
                    prog.setFloat(ShaderSources.U_SHIFT, mat.shift); prog.setFloat(ShaderSources.U_TOONY, mat.edge)
                }
                prog.setFloat(ShaderSources.U_CUTOFF, mat.cutoff)
                if (outline) prog.setVec4(ShaderSources.U_BASE, mat.base)
                setBlend(item.pass == Pass.BLEND)
                setCull(if (outline) 1 else if (mat.doubleSided) 0 else 2)
                if (flags and ShaderSources.FLAG_BASE_TEX != 0) bind(0, tex(mat.baseImage))
                if (flags and ShaderSources.FLAG_SHADE_TEX != 0) bind(1, tex(mat.shadeImage))
                if (flags and ShaderSources.FLAG_EMISSIVE_TEX != 0) bind(2, tex(mat.emissiveImage))
                val g = gpu[item.primitive]; val vao = g.vao[g.group.cur]
                if (vao != curVao) { GLES30.glBindVertexArray(vao); curVao = vao }
                GLES30.glDrawElements(GLES30.GL_TRIANGLES, p.indexCount, if (p.indicesAreInt) GLES30.GL_UNSIGNED_INT else GLES30.GL_UNSIGNED_SHORT, 0); calls++
            }
            if (blendState == 1) { GLES30.glDisable(GLES30.GL_BLEND); GLES30.glDepthMask(true) }
            GLES30.glBindVertexArray(0)
            GlErrors.check("draw")
            lastDrawStats = DrawStats(calls, programs.counters.uniformCalls - uniformsBefore, bytes)
        } finally { Trace.endSection() }
    }

    private fun material(i: Int): GlMaterial = materials.getOrNull(i) ?: DEFAULT
    private fun setBlend(on: Boolean) {
        val want = if (on) 1 else 0; if (want == blendState) return
        if (on) { GLES30.glEnable(GLES30.GL_BLEND); GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA); GLES30.glDepthMask(false) } else { GLES30.glDisable(GLES30.GL_BLEND); GLES30.glDepthMask(true) }
        blendState = want
    }
    private fun setCull(cull: Int) {
        if (cull == cullState) return; cullState = cull
        if (cull == 0) GLES30.glDisable(GLES30.GL_CULL_FACE) else { GLES30.glEnable(GLES30.GL_CULL_FACE); GLES30.glCullFace(if (cull == 1) GLES30.GL_FRONT else GLES30.GL_BACK) }
    }
    private fun tex(i: Int) = if (i in textures.indices) textures[i] else 0
    private fun bind(unit: Int, id: Int) {
        if (boundTex[unit] == id) return
        if (activeUnit != unit) { GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit); activeUnit = unit }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id); boundTex[unit] = id
    }

    /** Source-compatible overload for the old view: the program lambda is ignored, variants are chosen per material. */
    @Deprecated("Use draw(vp, light, poseChanged)")
    fun draw(vp: FloatArray, light: FloatArray, program: (skinned: Boolean) -> GlProgram) = draw(vp, light, true)

    fun release() {
        for (g in gpu) { GLES30.glDeleteVertexArrays(g.vao.size, g.vao, 0); GLES30.glDeleteBuffers(1, intArrayOf(g.ibo), 0) }
        for (grp in posGroups.values) GLES30.glDeleteBuffers(grp.bufs.size, grp.bufs, 0)
        for (b in attrBufs.values) GLES30.glDeleteBuffers(1, intArrayOf(b), 0)
        posGroups.clear(); attrBufs.clear()
        gpu.clear(); GLES30.glDeleteTextures(textures.size, textures, 0); GLES30.glDeleteTextures(boneTex.size, boneTex, 0)
        if (ownsPrograms) programs.release()
    }

    companion object {
        private val DEFAULT = MaterialMath.compile(VrmMaterial("default", floatArrayOf(1f, 1f, 1f, 1f), floatArrayOf(.8f, .8f, .8f), -1, -1, floatArrayOf(0f, 0f, 0f), -1, AlphaMode.OPAQUE, .5f, false, 0f, .9f, 0f, floatArrayOf(0f, 0f, 0f), false))
    }
}

internal object GlErrors {
    /** Throws a descriptive exception when the GL error flag is set. */
    fun check(what: String) {
        val e = android.opengl.GLES30.glGetError()
        if (e != 0) throw IllegalStateException("GL error 0x${Integer.toHexString(e)} during $what")
    }
}

/** Debug switch used to compare the two skinning paths on one model. */
internal const val FORCE_BONE_TEX = false
