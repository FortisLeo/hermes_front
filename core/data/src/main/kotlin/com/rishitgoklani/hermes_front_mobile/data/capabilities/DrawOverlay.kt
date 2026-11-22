package com.rishitgoklani.hermes_front_mobile.data.capabilities

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.ui.DrawParser
import com.rishitgoklani.hermes_front_mobile.domain.ui.DrawShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.math.atan2

/** Full-screen click-through annotation layer (#60): circles, boxes, arrows and labels over any app for a few seconds. */
class DrawOverlay(private val context: Context) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private var view: View? = null

    suspend fun show(shapes: List<DrawShape>, durationMs: Long) {
        withContext(Dispatchers.Main) {
            clear()
            val v = object : View(context) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 8f; strokeCap = Paint.Cap.ROUND; textSize = 44f }
                override fun onDraw(c: Canvas) { shapes.forEach { draw(c, it) } }
                fun draw(c: Canvas, s: DrawShape) {
                    p.color = s.color.toInt(); p.style = Paint.Style.STROKE
                    when (s) {
                        is DrawShape.Circle -> c.drawCircle(s.x, s.y, s.r, p)
                        is DrawShape.Rect -> c.drawRect(s.l, s.t, s.r, s.b, p)
                        is DrawShape.Line -> { c.drawLine(s.x1, s.y1, s.x2, s.y2, p); if (s.arrow) {
                            val a = atan2(s.y2 - s.y1, s.x2 - s.x1); val h = 36f; val path = Path()
                            path.moveTo(s.x2, s.y2); path.lineTo(s.x2 - h * Math.cos(a - 0.5).toFloat(), s.y2 - h * Math.sin(a - 0.5).toFloat())
                            path.moveTo(s.x2, s.y2); path.lineTo(s.x2 - h * Math.cos(a + 0.5).toFloat(), s.y2 - h * Math.sin(a + 0.5).toFloat()); c.drawPath(path, p) } }
                        is DrawShape.Label -> { p.style = Paint.Style.FILL; c.drawText(s.text, s.x, s.y, p) }
                    }
                }
            }
            wm.addView(v, WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT))
            view = v
        }
        delay(durationMs); withContext(Dispatchers.Main) { clear() }
    }
    fun clear() { view?.let { runCatching { wm.removeView(it) } }; view = null }
}

fun drawOverlayCapability(context: Context, overlay: DrawOverlay) = Capability(
    "overlay_draw", "Draw annotations over any app for duration_ms (default 5000, max 30000): shapes = [{type: circle|rect|line|arrow|text, x, y, r|w,h|x2,y2|text, color #RRGGBB}].",
    """{"type":"object","properties":{"shapes":{"type":"array","items":{"type":"object"}},"duration_ms":{"type":"integer"}},"required":["shapes"]}""",
) { a ->
    if (!Settings.canDrawOverlays(context)) return@Capability ActionResult.failed("Overlay permission not granted")
    val arr = a["shapes"] as? JsonArray ?: return@Capability ActionResult.failed("shapes must be an array")
    val (shapes, err) = DrawParser.parse(arr)
    if (err != null) return@Capability ActionResult.failed(err)
    val dur = (a["duration_ms"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 5000L).coerceIn(500L, 30_000L)
    overlay.show(shapes, dur); ActionResult.completed("Drew ${shapes.size} shape(s)")
}
