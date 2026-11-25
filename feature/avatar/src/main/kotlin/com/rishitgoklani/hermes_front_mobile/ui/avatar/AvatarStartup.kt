package com.rishitgoklani.hermes_front_mobile.ui.avatar

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
    internal val preloader = ModelPreloader(VrmGlView.defaultConfig(context), deviceTextureBudget(context))
    private val _poster = MutableStateFlow<Pair<String, ImageBitmap>?>(null)
    /** The decoded poster of the avatar the app opens with, once [start] has read it. */
    val poster: StateFlow<Pair<String, ImageBitmap>?> = _poster

    /** Prepare avatar [id] (texture cap [cap]): poster first (it is what the first frame needs), then the model. Blocking. */
    fun start(id: String, cap: Int) {
        if (_poster.value?.first != id) posters.read(id)?.let { b -> BitmapFactory.decodeByteArray(b, 0, b.size)?.let { _poster.value = id to it.asImageBitmap() } }
        preloader.preload(spec(id, cap))
    }

    internal fun spec(id: String, cap: Int) = ModelSpec(id, File(libraryDir, "$id.vrm"), cap)
    internal fun posterFor(key: String?): ImageBitmap? = _poster.value?.takeIf { it.first == key }?.second
}

/**
 * The saved poster of avatar [key] filling the box (stretched exactly like the 3D view, so the two line up when they cross-fade),
 * or nothing when there is none. Uses the poster [startup] already decoded, else decodes the file off the main thread.
 */
@Composable
fun AvatarPoster(key: String?, posters: AvatarPosters, startup: AvatarStartup?, modifier: Modifier = Modifier) {
    val image by produceState(initialValue = startup?.posterFor(key), key) {
        if (value == null && key != null) value = withContext(Dispatchers.IO) { posters.read(key)?.let { b -> BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() } }
    }
    image?.let { Image(it, contentDescription = null, modifier = modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
}
