package com.rishitgoklani.hermes_front_mobile.engine.vrmgl

/** Kept for source compatibility; variants are built by [ShaderSources] and cached by [ProgramCache]. */
internal object Shaders {
    const val FLAG_BASE_TEX = ShaderSources.FLAG_BASE_TEX; const val FLAG_SHADE_TEX = ShaderSources.FLAG_SHADE_TEX; const val FLAG_EMISSIVE_TEX = ShaderSources.FLAG_EMISSIVE_TEX
    const val FLAG_UNLIT = ShaderSources.FLAG_UNLIT; const val FLAG_MASK = ShaderSources.FLAG_MASK; const val FLAG_OUTLINE = ShaderSources.FLAG_OUTLINE
    fun vertex(skinned: Boolean, maxJoints: Int): String = ShaderSources.vertex(0, skinned, maxJoints)
    val FRAGMENT: String get() = ShaderSources.fragment(0)
}
