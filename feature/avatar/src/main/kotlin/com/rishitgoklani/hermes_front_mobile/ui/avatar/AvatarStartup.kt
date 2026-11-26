package com.rishitgoklani.hermes_front_mobile.ui.avatar

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.rishitgoklani.hermes_front_mobile.engine.vrmgl.ModelCache
import com.rishitgoklani.hermes_front_mobile.engine.vrmgl.ModelPreloader
import com.rishitgoklani.hermes_front_mobile.engine.vrmgl.ModelSpec
import com.rishitgoklani.hermes_front_mobile.engine.vrmgl.VrmGlView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * What the app prepares the moment the activity is created, before any UI exists: the saved avatar's poster (decoded, ready for
 * the first frame) and its model (parsed and decoded, handed to the view when it asks). [start] runs on a background thread.
 */
class AvatarStartup(context: Context, private val posters: AvatarPosters, private val libraryDir: File) {
    /** Parsed models and ETC2 textures on disk (rebuildable, so in the cache directory). */
    internal val cache = ModelCache(File(context.cacheDir, "vrm"))
    internal val preloader = ModelPreloader(VrmGlView.defaultConfig(context), deviceTextureBudget(context), cache)
    private class Poster(val key: String, val landscape: Boolean, val image: ImageBitmap)
    private val _poster = MutableStateFlow<Poster?>(null)
    /** Key of the avatar whose poster [start] decoded (the one the app opens with), or null. */
    val posterKey: StateFlow<String?> get() = _posterKey
    private val _posterKey = MutableStateFlow<String?>(null)

    /** Prepare avatar [id] (texture cap [cap]) for a screen in [landscape] or portrait: poster first (the first frame needs it), then the model. Blocking. */
    fun start(id: String, cap: Int, landscape: Boolean) {
        val p = _poster.value
        if (p == null || p.key != id || p.landscape != landscape) posters.read(id, landscape)?.let { b ->
            BitmapFactory.decodeByteArray(b, 0, b.size)?.let { _poster.value = Poster(id, landscape, it.asImageBitmap()); _posterKey.value = id }
        }
        preloader.preload(spec(id, cap))
    }

    internal fun spec(id: String, cap: Int) = ModelSpec(id, File(libraryDir, "$id.vrm"), cap)
    internal fun posterFor(key: String?, landscape: Boolean): ImageBitmap? = _poster.value?.takeIf { it.key == key && it.landscape == landscape }?.image
}

/**
 * The saved poster of avatar [key] filling the box (stretched exactly like the 3D view, so the two line up when they cross-fade),
 * or nothing when there is none. Uses the poster [startup] already decoded, else decodes the file off the main thread.
 */
@Composable
fun AvatarPoster(key: String?, posters: AvatarPosters, startup: AvatarStartup?, modifier: Modifier = Modifier) = BoxWithConstraints(modifier.fillMaxSize()) {
    val landscape = maxWidth > maxHeight
    val image by produceState(initialValue = startup?.posterFor(key, landscape), key, landscape) {
        if (value == null && key != null) value = withContext(Dispatchers.IO) { posters.read(key, landscape)?.let { b -> BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() } }
    }
    // Only a poster of the same shape lines up with the 3D view (a different window size frames the model differently).
    val box = maxWidth.value / maxHeight.value
    image?.takeIf { kotlin.math.abs(it.width.toFloat() / it.height - box) / box < 0.04f }?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
}
