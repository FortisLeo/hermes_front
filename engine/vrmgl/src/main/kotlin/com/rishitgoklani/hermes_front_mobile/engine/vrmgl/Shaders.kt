package com.rishitgoklani.hermes_front_mobile.engine.vrmgl

/** Toon shading in the spirit of MToon (two-tone lit/shade with a soft step), small enough for a low-end GPU. */
internal object Shaders {
    const val FLAG_BASE_TEX = 1; const val FLAG_SHADE_TEX = 2; const val FLAG_EMISSIVE_TEX = 4; const val FLAG_UNLIT = 8; const val FLAG_MASK = 16; const val FLAG_OUTLINE = 32

    fun vertex(skinned: Boolean, maxJoints: Int): String = buildString {
        append("#version 300 es\n")
        if (skinned) append("#define SKINNED\n#define MAXJ ${maxOf(1, maxJoints)}\n")
        append("""
        layout(location=0) in vec3 aPos;
        layout(location=1) in vec4 aNormal;
        layout(location=2) in vec2 aUv;
        #ifdef SKINNED
        layout(location=3) in uvec4 aJoints;
        layout(location=4) in vec4 aWeights;
        uniform vec4 uBones[3 * MAXJ];
        #endif
        uniform mat4 uVP; uniform mat4 uModel; uniform float uOutline;
        out vec3 vN; out vec2 vUv;
        void main() {
            vec3 p = aPos; vec3 n = aNormal.xyz;
        #ifdef SKINNED
            vec3 sp = vec3(0.0); vec3 sn = vec3(0.0); vec4 pp = vec4(aPos, 1.0);
            for (int i = 0; i < 4; i++) {
                float w = aWeights[i]; if (w <= 0.0) continue;
                int b = int(aJoints[i]) * 3; vec4 r0 = uBones[b]; vec4 r1 = uBones[b + 1]; vec4 r2 = uBones[b + 2];
                sp += w * vec3(dot(r0, pp), dot(r1, pp), dot(r2, pp));
                sn += w * vec3(dot(r0.xyz, n), dot(r1.xyz, n), dot(r2.xyz, n));
            }
            p = sp; n = sn;
        #endif
            n = normalize(n); p += n * uOutline;
            gl_Position = uVP * uModel * vec4(p, 1.0);
            vN = mat3(uModel) * n; vUv = aUv;
        }
        """.trimIndent())
    }

    val FRAGMENT = """#version 300 es
        precision mediump float;
        in vec3 vN; in vec2 vUv; out vec4 o;
        uniform vec4 uBase; uniform vec3 uShade; uniform vec3 uEmissive; uniform vec3 uOutlineColor; uniform vec3 uLight;
        uniform float uShift; uniform float uToony; uniform float uCutoff; uniform int uFlags;
        uniform sampler2D uBaseTex; uniform sampler2D uShadeTex; uniform sampler2D uEmTex;
        void main() {
            vec4 base = uBase; if ((uFlags & 1) != 0) base *= texture(uBaseTex, vUv);
            if ((uFlags & 16) != 0 && base.a < uCutoff) discard;
            if ((uFlags & 32) != 0) { o = vec4(uOutlineColor, 1.0); return; }
            vec3 col = base.rgb;
            if ((uFlags & 8) == 0) {
                vec3 n = normalize(vN); if (!gl_FrontFacing) n = -n;
                float d = dot(n, uLight) + uShift; float f = max(1.0 - uToony, 0.02) * 0.5; float s = smoothstep(-f, f, d);
                vec3 sh = uShade; if ((uFlags & 2) != 0) sh *= texture(uShadeTex, vUv).rgb; else if ((uFlags & 1) != 0) sh *= texture(uBaseTex, vUv).rgb;
                col = mix(sh, base.rgb, s);
            }
            if ((uFlags & 4) != 0) col += uEmissive * texture(uEmTex, vUv).rgb; else col += uEmissive;
            o = vec4(col, base.a);
        }
    """.trimIndent()
}
