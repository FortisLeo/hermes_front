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
import com.rishitgoklani.hermes_front_mobile.engine.vrm.DriverInput
import com.rishitgoklani.hermes_front_mobile.engine.vrm.Framing
import com.rishitgoklani.hermes_front_mobile.engine.vrm.ImageFormats
import com.rishitgoklani.hermes_front_mobile.engine.vrm.ModelSlots
import com.rishitgoklani.hermes_front_mobile.engine.vrm.SlotHost
import com.rishitgoklani.hermes_front_mobile.engine.vrm.VrmParser
import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.Glb
import com.rishitgoklani.hermes_front_mobile.engine.vrm.math.Mat4
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
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
 * The VRM view: OpenGL ES 3.0, one GL context, at most two models resident (active + staged). Drive it declaratively with [want] and
 * [setInput]. Frame rate drops to 10 fps when nothing moves. All GPU work happens on the GL thread; decoding on one loader thread.
 */
class VrmGlView(context: Context, private val config: EngineConfig = defaultConfig(context)) : GLSurfaceView(context) {
    companion object {
        /** Low-RAM phones get 16-bit textures and no outline pass. */
        fun defaultConfig(context: Context): EngineConfig {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager; val info = ActivityManager.MemoryInfo(); am.getMemoryInfo(info)
            val lowRam = am.isLowRamDevice || info.totalMem < 4L * 1024 * 1024 * 1024
            return EngineConfig(outlines = !am.isLowRamDevice, springs = true, rgb565 = lowRam)
        }
        private const val BG_R = 0x05 / 255f; private const val BG_G = 0x07 / 255f; private const val BG_B = 0x0A / 255f
    }

    var events: VrmEvents? = null
    @Volatile private var input = DriverInput()
    @Volatile private var active: GlModel? = null
    private val main = Handler(Looper.getMainLooper())
    private val loader = Executors.newSingleThreadExecutor { r -> Thread(r, "vrm-loader").also { it.priority = Thread.NORM_PRIORITY - 1 } }
    private val specs = HashMap<String, ModelSpec>()
    private var generation = 0
    private var lastWantActive: ModelSpec? = null; private var lastWantStaged: ModelSpec? = null

    private val programs = HashMap<Int, GlProgram>()   // key: 0 = static, otherwise max joints of the skinned program
    private val vp = FloatArray(16); private val proj = FloatArray(16); private val viewM = FloatArray(16)
    private val light = floatArrayOf(0.4264f, 0.8528f, 0.4264f)
    private var framing: Framing? = null
    private var width = 1; private var height = 1
    private var lastNanos = 0L; private var headTick = 0
    @Volatile private var busyUntil = 0L
    private var thumbRequest: Pair<Int, (ByteArray?) -> Unit>? = null
    @Volatile private var paused = true
    private var uniformLimit = 1024

    private val slots = ModelSlots(object : SlotHost<GlModel> {
        override fun load(key: String, done: (Result<GlModel>) -> Unit) = this@VrmGlView.load(key, done)
        override fun show(key: String, model: GlModel) {
            active = model; model.driver.reset(); model.springs.reset()
            framing = Framing.of(model.asset, model.rig.modelMatrix); busyUntil = SystemClock.uptimeMillis() + 1500
            events?.onNodes(model.rig.nodeNames())
        }
        override fun dispose(key: String, model: GlModel) { if (active === model) active = null; queueEvent { model.release() } }
        override fun shown(key: String, ms: Long, wasStaged: Boolean) { events?.onShown(key, ms, wasStaged) }
        override fun staged(key: String, ms: Long) { events?.onStaged(key, ms) }
        override fun failed(key: String, message: String) { events?.onError(key, message) }
    })

    private val ticker = object : Runnable {
        override fun run() {
            if (paused) return
            requestRender()
            val moving = (active?.driver?.moving == true) || SystemClock.uptimeMillis() < busyUntil
            main.postDelayed(this, if (moving) 33L else 100L)
        }
    }

