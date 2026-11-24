package com.rishitgoklani.hermes_front_mobile.domain.avatar

/**
 * Refuses models that cannot render on this phone. (A model that kills the app is handled by the library: PENDING -> BROKEN.) The phone has ~3.5 GB RAM, and a VRM with big textures
 * makes the WebView exhaust memory and the process dies without a Java crash. Big textures are shrunk by the viewer
 * ([TextureBudget] picks the size); only models that cannot fit even then are refused.
 */
object AvatarModelGuard {
    const val MAX_FILE_BYTES = 50 * 1024 * 1024

    /** Null when the model can be rendered (possibly with shrunk textures), otherwise the reason to refuse it. */
    fun problem(bytes: ByteArray, budget: TextureBudget = TextureBudget()): String? =
        if (bytes.size > MAX_FILE_BYTES) "model is ${"%.1f".format(bytes.size / 1048576.0)} MB; the limit is ${MAX_FILE_BYTES / 1048576} MB" else budget.problem(bytes)

    /** [budget] defaults to the fixed 48 MB policy; pass [TextureBudget.forDevice] where the phone's RAM is known. */
    fun plan(bytes: ByteArray, budget: TextureBudget = TextureBudget()): TexturePlan = budget.plan(bytes)
    /** Texture size cap the viewer should apply to this model. */
    fun textureCap(bytes: ByteArray, budget: TextureBudget = TextureBudget()): Int = plan(bytes, budget).cap
}
