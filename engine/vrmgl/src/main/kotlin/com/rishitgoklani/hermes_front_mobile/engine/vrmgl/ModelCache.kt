package com.rishitgoklani.hermes_front_mobile.engine.vrmgl

import android.graphics.Bitmap
import android.opengl.GLES30
import android.os.Process
import android.util.Log
import com.rishitgoklani.hermes_front_mobile.engine.vrm.AssetCache
import com.rishitgoklani.hermes_front_mobile.engine.vrm.GlbImageSlices
import com.rishitgoklani.hermes_front_mobile.engine.vrm.ImageFormats
import com.rishitgoklani.hermes_front_mobile.engine.vrm.SourceStamp
import com.rishitgoklani.hermes_front_mobile.engine.vrm.TextureCacheFile
import com.rishitgoklani.hermes_front_mobile.engine.vrm.VrmAsset
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.Accessors
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.Glb
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Per-model caches next to nothing else in [dir]: `<key>.geo` (the parsed model, see [AssetCache]) and `<key>.etc2` (every texture
 * compressed with its mip chain, see [TextureCacheFile]). A load uses whatever is valid for the model file and falls back to the GLB
 * otherwise. Missing caches are built on a background thread at low priority a few seconds after a model was loaded, so the first
 * show of an avatar works as before and every later one is faster. Files of models no longer in the library are deleted then too.
 */
class ModelCache(private val dir: File, private val buildDelayMs: Long = 3_000) {
    companion object {
        /** Bump when the texture encoding changes (settings, mip filter, encoder version): older texture caches are rebuilt. */
        const val ENCODER_VERSION = 1
        private const val TAG = "DeskbotVrmCache"
    }

    private val exec = Executors.newSingleThreadScheduledExecutor { r -> Thread({ Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND); r.run() }, "vrm-cache").also { it.isDaemon = true } }
    private val queued = ConcurrentHashMap.newKeySet<String>()

    private fun geoFile(key: String) = File(dir, "$key.geo")
    private fun texFile(key: String) = File(dir, "$key.etc2")

    /** The parsed model from the cache, or null. [imageBytes] is used only if something asks the asset for raw image bytes. */
    fun asset(spec: ModelSpec, imageBytes: (Int) -> ByteArray): VrmAsset? = AssetCache.read(geoFile(spec.key), SourceStamp.of(spec.file), imageBytes)

    /** The compressed textures of [spec], or null. */
    fun textures(spec: ModelSpec): TextureCacheFile.Contents? = TextureCacheFile.read(texFile(spec.key), SourceStamp.of(spec.file), ENCODER_VERSION)

    /** Build whatever cache [spec] is missing, in the background. [glb] opens the model file if the textures need encoding. */
    fun schedule(spec: ModelSpec, asset: VrmAsset, glb: () -> Glb) {
        if (!queued.add(spec.key)) return
        exec.schedule({
            try { build(spec, asset, glb) } catch (t: Throwable) { Log.w(TAG, "cache build failed for ${spec.key.take(8)}: ${t.message}") }
            finally { queued.remove(spec.key) }
        }, buildDelayMs, TimeUnit.MILLISECONDS)
    }

    private fun build(spec: ModelSpec, asset: VrmAsset, glb: () -> Glb) {
        dir.mkdirs(); spec.file.parentFile?.let(::prune)
        val stamp = SourceStamp.of(spec.file)
        if (AssetCache.read(geoFile(spec.key), stamp) { ByteArray(0) } == null) {
            val t0 = LoadPerf.nowMs(); AssetCache.write(asset, geoFile(spec.key), stamp)
            Log.i(LoadPerf.TAG, "cache ${spec.key.take(8)} geometry=${LoadPerf.nowMs() - t0}ms bytes=${geoFile(spec.key).length()}")
        }
        if (!Etc2Encoder.available || TextureCacheFile.read(texFile(spec.key), stamp, ENCODER_VERSION) != null) return
        val t0 = LoadPerf.nowMs(); val g = glb(); val alpha = ImageFormats.needsAlpha(asset)
        val builder = TextureCacheFile.Builder(spec.texCap); var px = 0L
        for (img in TextureLoader.usedImages(asset)) {
            val slice = GlbImageSlices.slice(g, asset, img) ?: continue; val size = TextureLoader.bounds(slice) ?: continue
            var level: Bitmap = TextureLoader.decodeArgb(slice, size, spec.texCap) ?: continue
            val levels = ArrayList<Triple<Int, Int, ByteArray>>()
            try {
                while (true) {
                    levels += Triple(level.width, level.height, Etc2Encoder.encode(level, alpha[img])); px += level.width.toLong() * level.height
                    if (level.width == 1 && level.height == 1) break
                    val next = Bitmap.createScaledBitmap(level, max(1, level.width / 2), max(1, level.height / 2), true)   // box-like halving for the next mip
                    level.recycle(); level = next
                }
            } finally { level.recycle() }
            builder.add(img, if (alpha[img]) GLES30.GL_COMPRESSED_RGBA8_ETC2_EAC else GLES30.GL_COMPRESSED_RGB8_ETC2, alpha[img], levels)
        }
        builder.write(texFile(spec.key), stamp, ENCODER_VERSION)
        Log.i(LoadPerf.TAG, "cache ${spec.key.take(8)} textures=${LoadPerf.nowMs() - t0}ms images=${builder.count} mpx=${"%.1f".format(px / 1e6)} bytes=${texFile(spec.key).length()}")
    }

    /** Deletes cache files whose model is no longer in [libraryDir]. */
    private fun prune(libraryDir: File) {
        dir.listFiles()?.forEach { f -> val key = f.name.substringBefore('.'); if (key.isNotEmpty() && !File(libraryDir, "$key.vrm").exists()) f.delete() }
    }
}

/** Raw bytes of buffer view [view] of [glb] (what [VrmAsset.imageBytes] returns for a model parsed from the file). */
internal fun glbViewBytes(glb: Glb, view: Int): ByteArray = Accessors(glb.json, glb.bin).viewBytes(view)
