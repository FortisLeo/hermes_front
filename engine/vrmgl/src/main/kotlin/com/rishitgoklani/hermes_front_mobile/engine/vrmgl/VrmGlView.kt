package com.rishitgoklani.hermes_front_mobile.engine.vrmgl

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.View
import com.rishitgoklani.hermes_front_mobile.engine.vrm.ClipSource
import com.rishitgoklani.hermes_front_mobile.engine.vrm.DriverInput
import com.rishitgoklani.hermes_front_mobile.engine.vrm.FramePacer
import com.rishitgoklani.hermes_front_mobile.engine.vrm.Framing
import com.rishitgoklani.hermes_front_mobile.engine.vrm.GlWorkQueue
import com.rishitgoklani.hermes_front_mobile.engine.vrm.GlbImageSlices
import com.rishitgoklani.hermes_front_mobile.engine.vrm.IdleGate
import com.rishitgoklani.hermes_front_mobile.engine.vrm.ImageFormats
import com.rishitgoklani.hermes_front_mobile.engine.vrm.MemoryPolicy
import com.rishitgoklani.hermes_front_mobile.engine.vrm.ModelSlots
import com.rishitgoklani.hermes_front_mobile.engine.vrm.SlotHost
import com.rishitgoklani.hermes_front_mobile.engine.vrm.TextureBudgeting
import com.rishitgoklani.hermes_front_mobile.engine.vrm.TrimAction
import com.rishitgoklani.hermes_front_mobile.engine.vrm.VrmParser
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.Glb
import com.rishitgoklani.hermes_front_mobile.engine.vrm.math.Mat4
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** A model to show: [key] is the library id, [file] the .vrm, [texCap] the texture size ceiling in pixels. */
data class ModelSpec(val key: String, val file: File, val texCap: Int)

/** What the engine reports (always on the main thread). */
interface VrmEvents {
    fun onShown(key: String, ms: Long, wasStaged: Boolean) {}
    fun onStaged(key: String, ms: Long) {}
    fun onError(key: String, message: String) {}
    fun onNodes(names: List<String>) {}
    fun onHead(x: Float, y: Float, size: Float) {}
    /** The renderer hit an unrecoverable GL error (shader compile, EGL). The host should fall back to the Eyes face. */
    fun onFatal(message: String) {}
}

/**
 * The VRM view: OpenGL ES 3.0, one GL context, at most two models resident (active + staged, the staged one geometry-only). Drive it
 * declaratively with [want] and [setInput]. Frames are paced by a Choreographer callback (30 fps while moving, 10 fps when idle, none at
 * all when nothing changed for a while or the view is hidden). All GPU work happens on the GL thread; decoding on one loader thread, which
 * hands textures over one per frame. A GL failure stops rendering and is reported once through [VrmEvents.onFatal]; it never crashes the process.
 */
