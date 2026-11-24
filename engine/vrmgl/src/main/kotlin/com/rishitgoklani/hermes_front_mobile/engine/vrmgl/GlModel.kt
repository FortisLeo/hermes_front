package com.rishitgoklani.hermes_front_mobile.engine.vrmgl

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLUtils
import com.rishitgoklani.hermes_front_mobile.engine.vrm.AlphaMode
import com.rishitgoklani.hermes_front_mobile.engine.vrm.DrawItem
import com.rishitgoklani.hermes_front_mobile.engine.vrm.Pass
import com.rishitgoklani.hermes_front_mobile.engine.vrm.PoseDriver
import com.rishitgoklani.hermes_front_mobile.engine.vrm.RenderPlan
import com.rishitgoklani.hermes_front_mobile.engine.vrm.Rig
import com.rishitgoklani.hermes_front_mobile.engine.vrm.SpringSimulation
import com.rishitgoklani.hermes_front_mobile.engine.vrm.VrmAsset
import com.rishitgoklani.hermes_front_mobile.engine.vrm.VrmMaterial
import com.rishitgoklani.hermes_front_mobile.engine.vrm.VrmPrimitive
import com.rishitgoklani.hermes_front_mobile.engine.vrm.math.Mat4
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.pow

/** Quality switches chosen per device. */
data class EngineConfig(val outlines: Boolean = true, val springs: Boolean = true, val rgb565: Boolean = true)

/** A model on the GPU plus its live rig, driver and physics. Create and release on the GL thread only. */
internal class GlModel(val asset: VrmAsset, private val config: EngineConfig) {
    val rig = Rig(asset); val driver = PoseDriver(rig); val springs = SpringSimulation(rig)
    val plan: List<DrawItem> = RenderPlan.build(asset, config.outlines)
    val maxJoints = asset.skins.maxOfOrNull { it.joints.size } ?: 0
    private class Gpu(val vao: Int, val pos: Int, val attr: Int, val ibo: Int)
    private val gpu = ArrayList<Gpu>(); private val textures = IntArray(asset.images.size)
    private val skinRows = asset.skins.map { FloatArray(it.joints.size * 12) }; private val skinMat = asset.skins.map { FloatArray(it.joints.size * 16) }
    private val morphScratch: FloatBuffer
    private val morphWork: FloatArray
    private val nodeModel = FloatArray(16)
    private var white = 0

