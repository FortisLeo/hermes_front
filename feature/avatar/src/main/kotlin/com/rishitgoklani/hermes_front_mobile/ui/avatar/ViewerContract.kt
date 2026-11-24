package com.rishitgoklani.hermes_front_mobile.ui.avatar



/** A model for the viewer to show or stage: [key] is the library id, [url] the (legacy) web URL, [cap] the texture size ceiling in pixels. */
data class ModelRequest(val key: String, val url: String, val cap: Int)

/** What the viewer tells the app (all delivered on the main thread). */
interface ViewerEvents {
    fun onShown(key: String, ms: Long, wasStaged: Boolean) {}
    fun onStaged(key: String, ms: Long) {}
    fun onError(key: String, message: String) {}
    fun onNodes(names: List<String>) {}
    fun onHead(x: Float, y: Float, size: Float) {}
    /** The native renderer failed for good (GL error); the host should fall back to the eyes. */
    fun onFatal(message: String) {}
}

/** Lets other code ask the live viewer for a small PNG of the current frame (used for library thumbnails). */
class AvatarSnapshots {
    internal var capture: (((ByteArray?) -> Unit) -> Unit)? = null
    suspend fun take(): ByteArray? = kotlinx.coroutines.suspendCancellableCoroutine { cont -> val c = capture; if (c == null) cont.resume(null) {} else c { cont.resume(it) {} } }
}

fun libraryModelUrl(id: String) = "https://appassets.androidplatform.net/library/$id.vrm"