class VrmGlView(
    context: Context, private val config: EngineConfig = defaultConfig(context),
    /** Texture memory shared by the resident models (see [TextureBudgeting.deviceBudgetBytes]); the app may pass its own number. */
    private val textureBudgetBytes: Long = defaultTextureBudget(context),
) : GLSurfaceView(context) {
    companion object {
        private const val TAG = "DeskbotVrmGl"
        /** Low-RAM phones get 16-bit textures and no outline pass. */
        fun defaultConfig(context: Context): EngineConfig {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager; val info = ActivityManager.MemoryInfo(); am.getMemoryInfo(info)
            val lowRam = am.isLowRamDevice || info.totalMem < 4L * 1024 * 1024 * 1024
            return EngineConfig(outlines = !am.isLowRamDevice, springs = true, rgb565 = lowRam)
        }
        /** Per-device texture budget: a 80th of RAM clamped to 24..96 MB, halved on low-RAM devices. */
        fun defaultTextureBudget(context: Context): Long {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager; val info = ActivityManager.MemoryInfo(); am.getMemoryInfo(info)
            return TextureBudgeting.deviceBudgetBytes(info.totalMem, am.isLowRamDevice)
        }
        private const val BG_R = 0x05 / 255f; private const val BG_G = 0x07 / 255f; private const val BG_B = 0x0A / 255f
        private const val GL_WAIT_LIMIT_MS = 20_000L          // unpaused time a loader waits for the GL thread before giving up
        private const val SLEEP_HEARTBEAT_MS = 4_000L         // while asleep, wake briefly now and then so blinking and breathing continue
        private const val HEARTBEAT_AWAKE_NS = 700_000_000L
    }

    var events: VrmEvents? = null
    /** Folder of .vrma clips; the pose driver loads `<name>.vrma` from here when a clip is requested. */
    var clipsDir: File? = null

    @Volatile private var input = DriverInput()
    @Volatile private var active: GlModel? = null
    private val main = Handler(Looper.getMainLooper())
    private val loader = Executors.newSingleThreadExecutor { r -> Thread(r, "vrm-loader").also { it.priority = Thread.NORM_PRIORITY - 1 } }
    private val specs = HashMap<String, ModelSpec>()
    private var lastWantActive: ModelSpec? = null; private var lastWantStaged: ModelSpec? = null

    private val programs = HashMap<Int, GlProgram>()   // key: 0 = static, otherwise max joints of the skinned program
    private val vp = FloatArray(16); private val proj = FloatArray(16); private val viewM = FloatArray(16)
    private val light = floatArrayOf(0.4264f, 0.8528f, 0.4264f)
    private var framing: Framing? = null
    private var width = 1; private var height = 1
    private var lastNanos = 0L; private var headTick = 0
    @Volatile private var busyUntil = 0L
    @Volatile private var thumbRequest: Pair<Int, (ByteArray?) -> Unit>? = null
    @Volatile private var paused = true
    @Volatile private var released = false
    @Volatile private var dead = false
    @Volatile private var running = false                  // the frame loop is allowed to run (resumed, visible, alive)
    private val fatalSent = AtomicBoolean(false)
    @Volatile private var glGeneration = 0
    private var uniformLimit = 1024

    // frame pacing (main thread)
    private val queue = GlWorkQueue()
    private var pacer = FramePacer()
    private val gate = IdleGate()
    private val choreographer by lazy { Choreographer.getInstance() }
    private var looping = false
    private var windowVisible = true
    private val memory = MemoryPolicy((context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice)
    private var stagedSuppressed = false; private var releasedForMemory = false

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            looping = false
            if (!running) return
            val moving = isMoving(); gate.update(moving, frameTimeNanos)
            if (gate.asleep(frameTimeNanos)) { main.removeCallbacks(heartbeat); main.postDelayed(heartbeat, SLEEP_HEARTBEAT_MS); return }   // nothing changed for a while: stop completely
            if (pacer.shouldRender(moving, frameTimeNanos, urgent = queue.hasWork || thumbRequest != null)) requestRender()
            looping = true
            if (moving) choreographer.postFrameCallback(this) else choreographer.postFrameCallbackDelayed(this, pacer.idleDelayMs())
        }
    }
    private val heartbeat = Runnable { gate.touch(System.nanoTime(), HEARTBEAT_AWAKE_NS); startLoop() }
    private val backgroundRelease = Runnable { if (paused) dropGlContext() }

    /** Anything that makes the next frames differ: animation, a recent input, GL work waiting, a snapshot. */
    private fun isMoving(): Boolean = active?.driver?.moving == true || SystemClock.uptimeMillis() < busyUntil || queue.hasWork || thumbRequest != null || !springsSettled()
    /** Integrator hook: true once the hair/cloth springs have come to rest (e.g. SpringSimulation.settled). Until it exists the 3 s quiet period covers it. */
    private fun springsSettled(): Boolean = true

    private fun startLoop() { if (running && !looping) { looping = true; choreographer.postFrameCallback(frame) } }
    private fun stopLoop() { choreographer.removeFrameCallback(frame); main.removeCallbacks(heartbeat); looping = false }
    private fun refreshLoop() {
        running = !paused && !released && !dead && windowVisible && isShown
        if (running) { gate.touch(System.nanoTime()); startLoop() } else stopLoop()
    }
    private fun wake() { main.removeCallbacks(heartbeat); gate.touch(System.nanoTime()); startLoop() }
    private fun wakeFromAnyThread() { if (Looper.myLooper() == Looper.getMainLooper()) wake() else main.post { wake() } }

    private class Op { val cancelled = AtomicBoolean(false); fun check() { if (cancelled.get()) throw CancellationException("cancelled") } }
    private class Resident(val model: GlModel, val glb: Glb, val spec: ModelSpec) { @Volatile var textureBytes = 0L; @Volatile var op: Op? = null }
    private val loads = ConcurrentHashMap<String, Op>()
    private val residents = ConcurrentHashMap.newKeySet<Resident>()

    private val clipSource = ClipSource { name ->
        val dir = clipsDir
        if (dir == null || name.isEmpty() || '/' in name || ".." in name) null else File(dir, "$name.vrma").takeIf { it.isFile }?.readBytes()
    }

    private val slots = ModelSlots(object : SlotHost<Resident> {
        override fun load(key: String, withTextures: Boolean, done: (Result<Resident>) -> Unit) = this@VrmGlView.load(key, withTextures, done)
        override fun finishShow(key: String, model: Resident, done: (Result<Unit>) -> Unit) = this@VrmGlView.finishTextures(model, done)
        override fun cancel(key: String) { loads.remove(key)?.cancelled?.set(true) }
        override fun show(key: String, model: Resident) {
            active = model.model; model.model.driver.reset(); model.model.springs.reset()
            framing = Framing.of(model.model.asset, model.model.rig.modelMatrix); busyUntil = SystemClock.uptimeMillis() + 1500; wake()
            events?.onNodes(model.model.rig.nodeNames())
        }
        override fun dispose(key: String, model: Resident) {
            model.op?.cancelled?.set(true); residents.remove(model)
            if (active === model.model) active = null
            queueEvent { runCatching { model.model.release() } }
        }
        override fun shown(key: String, ms: Long, wasStaged: Boolean) { events?.onShown(key, ms, wasStaged) }
        override fun staged(key: String, ms: Long) { events?.onStaged(key, ms) }
        override fun failed(key: String, message: String) { events?.onError(key, message) }
    })

    init {
        setEGLContextClientVersion(3); setEGLConfigChooser(8, 8, 8, 0, 16, 0); preserveEGLContextOnPause = true
        setRenderer(object : Renderer {
            override fun onSurfaceCreated(gl: GL10?, c: EGLConfig?) {
                try {
                    programs.clear(); val v = IntArray(1); GLES30.glGetIntegerv(GLES30.GL_MAX_VERTEX_UNIFORM_VECTORS, v, 0); uniformLimit = v[0]
                    GLES30.glClearColor(BG_R, BG_G, BG_B, 1f); GLES30.glEnable(GLES30.GL_DEPTH_TEST)
                    val gen = ++glGeneration; if (gen > 1) main.post { reloadAfterContextLoss() }
                } catch (t: Throwable) { fatal("GL setup failed: ${t.message}", t) }
            }
            override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) { width = maxOf(w, 1); height = maxOf(h, 1); runCatching { GLES30.glViewport(0, 0, w, h) } }
            override fun onDrawFrame(gl: GL10?) = drawFrame()
        })
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    // ---- public API (main thread) ----
    fun setInput(i: DriverInput) { input = i; busyUntil = maxOf(busyUntil, SystemClock.uptimeMillis() + 400); wake() }

    /** Declare which model is on screen and which one to keep ready behind it. */
    fun want(activeSpec: ModelSpec?, stagedSpec: ModelSpec?) {
        lastWantActive = activeSpec; lastWantStaged = stagedSpec
        activeSpec?.let { specs[it.key] = it }; stagedSpec?.let { specs[it.key] = it }
        if (released || dead) return
        slots.want(activeSpec?.key, if (stagedSuppressed) null else stagedSpec?.key)
        wake()
    }
    fun retry(key: String) = slots.retry(key)
    val residentModels get() = slots.resident

    /** A small square PNG of the current frame, for library thumbnails. */
    fun snapshot(size: Int, done: (ByteArray?) -> Unit) {
        if (released || dead) { main.post { done(null) }; return }
        thumbRequest = size to done; wake(); requestRender()
    }

    override fun onResume() {
        super.onResume(); paused = false; preserveEGLContextOnPause = true; main.removeCallbacks(backgroundRelease); refreshLoop()
        if (releasedForMemory) {
            // The context was given up in the background: it comes back through onSurfaceCreated (reloadAfterContextLoss); this is the safety net.
            releasedForMemory = false; stagedSuppressed = false
            main.postDelayed({ if (!paused && !released && slots.resident == 0) want(lastWantActive, lastWantStaged) }, 500)
        } else if (stagedSuppressed) { stagedSuppressed = false; want(lastWantActive, lastWantStaged) }
    }
    override fun onPause() {
        paused = true; refreshLoop(); super.onPause()
        if (!released) main.postDelayed(backgroundRelease, memory.backgroundDelayMs)
    }

    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); windowVisible = visibility == View.VISIBLE; refreshLoop() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) { super.onVisibilityChanged(changedView, visibility); refreshLoop() }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        pacer = FramePacer(1000f / (display?.refreshRate ?: 60f).coerceIn(30f, 240f)); refreshLoop()
    }
    override fun onDetachedFromWindow() { stopLoop(); running = false; super.onDetachedFromWindow() }

    /** Forward ComponentCallbacks2.onTrimMemory. Safe at any time. */
    fun onTrimMemory(level: Int) {
        if (released) return
        when (memory.onTrim(level)) {
            TrimAction.NONE -> {}
            TrimAction.DROP_STAGED -> { stagedSuppressed = true; slots.dropStaged() }
            TrimAction.RELEASE_ALL -> if (paused) dropGlContext() else { stagedSuppressed = true; slots.dropStaged() }
        }
    }

    /**
     * Give the GPU everything back while in the background: forget the models and destroy the EGL context. Models reload through
     * [reloadAfterContextLoss] when the surface comes back. (Events queued to a paused GL thread have no current context, so deleting
     * objects one by one would leak; destroying the context frees them all.)
     */
    private fun dropGlContext() {
        if (!paused || releasedForMemory || released) return
        releasedForMemory = true
        slots.releaseAll(); active = null; framing = null; queue.clear()
        preserveEGLContextOnPause = false
        super.onResume()                                                   // let the GL thread see "resumed", then pause again: without preserve that destroys the context
        main.postDelayed({ if (paused && !released) super@VrmGlView.onPause() }, 250)
    }

    /** Stops everything and frees GPU objects. Safe to call twice and while loads are in flight. */
    fun release() {
        if (released) return
        released = true; running = false; paused = true
        stopLoop(); main.removeCallbacksAndMessages(null)
        slots.releaseAll(); active = null; queue.clear(); thumbRequest?.let { (_, cb) -> thumbRequest = null; cb(null) }
        queueEvent { runCatching { programs.values.forEach { it.delete() }; programs.clear() } }
        loader.shutdownNow()
    }

    private fun reloadAfterContextLoss() {
        if (released || dead) return
        slots.releaseAll(); active = null; framing = null; queue.clear(); val a = lastWantActive; val s = lastWantStaged; want(a, s)
    }

    private fun fatal(message: String, t: Throwable? = null) {
        Log.e(TAG, message, t); dead = true; running = false
        if (fatalSent.compareAndSet(false, true)) main.post { stopLoop(); if (!released) events?.onFatal(message) }
    }

    // ---- loading (loader thread) ----
    private fun load(key: String, withTextures: Boolean, done: (Result<Resident>) -> Unit) {
        val spec = specs[key] ?: return done(Result.failure(IllegalStateException("Unknown model")))
        val op = Op(); loads.put(key, op)?.cancelled?.set(true)
        if (released || loader.isShutdown) return
        runCatching { loader.execute {
            var model: GlModel? = null
            val r = runCatching {
                op.check()
                val glb = Glb.open(spec.file); val asset = VrmParser.parse(glb)
                if (asset.skins.any { it.joints.size * 3 + 24 > uniformLimit }) throw IllegalStateException("This GPU cannot skin a model with that many joints")
                val m = onGl(op) { GlModel(asset, config) }; model = m
                m.driver.clipSource = clipSource
                val res = Resident(m, glb, spec); residents.add(res)
                if (withTextures) loadTextures(res, op)
                res
            }
            r.onFailure { model?.let { m -> queueEvent { runCatching { m.release() } } } }
            main.post { loads.remove(key, op); if (!released) done(r) }
        } }
    }

    /** Geometry-only model about to go on screen: decode and upload its textures, still one image at a time. */
    private fun finishTextures(res: Resident, done: (Result<Unit>) -> Unit) {
        val op = Op(); res.op = op
        runCatching { loader.execute {
            val r = runCatching { loadTextures(res, op) }
            main.post { if (!released) done(r) }
        } }.onFailure { done(Result.failure(it)) }
    }

    /** Decode each used image (cap chosen so all of them fit the shared budget) and hand it to the GL thread through the queue. */
    private fun loadTextures(res: Resident, op: Op) {
        val asset = res.model.asset; val need = ImageFormats.needsAlpha(asset); val used = TextureLoader.usedImages(asset)
        val sizes = used.map { img -> GlbImageSlices.slice(res.glb, asset, img)?.let { TextureLoader.bounds(it) } }
        val infos = used.indices.mapNotNull { i -> sizes[i]?.let { TextureLoader.info(it, need[used[i]], config.rgb565) } }
        val others = residents.filter { it !== res }.sumOf { it.textureBytes }
        val cap = TextureBudgeting.capFor(infos, res.spec.texCap, TextureBudgeting.availableBytes(textureBudgetBytes, others))
        for ((i, img) in used.withIndex()) {
            op.check()
            val size = sizes[i] ?: continue; val slice = GlbImageSlices.slice(res.glb, asset, img) ?: continue
            val bmp = TextureLoader.decode(slice, size, cap, need[img], config.rgb565) ?: continue
            val bytes = bmp.allocationByteCount.toLong() * 4 / 3
            onGl(op) { try { res.model.uploadTexture(img, bmp) } finally { bmp.recycle() } }   // recycled on the GL thread; a dropped task leaves it to the GC
            res.textureBytes += bytes
        }
    }

    /** Run [block] on the GL thread inside a frame (at most one queued task per frame) and wait for it. Never waits forever. */
    private fun <T> onGl(op: Op, block: () -> T): T {
        var out: Result<T>? = null; val latch = CountDownLatch(1)
        queue.add { out = if (op.cancelled.get()) Result.failure(CancellationException("cancelled")) else runCatching(block); latch.countDown() }
        wakeFromAnyThread()
        var waited = 0L
        while (!latch.await(250, TimeUnit.MILLISECONDS)) {
            if (op.cancelled.get() || released) { op.cancelled.set(true); throw CancellationException("cancelled") }
            if (dead) { op.cancelled.set(true); throw IllegalStateException("GL stopped") }
            if (running) waited += 250   // time spent paused or hidden does not count
            if (waited > GL_WAIT_LIMIT_MS) { op.cancelled.set(true); throw IllegalStateException("GL thread did not respond") }
        }
        return out!!.getOrThrow()
    }

    // ---- drawing (GL thread) ----
    private fun program(skinned: Boolean, maxJoints: Int): GlProgram {
        val key = if (skinned) maxOf(1, maxJoints) else 0
        return programs.getOrPut(key) { GlProgram(Shaders.vertex(skinned, maxJoints), Shaders.FRAGMENT) }
    }

    private fun drawFrame() {
        if (dead || released) return
        try {
            queue.drain()                                            // one texture / model upload per frame, ~4 ms budget
            val now = System.nanoTime(); val dt = if (lastNanos == 0L) 0.033f else ((now - lastNanos) / 1e9f).coerceIn(0.001f, 0.1f); lastNanos = now
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
            val m = active; val f = framing
            if (m != null && f != null) {
                m.driver.frame(dt, SystemClock.uptimeMillis(), input); m.rig.update(); if (config.springs) m.springs.step(dt)
                m.updateMorphs()
                Mat4.perspective(f.fovY, width.toFloat() / height, 0.1f, 20f, proj); Mat4.lookAt(f.eyeX, f.eyeY, f.eyeZ, f.targetX, f.targetY, f.targetZ, viewM); Mat4.mul(proj, 0, viewM, 0, vp, 0)
                m.draw(vp, light) { skinned -> program(skinned, m.maxJoints) }
                if (++headTick % 6 == 0) reportHead(m, f)
            }
            thumbRequest?.let { (size, cb) -> thumbRequest = null; cb2(size, cb) }
        } catch (t: Throwable) { fatal("GL draw failed: ${t.message}", t) }
    }

    private val tmp = FloatArray(3)
    private fun reportHead(m: GlModel, f: Framing) {
        val head = m.asset.humanoid["head"] ?: return
        m.rig.worldPosition(head, tmp); val hx = tmp[0]; val hy = tmp[1]; val hz = tmp[2]
        val topY = maxOf(m.asset.boundsMax[1], hy + 0.15f)
        val face = FloatArray(3); val top = FloatArray(3); val c0 = FloatArray(3)
        Mat4.transformPoint(m.rig.modelMatrix, 0, hx, hy + 0.45f * (topY - hy), hz, face); Mat4.transformPoint(m.rig.modelMatrix, 0, hx, topY, hz, top); Mat4.transformPoint(m.rig.modelMatrix, 0, hx, hy, hz, c0)
        fun ndc(p: FloatArray): FloatArray { val x = vp[0] * p[0] + vp[4] * p[1] + vp[8] * p[2] + vp[12]; val y = vp[1] * p[0] + vp[5] * p[1] + vp[9] * p[2] + vp[13]; val w = vp[3] * p[0] + vp[7] * p[1] + vp[11] * p[2] + vp[15]; return floatArrayOf(x / w, y / w) }
        val a = ndc(face); val b = ndc(top); val c = ndc(c0)
        val x = (a[0] + 1) / 2; val y = (1 - a[1]) / 2; val size = Math.abs(b[1] - c[1]) / 2
        main.post { events?.onHead(x, y, size) }
    }

    private fun cb2(size: Int, cb: (ByteArray?) -> Unit) {
        val w = width; val h = height
        val buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder()); GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
        Thread {
            val r = runCatching {
                val raw = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); raw.copyPixelsFromBuffer(buf)
                val flipped = Bitmap.createBitmap(raw, 0, 0, w, h, Matrix().apply { preScale(1f, -1f) }, false); raw.recycle()
                val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888); val c = Canvas(out); c.drawColor(Color.rgb(5, 7, 10))
                val k = minOf(size / w.toFloat(), size / h.toFloat()); val dw = (w * k).toInt().coerceAtLeast(1); val dh = (h * k).toInt().coerceAtLeast(1)
                c.drawBitmap(Bitmap.createScaledBitmap(flipped, dw, dh, true), ((size - dw) / 2).toFloat(), ((size - dh) / 2).toFloat(), null); flipped.recycle()
                ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.PNG, 100, it); out.recycle() }.toByteArray()
            }.getOrNull()
            main.post { cb(r) }
        }.start()
    }
}