    init {
        val maxMorphVerts = asset.primitives.filter { it.morphBase != null }.maxOfOrNull { it.vertexCount } ?: 0
        morphScratch = ByteBuffer.allocateDirect(maxMorphVerts * 12).order(ByteOrder.nativeOrder()).asFloatBuffer(); morphWork = FloatArray(maxMorphVerts * 3)
        val ids = IntArray(1)
        for (p in asset.primitives) {
            GLES30.glGenVertexArrays(1, ids, 0); val vao = ids[0]; GLES30.glBindVertexArray(vao)
            val bufs = IntArray(3); GLES30.glGenBuffers(3, bufs, 0)
            p.positions.position(0); GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, bufs[0])
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, p.vertexCount * 12, p.positions, if (p.morphBase != null) GLES30.GL_DYNAMIC_DRAW else GLES30.GL_STATIC_DRAW)
            GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0)
            p.attributes.position(0); GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, bufs[1])
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, p.vertexCount * p.attributeStride, p.attributes, GLES30.GL_STATIC_DRAW)
            GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 4, GLES30.GL_BYTE, true, p.attributeStride, 0)
            GLES30.glEnableVertexAttribArray(2); GLES30.glVertexAttribPointer(2, 2, GLES30.GL_HALF_FLOAT, false, p.attributeStride, 4)
            if (p.skinned) {
                GLES30.glEnableVertexAttribArray(3); GLES30.glVertexAttribIPointer(3, 4, GLES30.GL_UNSIGNED_BYTE, p.attributeStride, 8)
                GLES30.glEnableVertexAttribArray(4); GLES30.glVertexAttribPointer(4, 4, GLES30.GL_UNSIGNED_BYTE, true, p.attributeStride, 12)
            }
            p.indices.position(0); GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, bufs[2])
            GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, p.indices.remaining(), p.indices, GLES30.GL_STATIC_DRAW)
            gpu += Gpu(vao, bufs[0], bufs[1], bufs[2])
        }
        GLES30.glBindVertexArray(0)
        GLES30.glGenTextures(1, ids, 0); white = ids[0]; GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, white)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, 1, 1, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, ByteBuffer.allocateDirect(4).put(byteArrayOf(-1, -1, -1, -1)).position(0))
    }

    /** Upload one decoded image. The caller recycles the bitmap right after. */
    fun uploadTexture(image: Int, bmp: Bitmap) {
        val id = IntArray(1); GLES30.glGenTextures(1, id, 0); textures[image] = id[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id[0])
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0); GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_NEAREST); GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT); GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
    }

    /** Re-blends morph targets for meshes whose expression weights changed, straight into the position buffers. */
    fun updateMorphs() {
        if (rig.dirtyMeshes.isEmpty()) return
        for ((i, p) in asset.primitives.withIndex()) {
            val base = p.morphBase ?: continue; if (p.mesh !in rig.dirtyMeshes) continue
            val w = rig.meshWeights[p.mesh] ?: continue
            System.arraycopy(base, 0, morphWork, 0, base.size)
            for ((t, target) in p.morphTargets.withIndex()) { val wt = w.getOrElse(t) { 0f }; if (wt == 0f) continue
                for (k in target.vertices.indices) { val v = target.vertices[k] * 3; morphWork[v] += target.deltas[k * 3] * wt; morphWork[v + 1] += target.deltas[k * 3 + 1] * wt; morphWork[v + 2] += target.deltas[k * 3 + 2] * wt } }
            morphScratch.clear(); morphScratch.put(morphWork, 0, base.size); morphScratch.position(0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, gpu[i].pos); GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, base.size * 4, morphScratch)
        }
        rig.dirtyMeshes.clear()
    }

    /** Skin matrices -> 3 row vectors per joint (a 3x4 matrix), the compact form the vertex shader reads. */
    private fun prepareSkins() {
        for (s in asset.skins.indices) {
            rig.jointMatrices(s, skinMat[s]); val m = skinMat[s]; val r = skinRows[s]
            for (j in 0 until asset.skins[s].joints.size) { val a = j * 16; val b = j * 12
                r[b] = m[a]; r[b + 1] = m[a + 4]; r[b + 2] = m[a + 8]; r[b + 3] = m[a + 12]
                r[b + 4] = m[a + 1]; r[b + 5] = m[a + 5]; r[b + 6] = m[a + 9]; r[b + 7] = m[a + 13]
                r[b + 8] = m[a + 2]; r[b + 9] = m[a + 6]; r[b + 10] = m[a + 10]; r[b + 11] = m[a + 14] }
        }
    }

    fun draw(vp: FloatArray, light: FloatArray, program: (skinned: Boolean) -> GlProgram) {
        prepareSkins()
        var current: GlProgram? = null; var blend = false; var culled = -1
        for (item in plan) {
            val p = asset.primitives[item.primitive]; val mat = asset.materials.getOrNull(p.material) ?: DEFAULT
            val prog = program(p.skinned)
            if (prog !== current) { prog.use(); current = prog
                GLES30.glUniformMatrix4fv(prog.loc("uVP"), 1, false, vp, 0); GLES30.glUniform3fv(prog.loc("uLight"), 1, light, 0)
                GLES30.glUniform1i(prog.loc("uBaseTex"), 0); GLES30.glUniform1i(prog.loc("uShadeTex"), 1); GLES30.glUniform1i(prog.loc("uEmTex"), 2) }
            if (p.skinned) { System.arraycopy(rig.modelMatrix, 0, nodeModel, 0, 16); GLES30.glUniform4fv(prog.loc("uBones"), asset.skins[p.skin].joints.size * 3, skinRows[p.skin], 0) }
            else Mat4.mul(rig.modelMatrix, 0, rig.world, p.node * 16, nodeModel, 0)
            GLES30.glUniformMatrix4fv(prog.loc("uModel"), 1, false, nodeModel, 0)
            val outline = item.pass == Pass.OUTLINE
            val wantBlend = item.pass == Pass.BLEND
            if (wantBlend != blend) { if (wantBlend) { GLES30.glEnable(GLES30.GL_BLEND); GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA); GLES30.glDepthMask(false) } else { GLES30.glDisable(GLES30.GL_BLEND); GLES30.glDepthMask(true) }; blend = wantBlend }
            val cull = if (outline) 1 else if (mat.doubleSided) 0 else 2
            if (cull != culled) { culled = cull; if (cull == 0) GLES30.glDisable(GLES30.GL_CULL_FACE) else { GLES30.glEnable(GLES30.GL_CULL_FACE); GLES30.glCullFace(if (cull == 1) GLES30.GL_FRONT else GLES30.GL_BACK) } }
            var flags = 0
            val bt = tex(mat.baseImage); val st = tex(mat.shadeImage); val et = tex(mat.emissiveImage)
            if (bt != 0) flags = flags or Shaders.FLAG_BASE_TEX; if (st != 0) flags = flags or Shaders.FLAG_SHADE_TEX; if (et != 0) flags = flags or Shaders.FLAG_EMISSIVE_TEX
            if (mat.unlit) flags = flags or Shaders.FLAG_UNLIT; if (mat.alphaMode == AlphaMode.MASK) flags = flags or Shaders.FLAG_MASK; if (outline) flags = flags or Shaders.FLAG_OUTLINE
            GLES30.glUniform1i(prog.loc("uFlags"), flags); GLES30.glUniform1f(prog.loc("uOutline"), if (outline) mat.outlineWidth else 0f)
            GLES30.glUniform4f(prog.loc("uBase"), g(mat.baseColor[0]), g(mat.baseColor[1]), g(mat.baseColor[2]), mat.baseColor[3])
            GLES30.glUniform3f(prog.loc("uShade"), g(mat.shadeColor[0]), g(mat.shadeColor[1]), g(mat.shadeColor[2]))
            GLES30.glUniform3f(prog.loc("uEmissive"), g(mat.emissive[0]), g(mat.emissive[1]), g(mat.emissive[2]))
            GLES30.glUniform3f(prog.loc("uOutlineColor"), g(mat.outlineColor[0]), g(mat.outlineColor[1]), g(mat.outlineColor[2]))
            GLES30.glUniform1f(prog.loc("uShift"), mat.shadeShift); GLES30.glUniform1f(prog.loc("uToony"), mat.shadeToony); GLES30.glUniform1f(prog.loc("uCutoff"), mat.cutoff)
            bind(0, if (bt != 0) bt else white); bind(1, if (st != 0) st else white); bind(2, if (et != 0) et else white)
            GLES30.glBindVertexArray(gpu[item.primitive].vao)
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, p.indexCount, if (p.indicesAreInt) GLES30.GL_UNSIGNED_INT else GLES30.GL_UNSIGNED_SHORT, 0)
        }
        if (blend) { GLES30.glDisable(GLES30.GL_BLEND); GLES30.glDepthMask(true) }
        GLES30.glBindVertexArray(0)
    }

    private fun tex(i: Int) = if (i in textures.indices) textures[i] else 0
    private fun bind(unit: Int, id: Int) { GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id) }
    /** Colours in glTF are linear; the shader works in the display's gamma space, so convert once here. */
    private fun g(c: Float) = c.coerceIn(0f, 1f).pow(1f / 2.2f)

    fun release() {
        for (g in gpu) { GLES30.glDeleteVertexArrays(1, intArrayOf(g.vao), 0); GLES30.glDeleteBuffers(3, intArrayOf(g.pos, g.attr, g.ibo), 0) }
        gpu.clear(); GLES30.glDeleteTextures(textures.size, textures, 0); GLES30.glDeleteTextures(1, intArrayOf(white), 0)
    }

    companion object {
        private val DEFAULT = VrmMaterial("default", floatArrayOf(1f, 1f, 1f, 1f), floatArrayOf(.8f, .8f, .8f), -1, -1, floatArrayOf(0f, 0f, 0f), -1, AlphaMode.OPAQUE, .5f, false, 0f, .9f, 0f, floatArrayOf(0f, 0f, 0f), false)
    }
}
