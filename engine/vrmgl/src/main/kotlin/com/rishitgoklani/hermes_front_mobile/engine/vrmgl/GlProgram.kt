package com.rishitgoklani.hermes_front_mobile.engine.vrmgl

import android.opengl.GLES30

internal class GlProgram(vertex: String, fragment: String) {
    val id: Int
    private val locs = HashMap<String, Int>()

    init {
        val vs = compile(GLES30.GL_VERTEX_SHADER, vertex); val fs = compile(GLES30.GL_FRAGMENT_SHADER, fragment)
        id = GLES30.glCreateProgram(); GLES30.glAttachShader(id, vs); GLES30.glAttachShader(id, fs); GLES30.glLinkProgram(id)
        val ok = IntArray(1); GLES30.glGetProgramiv(id, GLES30.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) { val log = GLES30.glGetProgramInfoLog(id); GLES30.glDeleteProgram(id); throw IllegalStateException("Shader link failed: $log") }
        GLES30.glDeleteShader(vs); GLES30.glDeleteShader(fs)
    }

    fun use() = GLES30.glUseProgram(id)
    fun loc(name: String): Int = locs.getOrPut(name) { GLES30.glGetUniformLocation(id, name) }
    fun delete() = GLES30.glDeleteProgram(id)

    private fun compile(type: Int, src: String): Int {
        val s = GLES30.glCreateShader(type); GLES30.glShaderSource(s, src); GLES30.glCompileShader(s)
        val ok = IntArray(1); GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) { val log = GLES30.glGetShaderInfoLog(s); GLES30.glDeleteShader(s); throw IllegalStateException("Shader compile failed: $log") }
        return s
    }
}
