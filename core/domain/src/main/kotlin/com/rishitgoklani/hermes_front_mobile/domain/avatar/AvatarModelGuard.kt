package com.rishitgoklani.hermes_front_mobile.domain.avatar

/**
 * Keeps a model that kills the app from becoming a crash loop. The phone has ~3.5 GB RAM, and a VRM with big textures
 * makes the WebView exhaust memory and the process dies without a Java crash. Big textures are shrunk by the viewer
 * ([TextureBudget] picks the size); only models that cannot fit even then are refused.
 */
object AvatarModelGuard {
    const val MAX_FILE_BYTES = 50 * 1024 * 1024
    private val budget = TextureBudget()
    enum class Action { None, RestorePrevious, Remove }

    /** Null when the model can be rendered (possibly with shrunk textures), otherwise the reason to refuse it. */
    fun problem(bytes: ByteArray): String? =
        if (bytes.size > MAX_FILE_BYTES) "model is ${"%.1f".format(bytes.size / 1048576.0)} MB; the limit is ${MAX_FILE_BYTES / 1048576} MB" else budget.problem(bytes)

    fun plan(bytes: ByteArray): TexturePlan = budget.plan(bytes)
    /** Texture size cap the viewer should apply to this model. */
    fun textureCap(bytes: ByteArray): Int = plan(bytes).cap

    /** [pending] = a newly installed model never got far enough to prove it renders (the app died first). */
    fun onStart(pending: Boolean, hasPrevious: Boolean): Action = when {
        !pending -> Action.None
        hasPrevious -> Action.RestorePrevious
        else -> Action.Remove
    }
}
