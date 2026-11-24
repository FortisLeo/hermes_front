package com.rishitgoklani.hermes_front_mobile.engine.vrmgl

import android.opengl.GLES30

/** Counts GL uniform calls that actually reach the driver, and hands out unique stamps for uploaded skin data. */
internal class GlCounters { var uniformCalls = 0; private var stamp = 0L; fun nextStamp() = ++stamp }

/**
 * A linked program. Uniform locations are resolved once at link time; setters skip calls whose value did not change
 * since the last call on this program (uniform values persist per program).
 */
internal class GlProgram(vertex: String, fragment: String, val label: String = "program", private val counters: GlCounters = GlCounters()) {
    val id: Int
    private val locs = IntArray(ShaderSources.UNIFORMS.size)
    private val shadow = Array(ShaderSources.UNIFORMS.size) { FloatArray(ShaderSources.SHADOW_SIZES[it]) { Float.NaN } }
    private val one = FloatArray(1)
    /** Stamp of the skin rows last uploaded to uBones, so a skin is uploaded once per pose. */
    var boneStamp = -1L

    init {
        val vs = compile(GLES30.GL_VERTEX_SHADER, vertex, "vertex")
        val fs = try { compile(GLES30.GL_FRAGMENT_SHADER, fragment, "fragment") } catch (e: Throwable) { GLES30.glDeleteShader(vs); throw e }
        val program = GLES30.glCreateProgram()
        if (program == 0) { GLES30.glDeleteShader(vs); GLES30.glDeleteShader(fs); throw IllegalStateException("glCreateProgram failed for $label (GL error ${GLES30.glGetError()})") }
        GLES30.glAttachShader(program, vs); GLES30.glAttachShader(program, fs); GLES30.glLinkProgram(program)
        val ok = IntArray(1); GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, ok, 0)
        val log = if (ok[0] == 0) GLES30.glGetProgramInfoLog(program) else ""
        GLES30.glDetachShader(program, vs); GLES30.glDetachShader(program, fs); GLES30.glDeleteShader(vs); GLES30.glDeleteShader(fs)
        if (ok[0] == 0) { GLES30.glDeleteProgram(program); throw IllegalStateException("Shader link failed ($label): $log") }
        id = program
        for (i in locs.indices) locs[i] = GLES30.glGetUniformLocation(id, ShaderSources.UNIFORMS[i])
        GLES30.glUseProgram(id)
        if (locs[ShaderSources.U_BASE_TEX] >= 0) GLES30.glUniform1i(locs[ShaderSources.U_BASE_TEX], 0)
        if (locs[ShaderSources.U_SHADE_TEX] >= 0) GLES30.glUniform1i(locs[ShaderSources.U_SHADE_TEX], 1)
        if (locs[ShaderSources.U_EM_TEX] >= 0) GLES30.glUniform1i(locs[ShaderSources.U_EM_TEX], 2)
        GLES30.glUseProgram(0)
    }

    fun use() = GLES30.glUseProgram(id)
    fun delete() = GLES30.glDeleteProgram(id)

    private fun changed(slot: Int, src: FloatArray, off: Int, n: Int): Boolean {
        if (locs[slot] < 0) return false
        val sh = shadow[slot]; var diff = false
        for (i in 0 until n) if (sh[i] != src[off + i]) { diff = true; break }
        if (!diff) return false
        System.arraycopy(src, off, sh, 0, n); counters.uniformCalls++; return true
    }
    fun setMat4(slot: Int, v: FloatArray, off: Int = 0) { if (changed(slot, v, off, 16)) GLES30.glUniformMatrix4fv(locs[slot], 1, false, v, off) }
    fun setVec3(slot: Int, v: FloatArray) { if (changed(slot, v, 0, 3)) GLES30.glUniform3fv(locs[slot], 1, v, 0) }
    fun setVec4(slot: Int, v: FloatArray) { if (changed(slot, v, 0, 4)) GLES30.glUniform4fv(locs[slot], 1, v, 0) }
    fun setFloat(slot: Int, v: Float) { one[0] = v; if (changed(slot, one, 0, 1)) GLES30.glUniform1f(locs[slot], v) }
    /** Uploads [joints] bone rows (3 vec4 each); the caller tracks [boneStamp]. */
    fun setBones(rows: FloatArray, joints: Int) { if (locs[ShaderSources.U_BONES] >= 0) { counters.uniformCalls++; GLES30.glUniform4fv(locs[ShaderSources.U_BONES], joints * 3, rows, 0) } }

    private fun compile(type: Int, src: String, what: String): Int {
        val s = GLES30.glCreateShader(type)
        if (s == 0) throw IllegalStateException("glCreateShader failed for $what ($label), GL error ${GLES30.glGetError()}")
        GLES30.glShaderSource(s, src); GLES30.glCompileShader(s)
        val ok = IntArray(1); GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) { val log = GLES30.glGetShaderInfoLog(s); GLES30.glDeleteShader(s); throw IllegalStateException("Shader compile failed ($what, $label): $log") }
        return s
    }
}

/** Programs by variant key. One cache can be shared by every model on the GL context; [release] deletes them all. */
internal class ProgramCache {
    val counters = GlCounters()
    private val programs = HashMap<Long, GlProgram>()
    fun get(flags: Int, skinned: Boolean, maxJoints: Int): GlProgram {
        val key = ShaderSources.variantKey(flags, skinned, maxJoints)
        return programs.getOrPut(key) { GlProgram(ShaderSources.vertex(flags, skinned, maxJoints), ShaderSources.fragment(flags), ShaderSources.label(flags, skinned), counters) }
    }
    val size get() = programs.size
    fun release() { for (p in programs.values) p.delete(); programs.clear() }
}
