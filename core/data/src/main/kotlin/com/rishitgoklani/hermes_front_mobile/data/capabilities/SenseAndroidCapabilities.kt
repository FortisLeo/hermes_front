package com.rishitgoklani.hermes_front_mobile.data.capabilities

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.FaceDetector
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import com.rishitgoklani.hermes_front_mobile.data.ui.DeskbotAccessibilityService
import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.sense.AmbientBrightness
import com.rishitgoklani.hermes_front_mobile.domain.sense.FaceCounter
import com.rishitgoklani.hermes_front_mobile.domain.ui.UiAutomation
import com.rishitgoklani.hermes_front_mobile.domain.ui.UiQuery
import com.rishitgoklani.hermes_front_mobile.domain.ui.UiTree
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import kotlin.coroutines.resume

/** Counts faces with the platform FaceDetector (detection only, no recognition). Frame is decoded, counted, discarded. */
class AndroidFaceCounter : FaceCounter {
    override suspend fun count(jpeg: ByteArray): Int = withContext(Dispatchers.Default) {
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565; inSampleSize = 2 }
        val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts) ?: return@withContext 0
        val w = bmp.width - bmp.width % 2
        val b = if (w != bmp.width) Bitmap.createBitmap(bmp, 0, 0, w, bmp.height) else bmp
        val found = arrayOfNulls<FaceDetector.Face>(5)
        FaceDetector(b.width, b.height, 5).findFaces(b, found)
    }
}

/** Ambient-light brightness loop: every [periodMs] read lux, apply brightness when the change passes hysteresis. */
class AmbientController(private val context: Context, private val scope: CoroutineScope, private val periodMs: Long = 15_000) {
    private val mapper = AmbientBrightness()
    private var job: Job? = null
    val enabled get() = job?.isActive == true

    fun start() {
        if (enabled) return
        job = scope.launch {
            val sm = context.getSystemService(SensorManager::class.java); val light = sm.getDefaultSensor(Sensor.TYPE_LIGHT) ?: return@launch
            while (true) {
                val lux = withTimeoutOrNull(2000) { suspendCancellableCoroutine<Float> { c ->
                    val l = object : SensorEventListener { override fun onSensorChanged(e: SensorEvent) { sm.unregisterListener(this); if (c.isActive) c.resume(e.values[0]) }; override fun onAccuracyChanged(s: Sensor?, a: Int) {} }
                    sm.registerListener(l, light, SensorManager.SENSOR_DELAY_NORMAL); c.invokeOnCancellation { sm.unregisterListener(l) }
                } }
                if (lux != null && Settings.System.canWrite(context)) mapper.update(lux)?.let { p ->
                    Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                    Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, Percent.toRange(p, 255, 1))
                }
                delay(periodMs)
            }
        }
    }
    fun stop() { job?.cancel(); job = null }
}

fun ambientCapability(ambient: AmbientController) = Capability("ambient_mode", "Auto-adjust screen brightness from the light sensor (action=on|off|status).",
    """{"type":"object","properties":{"action":{"type":"string","enum":["on","off","status"]}},"required":["action"]}""") { a ->
    when (a.str("action")) { "on" -> { ambient.start(); ActionResult.completed("Ambient brightness on") }; "off" -> { ambient.stop(); ActionResult.completed("Ambient brightness off") }
        "status" -> ActionResult.completed(if (ambient.enabled) "on" else "off", buildJsonObject { put("enabled", ambient.enabled) }); else -> ActionResult.failed("action must be on, off or status") }
}

fun auditLogCapability(file: File) = Capability("audit_log", "Tail of the tool-call audit log (tab-separated: time, tool, status, args). lines default 30, max 200.",
    """{"type":"object","properties":{"lines":{"type":"integer"},"tool":{"type":"string"}}}""") { a ->
    val n = (a.int("lines") ?: 30).coerceIn(1, 200); val tool = a.str("tool")
    val lines = runCatching { file.readLines() }.getOrDefault(emptyList()).filter { tool == null || it.split('\t').getOrNull(1) == tool }.takeLast(n)
    ActionResult.completed(if (lines.isEmpty()) "No entries" else lines.joinToString("\n"), buildJsonObject { put("count", lines.size) })
}

