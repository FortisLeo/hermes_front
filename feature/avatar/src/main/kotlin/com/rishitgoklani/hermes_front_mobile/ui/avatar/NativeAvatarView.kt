package com.rishitgoklani.hermes_front_mobile.ui.avatar

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
import com.rishitgoklani.hermes_front_mobile.domain.avatar.AvatarAnimation
import com.rishitgoklani.hermes_front_mobile.domain.avatar.AvatarPose
import com.rishitgoklani.hermes_front_mobile.domain.avatar.AvatarState
import com.rishitgoklani.hermes_front_mobile.domain.avatar.Vec3
import com.rishitgoklani.hermes_front_mobile.engine.vrm.AnimationSpec
import com.rishitgoklani.hermes_front_mobile.engine.vrm.DriverInput
import com.rishitgoklani.hermes_front_mobile.engine.vrm.KeyframeSpec
import com.rishitgoklani.hermes_front_mobile.engine.vrm.PoseSpec
import com.rishitgoklani.hermes_front_mobile.engine.vrmgl.ModelSpec
import com.rishitgoklani.hermes_front_mobile.engine.vrmgl.VrmEvents
import com.rishitgoklani.hermes_front_mobile.engine.vrmgl.VrmGlView
import java.io.File

/** App state -> engine input. Pure, so it is unit-tested. */
object EngineInputMapper {
    private fun v(x: Vec3) = floatArrayOf(x.x, x.y, x.z)
    fun pose(p: AvatarPose) = PoseSpec(p.bones.mapValues { v(it.value) }, p.expressions, p.hips?.let(::v), p.offsets.mapValues { v(it.value) })
    fun animation(a: AvatarAnimation?) = a?.let { an -> AnimationSpec(an.keyframes.map { KeyframeSpec(it.tMs.toLong(), pose(it.pose)) }, an.loop) }
    fun input(s: AvatarState) = DriverInput(s.mouth, s.gazeX, s.gazeY, s.mood.name.lowercase(), s.blendMs, pose(s.pose), animation(s.animation))
}

/**
 * The 3D avatar, drawn natively with the in-house VRM engine (OpenGL ES): no WebView, no JavaScript heap, no duplicate image copies.
 * Same contract as the old web viewer: state in, [ModelRequest]s for the active and next model, events out.
 */
@Composable
fun AvatarView(
    state: AvatarState, clipsDir: File, libraryDir: File, active: ModelRequest?, staged: ModelRequest?, events: ViewerEvents,
    modifier: Modifier = Modifier, snapshots: AvatarSnapshots? = null,
) {
    val ev = rememberUpdatedState(events)
    var view by remember { mutableStateOf<VrmGlView?>(null) }
    fun spec(r: ModelRequest?) = r?.let { ModelSpec(it.key, File(libraryDir, "${it.key}.vrm"), it.cap) }

    AndroidView(modifier = modifier.fillMaxSize(), factory = { ctx ->
        VrmGlView(ctx).also { v ->
            v.events = object : VrmEvents {
                override fun onShown(key: String, ms: Long, wasStaged: Boolean) = ev.value.onShown(key, ms, wasStaged)
                override fun onStaged(key: String, ms: Long) = ev.value.onStaged(key, ms)
                override fun onError(key: String, message: String) = ev.value.onError(key, message)
                override fun onNodes(names: List<String>) = ev.value.onNodes(names)
                override fun onHead(x: Float, y: Float, size: Float) = ev.value.onHead(x, y, size)
            }
            v.onResume(); view = v
        }
    }, onRelease = { snapshots?.capture = null; it.release() })

    LaunchedEffect(active, staged, view) { view?.want(spec(active), spec(staged)) }
    LaunchedEffect(state, view) { view?.setInput(EngineInputMapper.input(state)) }
    DisposableEffect(view) {
        val w = view
        snapshots?.capture = { done -> if (w == null) done(null) else w.snapshot(256, done) }
        onDispose { snapshots?.capture = null }
    }
    val ctx = LocalContext.current
    DisposableEffect(view) {
        val owner = ctx as? LifecycleOwner
        val obs = LifecycleEventObserver { _, e -> when (e) { Lifecycle.Event.ON_PAUSE -> view?.onPause(); Lifecycle.Event.ON_RESUME -> view?.onResume(); else -> {} } }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs) }
    }
}
