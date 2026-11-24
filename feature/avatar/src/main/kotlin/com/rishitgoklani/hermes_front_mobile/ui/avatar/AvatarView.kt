package com.rishitgoklani.hermes_front_mobile.ui.avatar

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.webkit.WebViewAssetLoader
import com.rishitgoklani.hermes_front_mobile.domain.avatar.AvatarState
import com.rishitgoklani.hermes_front_mobile.domain.avatar.PoseParser
import java.io.File

/** A model for the viewer to show or stage: [key] is the library id, [url] is served by the asset loader, [cap] the texture size ceiling. */
data class ModelRequest(val key: String, val url: String, val cap: Int)

/** What the viewer tells the app (all delivered on the main thread). */
interface ViewerEvents {
    fun onShown(key: String, ms: Long, wasStaged: Boolean) {}
    fun onStaged(key: String, ms: Long) {}
    fun onError(key: String, message: String) {}
    fun onNodes(names: List<String>) {}
    fun onHead(x: Float, y: Float, size: Float) {}
}

/** Lets other code ask the live viewer for a small PNG of the current frame (used for library thumbnails). */
class AvatarSnapshots {
    internal var capture: (((ByteArray?) -> Unit) -> Unit)? = null
    suspend fun take(): ByteArray? = kotlinx.coroutines.suspendCancellableCoroutine { cont -> val c = capture; if (c == null) cont.resume(null) {} else c { cont.resume(it) {} } }
}

fun libraryModelUrl(id: String) = "https://appassets.androidplatform.net/library/$id.vrm"

/**
 * 3D VRM avatar (#423): three.js + three-vrm inside ONE WebView that stays alive while the slot is an avatar. The viewer keeps the active
 * model and (at most) one staged model; this composable only forwards [active]/[staged] requests and relays events. The WebView can only
 * reach bundled assets, the clips folder (/model/) and the library folder (/library/) through WebViewAssetLoader; no file:// access.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebAvatarView(
    state: AvatarState, clipsDir: File, libraryDir: File, active: ModelRequest?, staged: ModelRequest?, events: ViewerEvents,
    modifier: Modifier = Modifier, snapshots: AvatarSnapshots? = null,
) {
    val ev = rememberUpdatedState(events)
    val latestState = rememberUpdatedState(state)
    val latestActive = rememberUpdatedState(active)
    val latestStaged = rememberUpdatedState(staged)
    var web by remember { mutableStateOf<WebView?>(null) }
    var pageReady by remember { mutableStateOf(false) }

    fun push(w: WebView) {
        val a = latestActive.value; val s = latestStaged.value
        w.evaluateJavascript(if (a != null) "window.avatar.setModel(${q(a.key)},${q(a.url)},${a.cap})" else "window.avatar.unload()", null)
        if (a != null && s != null) w.evaluateJavascript("window.avatar.stage(${q(s.key)},${q(s.url)},${s.cap})", null)
    }

    AndroidView(modifier = modifier.fillMaxSize(), factory = { ctx ->
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
            .addPathHandler("/model/", WebViewAssetLoader.InternalStoragePathHandler(ctx, clipsDir.also { it.mkdirs() }))
            .addPathHandler("/library/", WebViewAssetLoader.InternalStoragePathHandler(ctx, libraryDir.also { it.mkdirs() }))
            .build()
        WebView(ctx).apply {
            addJavascriptInterface(object {
                @android.webkit.JavascriptInterface fun onHead(x: Double, y: Double, h: Double) { post { ev.value.onHead(x.toFloat(), y.toFloat(), h.toFloat()) } }
                @android.webkit.JavascriptInterface fun onModel(key: String, ms: Double, staged: Boolean) { post {
                    ev.value.onShown(key, ms.toLong(), staged)
                    evaluateJavascript("window.avatar.listBones()") { r -> runCatching { ev.value.onNodes(parseNames(r)) } }
                } }
                @android.webkit.JavascriptInterface fun onStaged(key: String, ms: Double) { post { ev.value.onStaged(key, ms.toLong()) } }
                @android.webkit.JavascriptInterface fun onError(key: String, message: String) { post { ev.value.onError(key, message) } }
            }, "Native")
            settings.javaScriptEnabled = true; settings.allowFileAccess = false; settings.allowContentAccess = false; settings.blockNetworkLoads = false
            webViewClient = object : WebViewClient() {
                // The page may finish loading after the first state was pushed, so send the latest state and models again.
                override fun onPageFinished(view: WebView, url: String?) { view.evaluateJavascript("window.avatar&&window.avatar.apply(${PoseParser.toJson(latestState.value)})", null); pageReady = true; push(view) }
                override fun shouldInterceptRequest(view: WebView, request: android.webkit.WebResourceRequest) = loader.shouldInterceptRequest(request.url)
            }
            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onConsoleMessage(m: android.webkit.ConsoleMessage): Boolean { android.util.Log.d("DeskbotAvatar", "${m.messageLevel()} ${m.message()} (${m.sourceId()}:${m.lineNumber()})"); return true }
            }
            setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
            setBackgroundColor(0xFF05070A.toInt())
            loadUrl("https://appassets.androidplatform.net/assets/avatar/viewer.html"); web = this
        }
    }, onRelease = { snapshots?.capture = null; it.destroy() })

    LaunchedEffect(active, staged, pageReady, web) { val w = web; if (w != null && pageReady) push(w) }

    DisposableEffect(web) {
        val w = web
        snapshots?.capture = { done -> if (w == null) done(null) else w.evaluateJavascript("window.avatar.thumb(256)") { r ->
            val b64 = r?.substringAfter("base64,", "")?.trimEnd('"').orEmpty()
            done(if (b64.isEmpty()) null else runCatching { android.util.Base64.decode(b64, android.util.Base64.DEFAULT) }.getOrNull())
        } }
        onDispose { snapshots?.capture = null }
    }

    // Stop drawing (and let the WebView idle) whenever the app is not in the foreground.
    val ctx = androidx.compose.ui.platform.LocalContext.current
    DisposableEffect(web) {
        val owner = ctx as? androidx.lifecycle.LifecycleOwner
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            when (e) { androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> { web?.onPause(); web?.evaluateJavascript("window.avatar&&window.avatar.pause&&window.avatar.pause(true)", null) }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> { web?.onResume(); web?.evaluateJavascript("window.avatar&&window.avatar.pause&&window.avatar.pause(false)", null) }
                else -> {} }
        }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs) }
    }
    LaunchedEffect(state, web) { web?.evaluateJavascript("window.avatar&&window.avatar.apply(${PoseParser.toJson(state)})", null) }
}

private fun q(s: String) = kotlinx.serialization.json.JsonPrimitive(s).toString()
private fun parseNames(r: String?): List<String> {
    val inner = (kotlinx.serialization.json.Json.parseToJsonElement(r ?: return emptyList()) as kotlinx.serialization.json.JsonPrimitive).content
    return (kotlinx.serialization.json.Json.parseToJsonElement(inner) as kotlinx.serialization.json.JsonArray).map { (it as kotlinx.serialization.json.JsonPrimitive).content }
}
