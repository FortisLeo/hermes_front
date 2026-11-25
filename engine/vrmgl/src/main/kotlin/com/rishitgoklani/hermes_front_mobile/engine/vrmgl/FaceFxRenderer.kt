package com.rishitgoklani.hermes_front_mobile.engine.vrmgl

import android.opengl.GLES30
import com.rishitgoklani.hermes_front_mobile.engine.vrm.FaceFxLayout

/**
 * Draws the on-face effects (blush, tears, sweat drop, anger mark) as small procedural quads placed by [FaceFxLayout]. One tiny program, no
 * textures and no vertex buffers: the quad's corners come from gl_VertexID and the shapes are drawn in the fragment shader.
 * Cheeks and tears are depth tested so bangs and hair cover them; the comic symbols are drawn on top. Create and use on the GL thread only.
 */
internal class FaceFxRenderer {
    private val program: Int
    private val uVP: Int; private val uM: Int; private val uKind: Int; private val uAlpha: Int; private val uTime: Int; private val uSide: Int; private val uScale: Int

    init {
        val vs = compile(GLES30.GL_VERTEX_SHADER, VERTEX); val fs = try { compile(GLES30.GL_FRAGMENT_SHADER, FRAGMENT) } catch (e: Throwable) { GLES30.glDeleteShader(vs); throw e }
        val p = GLES30.glCreateProgram(); GLES30.glAttachShader(p, vs); GLES30.glAttachShader(p, fs); GLES30.glLinkProgram(p)
        val ok = IntArray(1); GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
        val log = if (ok[0] == 0) GLES30.glGetProgramInfoLog(p) else ""
        GLES30.glDeleteShader(vs); GLES30.glDeleteShader(fs)
        if (ok[0] == 0) { GLES30.glDeleteProgram(p); throw IllegalStateException("Face effects shader link failed: $log") }
        program = p
        uVP = GLES30.glGetUniformLocation(p, "uVP"); uM = GLES30.glGetUniformLocation(p, "uM"); uKind = GLES30.glGetUniformLocation(p, "uKind")
        uAlpha = GLES30.glGetUniformLocation(p, "uAlpha"); uTime = GLES30.glGetUniformLocation(p, "uTime"); uSide = GLES30.glGetUniformLocation(p, "uSide"); uScale = GLES30.glGetUniformLocation(p, "uScale")
    }