/** Draws a colored rectangle on screen around an element (found by text/desc) or explicit bounds, for a few seconds. */
class HighlightOverlay(private val context: Context) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private var view: View? = null
    suspend fun show(l: Int, t: Int, r: Int, b: Int, durationMs: Long, colorHex: Int = Color.parseColor("#FFEB3B")) {
        withContext(Dispatchers.Main) {
            clear()
            val v = View(context).apply { background = GradientDrawable().apply { setStroke(8, colorHex); cornerRadius = 12f; setColor(0x22FFFFFF) } }
            wm.addView(v, WindowManager.LayoutParams(r - l, b - t, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply { gravity = android.view.Gravity.TOP or android.view.Gravity.START; x = l; y = t })
            view = v
        }
        delay(durationMs); withContext(Dispatchers.Main) { clear() }
    }
    fun clear() { view?.let { runCatching { wm.removeView(it) } }; view = null }
}

fun highlightCapability(context: Context, highlight: HighlightOverlay, ui: UiAutomation) = Capability("overlay_highlight",
    "Draw a highlight box on screen around the element matching text/desc, or around x,y,w,h, for duration_ms (default 4000) to guide the user.",
    """{"type":"object","properties":{"text":{"type":"string"},"desc":{"type":"string"},"x":{"type":"integer"},"y":{"type":"integer"},"w":{"type":"integer"},"h":{"type":"integer"},"duration_ms":{"type":"integer"}}}""") { a ->
    if (!Settings.canDrawOverlays(context)) return@Capability ActionResult.failed("Overlay permission not granted")
    val dur = a.long("duration_ms") ?: 4000
    val rect: IntArray = if (a.int("x") != null) intArrayOf(a.int("x")!!, a.int("y") ?: 0, a.int("x")!! + (a.int("w") ?: 200), (a.int("y") ?: 0) + (a.int("h") ?: 100))
    else {
        val root = ui.tree() ?: return@Capability ActionResult.failed("No active window")
        val n = UiTree.find(root, UiQuery(text = a.str("text"), desc = a.str("desc"))).firstOrNull() ?: return@Capability ActionResult.failed("No element matches")
        intArrayOf(n.left, n.top, n.right, n.bottom)
    }
    highlight.show(rect[0], rect[1], rect[2], rect[3], dur); ActionResult.completed("Highlighted")
}

object SoundMath {
    /** RMS level of 16-bit PCM in dBFS (0 = full scale, about -90 = silence). */
    fun dbfs(pcm: ShortArray, count: Int): Double {
        if (count <= 0) return -90.0
        var sum = 0.0; for (i in 0 until count) { val v = pcm[i] / 32768.0; sum += v * v }
        val rms = kotlin.math.sqrt(sum / count); return if (rms <= 1e-5) -90.0 else (20 * kotlin.math.log10(rms)).coerceAtLeast(-90.0)
    }
    fun label(db: Double) = when { db > -20 -> "loud"; db > -40 -> "moderate"; db > -60 -> "quiet"; else -> "silent" }
}

@android.annotation.SuppressLint("MissingPermission")
fun soundLevelCapability() = Capability("sound_level", "Measure the room's sound level for duration_ms (default 1000, max 5000): dBFS and a label (silent/quiet/moderate/loud). Audio is analysed and discarded.",
    """{"type":"object","properties":{"duration_ms":{"type":"integer"}}}""") { a ->
    val ms = (a.long("duration_ms") ?: 1000).coerceIn(200, 5000)
    val rate = 16_000; val min = android.media.AudioRecord.getMinBufferSize(rate, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT)
    val rec = android.media.AudioRecord(android.media.MediaRecorder.AudioSource.MIC, rate, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 8000))
    if (rec.state != android.media.AudioRecord.STATE_INITIALIZED) { rec.release(); return@Capability ActionResult.failed("microphone unavailable") }
    val buf = ShortArray(rate / 10); var peak = -90.0; var total = 0.0; var n = 0
    try {
        rec.startRecording(); val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { val c = rec.read(buf, 0, buf.size); if (c <= 0) break; val db = SoundMath.dbfs(buf, c); peak = maxOf(peak, db); total += db; n++ }
    } finally { runCatching { rec.stop() }; rec.release() }
    if (n == 0) ActionResult.failed("no audio captured") else { val avg = total / n
        ActionResult.completed("%s (%.0f dBFS)".format(SoundMath.label(avg), avg), buildJsonObject { put("avg_dbfs", avg); put("peak_dbfs", peak); put("label", SoundMath.label(avg)) }) }
}

