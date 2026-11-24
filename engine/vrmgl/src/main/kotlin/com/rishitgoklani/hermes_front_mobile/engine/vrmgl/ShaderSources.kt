package com.rishitgoklani.hermes_front_mobile.engine.vrmgl

/** Feature flags that select a shader variant. A variant is compiled with #define lines, so the shader has no uniform branching. */
internal object ShaderSources {
    const val FLAG_BASE_TEX = 1; const val FLAG_SHADE_TEX = 2; const val FLAG_EMISSIVE_TEX = 4; const val FLAG_UNLIT = 8; const val FLAG_MASK = 16; const val FLAG_OUTLINE = 32
    private const val TEX_BITS = FLAG_BASE_TEX or FLAG_SHADE_TEX or FLAG_EMISSIVE_TEX

    /** Drops flags that cannot change the output so equivalent materials share one program (outlines only need the alpha texture). */
    fun normalize(flags: Int): Int {
        if (flags and FLAG_OUTLINE != 0) {
            val alphaTex = if (flags and FLAG_MASK != 0 && flags and FLAG_BASE_TEX != 0) FLAG_MASK or FLAG_BASE_TEX else flags and FLAG_MASK
            return FLAG_OUTLINE or alphaTex
        }
        var f = flags
        if (f and FLAG_UNLIT != 0) f = f and FLAG_SHADE_TEX.inv()
        return f
    }

    fun variantKey(flags: Int, skinned: Boolean, maxJoints: Int): Long =
        normalize(flags).toLong() or (if (skinned) 1L shl 8 else 0L) or ((if (skinned) maxOf(1, maxJoints) else 0).toLong() shl 16)

    fun label(flags: Int, skinned: Boolean) = buildString {
        append(if (skinned) "skinned" else "static")
        for ((bit, n) in listOf(FLAG_BASE_TEX to "base", FLAG_SHADE_TEX to "shade", FLAG_EMISSIVE_TEX to "emissive", FLAG_UNLIT to "unlit", FLAG_MASK to "mask", FLAG_OUTLINE to "outline"))
            if (normalize(flags) and bit != 0) append('+').append(n)
    }

    private fun defines(flags: Int): String = buildString {
        val f = normalize(flags)
        if (f and FLAG_BASE_TEX != 0) append("#define BASE_TEX\n")
        if (f and FLAG_SHADE_TEX != 0) append("#define SHADE_TEX\n")
        if (f and FLAG_EMISSIVE_TEX != 0) append("#define EM_TEX\n")
        if (f and FLAG_UNLIT != 0) append("#define UNLIT\n")
        if (f and FLAG_MASK != 0) append("#define MASK\n")
        if (f and FLAG_OUTLINE != 0) append("#define OUTLINE\n")
        if (f and TEX_BITS != 0) append("#define HAS_UV\n")
    }

    fun vertex(flags: Int, skinned: Boolean, maxJoints: Int): String = buildString {
        append("#version 300 es\n").append(defines(flags))
        if (skinned) append("#define SKINNED\n#define MAXJ ${maxOf(1, maxJoints)}\n")
        append("""
        layout(location=0) in vec3 aPos;
        layout(location=1) in vec4 aNormal;
        layout(location=2) in vec2 aUv;
        #ifdef SKINNED
        layout(location=3) in uvec4 aJoints;
        layout(location=4) in vec4 aWeights;
        uniform vec4 uBones[3 * MAXJ];
        void bone(uint j, float w, vec4 pp, vec3 n, inout vec3 sp, inout vec3 sn) {
            int b = int(j) * 3; vec4 r0 = uBones[b]; vec4 r1 = uBones[b + 1]; vec4 r2 = uBones[b + 2];
            sp += w * vec3(dot(r0, pp), dot(r1, pp), dot(r2, pp));
            sn += w * vec3(dot(r0.xyz, n), dot(r1.xyz, n), dot(r2.xyz, n));
        }
        #endif
        uniform mat4 uVP; uniform mat4 uModel;
        #ifdef OUTLINE
        uniform float uOutline;
        #else
        out vec3 vN;
        #endif
        #if defined(HAS_UV) || defined(MASK)
        out highp vec2 vUv;
        #endif
        void main() {
            vec3 p = aPos; vec3 n = aNormal.xyz;
        #ifdef SKINNED
            vec3 sp = vec3(0.0); vec3 sn = vec3(0.0); vec4 pp = vec4(aPos, 1.0);
            bone(aJoints.x, aWeights.x, pp, n, sp, sn); bone(aJoints.y, aWeights.y, pp, n, sp, sn);
            bone(aJoints.z, aWeights.z, pp, n, sp, sn); bone(aJoints.w, aWeights.w, pp, n, sp, sn);
            p = sp; n = sn;
        #endif
            n = normalize(n);
        #ifdef OUTLINE
            p += n * uOutline;
        #else
            vN = mat3(uModel) * n;
        #endif
        #if defined(HAS_UV) || defined(MASK)
            vUv = aUv;
        #endif
            gl_Position = uVP * uModel * vec4(p, 1.0);
        }
        """.trimIndent())
    }