    /** Draws every decal with a weight above zero. Leaves blending off, depth writes on and depth testing on, as the model pass expects. */
    fun draw(layout: FaceFxLayout, vp: FloatArray, timeSec: Float) {
        var any = false
        for (d in layout.decals) if (d.weight > 0.003f) { any = true; break }
        if (!any) return
        GLES30.glUseProgram(program); GLES30.glBindVertexArray(0)
        GLES30.glDisable(GLES30.GL_CULL_FACE); GLES30.glEnable(GLES30.GL_BLEND); GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA); GLES30.glDepthMask(false)
        GLES30.glUniformMatrix4fv(uVP, 1, false, vp, 0); GLES30.glUniform1f(uTime, timeSec)
        var depth = true
        for (d in layout.decals) {
            if (d.weight <= 0.003f) continue
            if (d.surface != depth) { depth = d.surface; if (depth) GLES30.glEnable(GLES30.GL_DEPTH_TEST) else GLES30.glDisable(GLES30.GL_DEPTH_TEST) }
            GLES30.glUniformMatrix4fv(uM, 1, false, d.matrix, 0)
            GLES30.glUniform1f(uKind, d.kind.toFloat()); GLES30.glUniform1f(uAlpha, d.weight); GLES30.glUniform1f(uSide, d.side)
            GLES30.glUniform2f(uScale, d.halfW, d.halfH)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        }
        GLES30.glEnable(GLES30.GL_DEPTH_TEST); GLES30.glDepthMask(true); GLES30.glDisable(GLES30.GL_BLEND); GLES30.glUseProgram(0)
        GlErrors.check("face effects")
    }

    fun release() { GLES30.glDeleteProgram(program) }

    private fun compile(type: Int, src: String): Int {
        val s = GLES30.glCreateShader(type)
        GLES30.glShaderSource(s, src); GLES30.glCompileShader(s)
        val ok = IntArray(1); GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) { val log = GLES30.glGetShaderInfoLog(s); GLES30.glDeleteShader(s); throw IllegalStateException("Face effects shader compile failed: $log") }
        return s
    }

    companion object {
        private val VERTEX = """#version 300 es
            uniform mat4 uVP; uniform mat4 uM;
            out vec2 vP;
            void main() {
                vec2 c = vec2(float(gl_VertexID & 1) * 2.0 - 1.0, float(gl_VertexID >> 1) * 2.0 - 1.0);
                vP = c;
                gl_Position = uVP * uM * vec4(c, 0.0, 1.0);
            }""".trimIndent()

        // Kinds follow FaceMixer: 0 blush, 1 tears, 2 sweat drop, 3 anger mark. Output is premultiplied alpha.
        private val FRAGMENT = """#version 300 es
            precision mediump float;
            in vec2 vP; out vec4 o;
            uniform float uKind; uniform float uAlpha; uniform float uTime; uniform float uSide; uniform vec2 uScale;

            float teardrop(vec2 p) {
                float bulb = length(p - vec2(0.0, -0.25)) - 0.55;
                float tip = max(abs(p.x) - (0.9 - p.y) * 0.32, max(-0.25 - p.y, p.y - 0.9));
                return smoothstep(0.05, -0.03, min(bulb, tip));
            }

            void main() {
                vec2 p = vP; vec3 col = vec3(1.0); float a = 0.0;
                if (uKind < 0.5) {
                    // Blush: a soft pink glow with a few slanted hatch strokes, the way anime cheeks are drawn.
                    vec2 q = vec2(p.x, p.y * 1.5);
                    float r = length(q);
                    float glow = smoothstep(1.0, 0.1, r); glow *= glow;
                    float st = abs(fract((p.x * uSide * 1.2 + p.y * 1.6) * 1.5) - 0.5);
                    float hatch = smoothstep(0.16, 0.08, st) * smoothstep(0.9, 0.5, r);
                    a = clamp(glow * 0.72 + hatch * 0.2, 0.0, 1.0);
                    col = vec3(1.0, 0.36, 0.48);
                } else if (uKind < 1.5) {
                    // Tears: a faint stream from the lower lid with drops running down it. Shapes are in eye-distance units.
                    vec2 w = p * uScale;
                    float y01 = (p.y + 1.0) * 0.5;
                    float body = smoothstep(0.05, 0.02, abs(w.x)) * smoothstep(0.0, 0.3, y01) * 0.25;
                    float t = fract(uTime * 0.8 + uSide * 0.31);
                    float cy = uScale.y * (1.0 - 2.0 * t);
                    float drop = smoothstep(0.1, 0.06, length(vec2(w.x, (w.y - cy) * 0.6))) * smoothstep(0.0, 0.12, t) * smoothstep(0.0, 0.2, 1.0 - t);
                    a = clamp(body + drop * 0.9, 0.0, 1.0);
                    col = mix(vec3(0.5, 0.75, 1.0), vec3(0.9, 0.97, 1.0), drop * 0.6);
                } else if (uKind < 2.5) {
                    // Sweat drop: a bobbing teardrop with a highlight.
                    vec2 s = vec2(p.x, p.y + sin(uTime * 3.0) * 0.06);
                    a = teardrop(s);
                    col = mix(vec3(0.55, 0.8, 1.0), vec3(1.0), smoothstep(0.14, 0.04, length(s - vec2(-0.2, -0.2))) * 0.8);
                } else {
                    // Anger mark: four arcs around a cross-shaped gap, pulsing.
                    vec2 q = abs(p / (1.0 + 0.12 * sin(uTime * 9.0)));
                    float d = abs(length(q - vec2(0.95)) - 0.62) - 0.15;
                    a = smoothstep(0.04, -0.02, d) * step(max(q.x, q.y), 1.0);
                    col = mix(vec3(0.88, 0.1, 0.1), vec3(0.45, 0.0, 0.0), smoothstep(-0.07, 0.0, d));
                }
                a *= uAlpha;
                o = vec4(col * a, a);
            }""".trimIndent()
    }
}
