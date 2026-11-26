package com.rishitgoklani.hermes_front_mobile.ui.avatar

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.rishitgoklani.hermes_front_mobile.domain.avatar.AvatarState
import com.rishitgoklani.hermes_front_mobile.domain.avatar.DeviceTextureBudget
import com.rishitgoklani.hermes_front_mobile.domain.face.Effect
import com.rishitgoklani.hermes_front_mobile.engine.vrmgl.ModelSpec
import com.rishitgoklani.hermes_front_mobile.domain.DisplayQuality
import com.rishitgoklani.hermes_front_mobile.engine.vrmgl.QualityLevel
import com.rishitgoklani.hermes_front_mobile.engine.vrmgl.VrmEvents
import com.rishitgoklani.hermes_front_mobile.engine.vrmgl.VrmGlView
import java.io.File

/**
 * The 3D avatar, drawn natively with the in-house VRM engine (OpenGL ES): no WebView, no JavaScript heap, no duplicate image copies.
 * Same contract as the old web viewer: state in, [ModelRequest]s for the active and next model, events out.
 */
@Composable
fun AvatarView(
    state: AvatarState, clipsDir: File, libraryDir: File, active: ModelRequest?, staged: ModelRequest?, events: ViewerEvents,
    modifier: Modifier = Modifier, snapshots: AvatarSnapshots? = null, displayQuality: DisplayQuality = DisplayQuality.Auto,
    /** On-face effects to show and what the bot is doing ("thinking", "listening", ...), on top of the mood and reaction in [state]. */
    effects: Set<Effect> = emptySet(), activity: String = "",
    /** Model prepared at app start (taken by the view instead of loading again) and where to keep posters of what is shown. */
    startup: AvatarStartup? = null, posters: AvatarPosters? = null,
) {
    val ev = rememberUpdatedState(events)
    var view by remember { mutableStateOf<VrmGlView?>(null) }
    var shownKey by remember { mutableStateOf<String?>(null) }
    fun spec(r: ModelRequest?) = r?.let { startup?.spec(it.key, it.cap) ?: ModelSpec(it.key, File(libraryDir, "${it.key}.vrm"), it.cap) }

    AndroidView(modifier = modifier.fillMaxSize(), factory = { ctx ->
        VrmGlView(ctx, textureBudgetBytes = deviceTextureBudget(ctx)).also { v ->
            v.clipsDir = File(clipsDir, "clips")
            v.preloader = startup?.preloader; v.cache = startup?.cache   // clips live in <avatar dir>/clips (the web viewer served them under /model/clips/)
            v.events = object : VrmEvents {
                override fun onShown(key: String, ms: Long, wasStaged: Boolean) { shownKey = key; ev.value.onShown(key, ms, wasStaged) }
                override fun onStaged(key: String, ms: Long) = ev.value.onStaged(key, ms)
                override fun onError(key: String, message: String) = ev.value.onError(key, message)
                override fun onNodes(names: List<String>) = ev.value.onNodes(names)
                override fun onHead(x: Float, y: Float, size: Float) = ev.value.onHead(x, y, size)
                override fun onFatal(message: String) = ev.value.onFatal(message)
                override fun onFaceEffectsUnavailable(message: String) = ev.value.onFaceEffectsUnavailable(message)
            }
            v.onResume(); view = v
        }
    }, onRelease = { snapshots?.capture = null; it.release() })

    LaunchedEffect(active, staged, view) { view?.want(spec(active), spec(staged)) }
    LaunchedEffect(state, effects, activity, view) { view?.setInput(EngineInputMapper.input(state, effects, activity)) }
    // A few seconds after an avatar appears (settled in its idle pose), keep a poster of it for the next app start.
    LaunchedEffect(shownKey, view) {
        val key = shownKey ?: return@LaunchedEffect; val v = view ?: return@LaunchedEffect; val store = posters ?: return@LaunchedEffect
        kotlinx.coroutines.delay(2500)
        val landscape = v.width > v.height
        v.capturePoster(0.5f) { jpeg -> if (jpeg != null) Thread { store.save(key, landscape, jpeg) }.start() }
    }
    DisposableEffect(view) {
        val w = view
        snapshots?.capture = { done -> if (w == null) done(null) else w.snapshot(256, done) }
        onDispose { snapshots?.capture = null }
    }
    val ctx = LocalContext.current
    // Memory pressure: Android asks apps to give memory back; the view drops the staged model, or everything when hidden.
    DisposableEffect(view) {
        val w = view
        val callbacks = object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) { w?.onTrimMemory(level) }
            override fun onConfigurationChanged(newConfig: Configuration) {}
            @Deprecated("Replaced by onTrimMemory") override fun onLowMemory() { w?.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) }
        }
        val app = ctx.applicationContext
        app.registerComponentCallbacks(callbacks)
        onDispose { app.unregisterComponentCallbacks(callbacks) }
    }
    DisposableEffect(view) {
        val owner = ctx as? LifecycleOwner
        val obs = LifecycleEventObserver { _, e -> when (e) { Lifecycle.Event.ON_PAUSE -> view?.onPause(); Lifecycle.Event.ON_RESUME -> view?.onResume(); else -> {} } }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs) }
    }
}

/** The texture memory the phone can spare for avatars: see [DeviceTextureBudget]. */
internal fun deviceTextureBudget(ctx: Context): Long {
    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager; val info = ActivityManager.MemoryInfo(); am.getMemoryInfo(info)
    return DeviceTextureBudget.bytes(info.totalMem, am.isLowRamDevice)
}

/** Settings choice -> renderer level. Pure, so it is unit-tested. */
internal fun renderLevel(q: DisplayQuality): QualityLevel = when (q) { DisplayQuality.Low -> QualityLevel.Low; DisplayQuality.High -> QualityLevel.High; DisplayQuality.Auto -> QualityLevel.Auto }