    fun fragment(flags: Int): String = buildString {
        append("#version 300 es\n").append(defines(flags))
        append("""
        precision mediump float;
        out vec4 o;
        #ifdef OUTLINE
        uniform vec3 uOutlineColor;
        #else
        in vec3 vN;
        uniform vec3 uShade; uniform vec3 uEmissive; uniform vec3 uLight; uniform float uShift; uniform float uToony;
        #endif
        #if defined(HAS_UV) || defined(MASK)
        in highp vec2 vUv;
        #endif
        uniform vec4 uBase; uniform float uCutoff;
        #ifdef BASE_TEX
        uniform sampler2D uBaseTex;
        #endif
        #ifdef SHADE_TEX
        uniform sampler2D uShadeTex;
        #endif
        #ifdef EM_TEX
        uniform sampler2D uEmTex;
        #endif
        void main() {
            vec4 base = uBase;
        #ifdef BASE_TEX
            vec4 bt = texture(uBaseTex, vUv); base *= bt;
        #endif
        #ifdef MASK
            if (base.a < uCutoff) discard;
        #endif
        #ifdef OUTLINE
            o = vec4(uOutlineColor, 1.0);
        #else
            vec3 col = base.rgb;
        #ifndef UNLIT
            vec3 n = normalize(vN); if (!gl_FrontFacing) n = -n;
            float d = dot(n, uLight) + uShift;
            float s = smoothstep(-uToony, uToony, d);
            vec3 sh = uShade;
        #if defined(SHADE_TEX)
            sh *= texture(uShadeTex, vUv).rgb;
        #elif defined(BASE_TEX)
            sh *= bt.rgb;
        #endif
            col = mix(sh, base.rgb, s);
        #endif
        #ifdef EM_TEX
            col += uEmissive * texture(uEmTex, vUv).rgb;
        #else
            col += uEmissive;
        #endif
            o = vec4(col, base.a);
        #endif
        }
        """.trimIndent())
    }

    /** Uniform names in slot order; GlProgram resolves each location once at link time. uToony carries the precomputed edge half-width. */
    val UNIFORMS = arrayOf("uVP", "uModel", "uLight", "uBase", "uShade", "uEmissive", "uOutlineColor", "uShift", "uToony", "uCutoff", "uOutline", "uBones", "uBaseTex", "uShadeTex", "uEmTex")
    const val U_VP = 0; const val U_MODEL = 1; const val U_LIGHT = 2; const val U_BASE = 3; const val U_SHADE = 4; const val U_EMISSIVE = 5; const val U_OUTLINE_COLOR = 6
    const val U_SHIFT = 7; const val U_TOONY = 8; const val U_CUTOFF = 9; const val U_OUTLINE = 10; const val U_BONES = 11; const val U_BASE_TEX = 12; const val U_SHADE_TEX = 13; const val U_EM_TEX = 14
    /** Float count of the shadow copy per slot (0 = not shadow-cached). */
    val SHADOW_SIZES = intArrayOf(16, 16, 3, 4, 3, 3, 3, 1, 1, 1, 1, 0, 0, 0, 0)
}