/** Camera-based change watcher: every [periodS] seconds grabs a small frame, compares thumbnails, reports changes. */
class VisualWatcher(private val camera: com.rishitgoklani.hermes_front_mobile.domain.CameraPort, private val scope: CoroutineScope, private val onChange: suspend (Int) -> Unit) {
    private var job: Job? = null
    val running get() = job?.isActive == true
    fun start(facing: com.rishitgoklani.hermes_front_mobile.domain.CameraFacing, periodS: Int) {
        stop(); val detector = com.rishitgoklani.hermes_front_mobile.domain.sense.ChangeDetector(threshold = 14.0, cooldownMs = 30_000)
        job = scope.launch {
            var prev: ByteArray? = null
            while (true) {
                runCatching { thumb(kotlinx.coroutines.withTimeout(12_000) { camera.capture(facing, 40_000) }) }.onFailure { android.util.Log.w("DeskbotVisual", "capture failed: ${it.message}") }.getOrNull()?.let { t ->
                    prev?.let { android.util.Log.d("DeskbotVisual", "diff=%.1f".format(com.rishitgoklani.hermes_front_mobile.domain.sense.FrameDiff.score(it, t))) }; prev = t
                    detector.update(t)?.let { onChange(it.toInt()) }
                }
                delay(periodS * 1000L)
            }
        }
    }
    fun stop() { job?.cancel(); job = null }
    private fun thumb(jpeg: ByteArray): ByteArray {
        val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = 4 }) ?: error("decode failed")
        val s = Bitmap.createScaledBitmap(bmp, 32, 32, true); val px = IntArray(32 * 32); s.getPixels(px, 0, 32, 0, 0, 32, 32)
        return ByteArray(px.size) { i -> val c = px[i]; (((c shr 16 and 255) * 299 + (c shr 8 and 255) * 587 + (c and 255) * 114) / 1000).toByte() }
    }
}

fun visualWatchCapability(w: VisualWatcher) = Capability("visual_watch", "Watch the camera for scene changes (something appears/moves): action=start|stop|status, period_s (default 60, min 10), facing=front|back. Changes fire routine event 'visual_change'.",
    """{"type":"object","properties":{"action":{"type":"string","enum":["start","stop","status"]},"period_s":{"type":"integer"},"facing":{"type":"string"}},"required":["action"]}""") { a ->
    when (a.str("action")) {
        "start" -> { w.start(if (a.str("facing") == "back") com.rishitgoklani.hermes_front_mobile.domain.CameraFacing.Back else com.rishitgoklani.hermes_front_mobile.domain.CameraFacing.Front, (a.int("period_s") ?: 60).coerceIn(10, 3600)); ActionResult.completed("Watching for changes") }
        "stop" -> { w.stop(); ActionResult.completed("Stopped") }
        "status" -> ActionResult.completed(if (w.running) "watching" else "stopped")
        else -> ActionResult.failed("action must be start, stop or status")
    }
}

/** Samples room loudness every [periodS] seconds and reports spikes (doorbell, clap, glass) via [onSpike]. */
class SoundWatcher(private val scope: CoroutineScope, private val measure: suspend () -> Double?, private val onSpike: suspend (String, Int) -> Unit) {
    private var job: Job? = null
    val running get() = job?.isActive == true
    fun start(periodS: Int) {
        stop(); val d = com.rishitgoklani.hermes_front_mobile.domain.sense.SpikeDetector(marginDb = 12.0, cooldownMs = 10_000)
        job = scope.launch { while (true) { measure()?.let { db -> d.update(db)?.let { _ -> onSpike(SoundMath.label(db), db.toInt()) } }; delay(periodS * 1000L) } }
    }
    fun stop() { job?.cancel(); job = null }
}

fun soundWatchCapability(w: SoundWatcher) = Capability("sound_watch", "Listen for sudden loud sounds (doorbell, clap): action=start|stop|status, period_s (default 15). Spikes fire routine event 'sound'. Audio is never stored.",
    """{"type":"object","properties":{"action":{"type":"string","enum":["start","stop","status"]},"period_s":{"type":"integer"}},"required":["action"]}""") { a ->
    when (a.str("action")) { "start" -> { w.start((a.int("period_s") ?: 15).coerceIn(5, 300)); ActionResult.completed("Listening for loud sounds") }; "stop" -> { w.stop(); ActionResult.completed("Stopped") }
        "status" -> ActionResult.completed(if (w.running) "listening" else "stopped"); else -> ActionResult.failed("action must be start, stop or status") }
}
