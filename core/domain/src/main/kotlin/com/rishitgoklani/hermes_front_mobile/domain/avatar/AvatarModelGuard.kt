package com.rishitgoklani.hermes_front_mobile.domain.avatar

/**
 * Keeps a model that kills the app from becoming a crash loop. The phone has ~3.5 GB RAM, and a large VRM (big textures)
 * makes the WebView exhaust memory and the process dies without a Java crash.
 */
object AvatarModelGuard {
    const val MAX_RENDER_BYTES = 12 * 1024 * 1024
    enum class Action { None, RestorePrevious, Remove }

    /** Null when the size is fine, otherwise the reason to refuse the model. */
    fun sizeProblem(bytes: Int): String? =
        if (bytes > MAX_RENDER_BYTES) "model is ${"%.1f".format(bytes / 1048576.0)} MB; this phone can render up to ${MAX_RENDER_BYTES / 1048576} MB (shrink the textures and try again)" else null

    /** [pending] = a newly installed model never got far enough to prove it renders (the app died first). */
    fun onStart(pending: Boolean, hasPrevious: Boolean): Action = when {
        !pending -> Action.None
        hasPrevious -> Action.RestorePrevious
        else -> Action.Remove
    }
}