    init {
        setEGLContextClientVersion(3); setEGLConfigChooser(8, 8, 8, 0, 16, 0); preserveEGLContextOnPause = true
        setRenderer(object : Renderer {
            override fun onSurfaceCreated(gl: GL10?, c: EGLConfig?) {
                programs.clear(); val v = IntArray(1); GLES30.glGetIntegerv(GLES30.GL_MAX_VERTEX_UNIFORM_VECTORS, v, 0); uniformLimit = v[0]
                GLES30.glClearColor(BG_R, BG_G, BG_B, 1f); GLES30.glEnable(GLES30.GL_DEPTH_TEST)
                val gen = ++generation; if (gen > 1) main.post { reloadAfterContextLoss() }
            }
            override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) { width = maxOf(w, 1); height = maxOf(h, 1); GLES30.glViewport(0, 0, w, h) }
            override fun onDrawFrame(gl: GL10?) = drawFrame()
        })
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    // ---- public API (main thread) ----
    fun setInput(i: DriverInput) { input = i; busyUntil = maxOf(busyUntil, SystemClock.uptimeMillis() + 400) }

    /** Declare which model is on screen and which one to keep ready behind it. */
    fun want(activeSpec: ModelSpec?, stagedSpec: ModelSpec?) {
        lastWantActive = activeSpec; lastWantStaged = stagedSpec
        activeSpec?.let { specs[it.key] = it }; stagedSpec?.let { specs[it.key] = it }
        slots.want(activeSpec?.key, stagedSpec?.key)
    }
    fun retry(key: String) = slots.retry(key)
    val residentModels get() = slots.resident

    /** A small square PNG of the current frame, for library thumbnails. */
    fun snapshot(size: Int, done: (ByteArray?) -> Unit) { thumbRequest = size to done; requestRender() }

    override fun onResume() { super.onResume(); paused = false; main.removeCallbacks(ticker); main.post(ticker) }
    override fun onPause() { paused = true; main.removeCallbacks(ticker); super.onPause() }
    fun release() { paused = true; main.removeCallbacks(ticker); slots.releaseAll(); active = null; queueEvent { programs.values.forEach { it.delete() }; programs.clear() }; loader.shutdown() }

    private fun reloadAfterContextLoss() { slots.releaseAll(); active = null; framing = null; val a = lastWantActive; val s = lastWantStaged; want(a, s) }

    // ---- loading ----
    private fun load(key: String, done: (Result<GlModel>) -> Unit) {
        val spec = specs[key] ?: return done(Result.failure(IllegalStateException("Unknown model")))
        loader.execute {
            val r = runCatching {
                val asset = VrmParser.parse(Glb.open(spec.file))
                val need = ImageFormats.needsAlpha(asset)
                if (asset.skins.any { it.joints.size * 3 + 24 > uniformLimit }) throw IllegalStateException("This GPU cannot skin a model with that many joints")
                val model = onGl { GlModel(asset, config) }
                for (img in TextureLoader.usedImages(asset)) {
                    val bmp = TextureLoader.decode(asset, img, spec.texCap, need[img], config.rgb565) ?: continue
                    onGl { model.uploadTexture(img, bmp); bmp.recycle() }
                }
                model
            }
            main.post { done(r) }
        }
    }

    private fun <T> onGl(block: () -> T): T {
        var out: Result<T>? = null; val latch = CountDownLatch(1)
        queueEvent { out = runCatching(block); latch.countDown() }; requestRender()
        latch.await(); return out!!.getOrThrow()
    }

    // ---- drawing (GL thread) ----
    private fun program(skinned: Boolean, maxJoints: Int): GlProgram {
        val key = if (skinned) maxOf(1, maxJoints) else 0
        return programs.getOrPut(key) { GlProgram(Shaders.vertex(skinned, maxJoints), Shaders.FRAGMENT) }
    }

    private fun drawFrame() {
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
