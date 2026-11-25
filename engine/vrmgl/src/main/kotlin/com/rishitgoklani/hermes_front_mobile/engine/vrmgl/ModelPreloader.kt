package com.rishitgoklani.hermes_front_mobile.engine.vrmgl

import android.graphics.Bitmap
import android.util.Log
import com.rishitgoklani.hermes_front_mobile.engine.vrm.GlbImageSlices
import com.rishitgoklani.hermes_front_mobile.engine.vrm.PreloadSlot
import com.rishitgoklani.hermes_front_mobile.engine.vrm.TextureBudgeting
import com.rishitgoklani.hermes_front_mobile.engine.vrm.VrmAsset
import com.rishitgoklani.hermes_front_mobile.engine.vrm.VrmInfo
import com.rishitgoklani.hermes_front_mobile.engine.vrm.VrmInspector
import com.rishitgoklani.hermes_front_mobile.engine.vrm.VrmParser
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.Glb
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Parses a model and decodes its textures before the view exists (the app starts it as soon as the activity is created, while the
 * UI is still being built), so the view only has to upload to the GPU. One model at a time; what is not taken by the view within
 * [TTL_MS] is freed. The view checks that the preload matches its own settings and ignores it otherwise.
 */
class ModelPreloader(private val config: EngineConfig, private val textureBudgetBytes: Long, private val decodeLimitBytes: Long = 24L * 1024 * 1024) {
    internal class Ready(val spec: ModelSpec, val glb: Glb, val asset: VrmAsset, val info: VrmInfo?, val cap: Int, val bitmaps: ConcurrentHashMap<Int, Bitmap>, val ms: Long) {
        fun recycle() { bitmaps.values.forEach { it.recycle() }; bitmaps.clear() }
    }

    companion object { const val TTL_MS = 10_000L; private const val WAIT_MS = 4_000L }

    private val exec = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "vrm-preload").also { it.isDaemon = true; it.priority = Thread.NORM_PRIORITY - 1 } }
    private val slot = PreloadSlot<Ready>({ it.recycle() }, TTL_MS)

    /** Start preparing [spec] in the background (no-op if it is already being prepared). */
    fun preload(spec: ModelSpec) {
        slot.start(spec.key, exec) { work(spec) }
        exec.schedule({ slot.expire(spec.key) }, TTL_MS + 50, TimeUnit.MILLISECONDS)
    }

    fun clear() = slot.clear()

    /** The prepared model for [spec] if it was made with the same settings the view uses; waits for one still being made. */
    internal fun take(spec: ModelSpec, viewConfig: EngineConfig, viewBudget: Long): Ready? {
        val r = slot.take(spec.key, WAIT_MS) ?: return null
        if (r.spec != spec || viewConfig != config || viewBudget != textureBudgetBytes) { r.recycle(); return null }
        return r
    }

    private fun work(spec: ModelSpec): Ready {
        val t0 = LoadPerf.nowMs()
        val glb = Glb.open(spec.file); val asset = VrmParser.parse(glb); val info = runCatching { VrmInspector.inspect(glb) }.getOrNull()
        val plan = TexturePlan.of(glb, asset, config.rgb565, spec.texCap, TextureBudgeting.availableBytes(textureBudgetBytes, 0))
        val bitmaps = ConcurrentHashMap<Int, Bitmap>(); var bytes = 0L
        for (img in plan.images) {
            if (Thread.currentThread().isInterrupted || bytes >= decodeLimitBytes) break   // the rest is decoded by the view as usual
            val slice = GlbImageSlices.slice(glb, asset, img) ?: continue
            val bmp = TextureLoader.decode(slice, plan.sizes.getValue(img), plan.cap, plan.alpha[img], config.rgb565) ?: continue
            bitmaps[img] = bmp; bytes += bmp.allocationByteCount
        }
        val ms = LoadPerf.nowMs() - t0
        Log.i(LoadPerf.TAG, "preload ${spec.key.take(8)} took=$ms images=${bitmaps.size}/${plan.images.size} cap=${plan.cap}")
        return Ready(spec, glb, asset, info, plan.cap, bitmaps, ms)
    }
}

/** Which images a model needs, their sizes, alpha needs and the size cap that fits the budget. Shared by the view and the preloader so both pick the same cap. */
internal class TexturePlan(val images: List<Int>, val sizes: Map<Int, Pair<Int, Int>>, val alpha: BooleanArray, val cap: Int) {
    companion object {
        fun of(glb: Glb, asset: VrmAsset, rgb565: Boolean, texCap: Int, availableBytes: Long): TexturePlan {
            val need = com.rishitgoklani.hermes_front_mobile.engine.vrm.ImageFormats.needsAlpha(asset); val used = TextureLoader.usedImages(asset)
            val sizes = LinkedHashMap<Int, Pair<Int, Int>>()
            for (img in used) GlbImageSlices.slice(glb, asset, img)?.let { TextureLoader.bounds(it) }?.let { sizes[img] = it }
            val infos = sizes.map { (img, size) -> TextureLoader.info(size, need[img], rgb565) }
            return TexturePlan(sizes.keys.toList(), sizes, need, TextureBudgeting.capFor(infos, texCap, availableBytes))
        }
    }
}
