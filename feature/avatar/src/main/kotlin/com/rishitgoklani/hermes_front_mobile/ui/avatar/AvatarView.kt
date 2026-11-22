package com.rishitgoklani.hermes_front_mobile.ui.avatar

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.rishitgoklani.hermes_front_mobile.domain.avatar.PoseParser
import com.rishitgoklani.hermes_front_mobile.domain.avatar.AvatarState
import java.io.File

/**
 * 3D VRM avatar (#423): three.js + three-vrm inside a WebView that can only reach bundled assets and the avatar
 * folder (files/avatar/model.vrm) through WebViewAssetLoader; no network, no file:// access.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AvatarView(state: AvatarState, avatarDir: File, modifier: Modifier = Modifier, onNodes: (List<String>) -> Unit = {}) {
    val hasModel = File(avatarDir, "model.vrm").isFile
    if (!hasModel) { Box(modifier.fillMaxSize(), Alignment.Center) { Text("No avatar model. Put a .vrm at ${avatarDir.path}/model.vrm", color = Color.Gray) }; return }
    var web by remember { mutableStateOf<WebView?>(null) }
    val latest = androidx.compose.runtime.rememberUpdatedState(state)
    AndroidView(modifier = modifier.fillMaxSize(), factory = { ctx ->
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
            .addPathHandler("/model/", WebViewAssetLoader.InternalStoragePathHandler(ctx, avatarDir.also { it.mkdirs() }))
            .build()
        WebView(ctx).apply {
            settings.javaScriptEnabled = true; settings.allowFileAccess = false; settings.allowContentAccess = false; settings.blockNetworkLoads = false
            webViewClient = object : WebViewClient() {
                // The page may finish loading after the first state was pushed, so send the latest state again.
                override fun onPageFinished(view: WebView, url: String?) { view.evaluateJavascript("window.avatar&&window.avatar.apply(${PoseParser.toJson(latest.value)})", null) }
                override fun shouldInterceptRequest(view: WebView, request: android.webkit.WebResourceRequest) = loader.shouldInterceptRequest(request.url)
            }
            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onConsoleMessage(m: android.webkit.ConsoleMessage): Boolean { android.util.Log.d("DeskbotAvatar", "${m.messageLevel()} ${m.message()} (${m.sourceId()}:${m.lineNumber()})"); return true }
                override fun onReceivedTitle(view: WebView, title: String) {
                    android.util.Log.d("DeskbotAvatar", "title=$title")
                    if (title == "ready") view.evaluateJavascript("window.avatar.listBones()") { r -> runCatching { onNodes(kotlinx.serialization.json.Json.parseToJsonElement(kotlinx.serialization.json.Json.parseToJsonElement(r).let { (it as kotlinx.serialization.json.JsonPrimitive).content }).let { (it as kotlinx.serialization.json.JsonArray).map { n -> (n as kotlinx.serialization.json.JsonPrimitive).content } }) } }
                }
            }
            setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
            setBackgroundColor(0xFF05070A.toInt())
            loadUrl("https://appassets.androidplatform.net/assets/avatar/viewer.html"); web = this
        }
    }, onRelease = { it.destroy() })
    // Test hook: `touch files/avatar/snap.request` and the viewer writes snap.png (the compositor cannot screenshot WebGL on some phones).
    LaunchedEffect(web) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            val req = File(avatarDir, "snap.request")
            if (web != null && req.exists()) {
                req.delete()
                web?.evaluateJavascript("window.avatar.snap()") { r ->
                    val b64 = r?.substringAfter("base64,", "")?.trimEnd('"').orEmpty()
                    if (b64.isNotEmpty()) File(avatarDir, "snap.png").writeBytes(android.util.Base64.decode(b64, android.util.Base64.DEFAULT))
                }
            }
        }
    }
    // Stop drawing (and let the WebView idle) whenever the app is not in the foreground.
    val ctx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.DisposableEffect(web) {
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
