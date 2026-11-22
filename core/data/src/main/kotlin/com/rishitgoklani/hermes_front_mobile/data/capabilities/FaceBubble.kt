package com.rishitgoklani.hermes_front_mobile.data.capabilities

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.face.BubbleFace
import com.rishitgoklani.hermes_front_mobile.domain.face.FaceController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Floating face (#52): a small draggable overlay window showing the bot's eyes above other apps. Tap opens the app. */
class FaceBubble(private val context: Context, private val face: FaceController, private val scope: CoroutineScope, private val launch: Intent) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private var view: BubbleView? = null
    private var job: Job? = null
    val showing get() = view != null

    suspend fun show(): Boolean = withContext(Dispatchers.Main) {
        if (!Settings.canDrawOverlays(context)) return@withContext false
        if (view != null) return@withContext true
        val size = (context.resources.displayMetrics.density * 96).toInt()
        val v = BubbleView(context)
        val lp = WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
            .apply { gravity = Gravity.TOP or Gravity.START; x = 24; y = 240 }
        v.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
            override fun onTouch(view: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; moved = false }
                    MotionEvent.ACTION_MOVE -> { if (kotlin.math.abs(e.rawX - sx) > 12 || kotlin.math.abs(e.rawY - sy) > 12) moved = true; lp.x = ox + (e.rawX - sx).toInt(); lp.y = oy + (e.rawY - sy).toInt(); runCatching { wm.updateViewLayout(v, lp) } }
                    MotionEvent.ACTION_UP -> if (!moved) runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                return true
            }
        })
        wm.addView(v, lp); view = v
        job = scope.launch { face.state.collect { s -> withContext(Dispatchers.Main) { v.face = BubbleFace.from(s); v.invalidate() } } }
        true
    }

    suspend fun hide() { job?.cancel(); withContext(Dispatchers.Main) { view?.let { runCatching { wm.removeView(it) } }; view = null } }

    private class BubbleView(context: Context) : View(context) {
        var face = BubbleFace(0.9f, false)
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE605070A.toInt() }
        private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            c.drawCircle(w / 2, h / 2, w / 2, bg)
            ink.color = face.colorHex.toInt(); ink.style = Paint.Style.FILL
            val ew = w * 0.2f; val eh = h * 0.28f * face.openness
            for (cx in floatArrayOf(w * 0.32f, w * 0.68f)) {
                if (face.arcs) { ink.style = Paint.Style.STROKE; ink.strokeWidth = w * 0.06f; ink.strokeCap = Paint.Cap.ROUND; c.drawArc(RectF(cx - ew / 2, h * 0.38f, cx + ew / 2, h * 0.38f + ew), 180f, 180f, false, ink) }
                else c.drawRoundRect(RectF(cx - ew / 2, h / 2 - eh / 2, cx + ew / 2, h / 2 + eh / 2), ew / 2, ew / 2, ink)
            }
        }
    }
}

fun faceBubbleCapability(bubble: FaceBubble) = Capability(
    "face_bubble", "Floating bot face over other apps (action=on|off|status). Drag to move, tap to open the app.",
    """{"type":"object","properties":{"action":{"type":"string","enum":["on","off","status"]}},"required":["action"]}""",
) { a ->
    when (a["action"]?.jsonPrimitive?.contentOrNull) {
        "on" -> if (bubble.show()) ActionResult.completed("Face bubble on") else ActionResult.failed("Overlay permission not granted")
        "off" -> { bubble.hide(); ActionResult.completed("Face bubble off") }
        "status" -> ActionResult.completed(if (bubble.showing) "on" else "off")
        else -> ActionResult.failed("action must be on, off or status")
    }
}
