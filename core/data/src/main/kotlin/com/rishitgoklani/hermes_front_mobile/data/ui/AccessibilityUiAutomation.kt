package com.rishitgoklani.hermes_front_mobile.data.ui

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import com.rishitgoklani.hermes_front_mobile.domain.QualityPlanner
import com.rishitgoklani.hermes_front_mobile.domain.ui.GlobalAction
import com.rishitgoklani.hermes_front_mobile.domain.ui.MultiTouchPolicy
import com.rishitgoklani.hermes_front_mobile.domain.ui.UiAutomation
import com.rishitgoklani.hermes_front_mobile.domain.ui.UiNode
import com.rishitgoklani.hermes_front_mobile.domain.ui.UiQuery
import com.rishitgoklani.hermes_front_mobile.domain.ui.UiTree
import com.rishitgoklani.hermes_front_mobile.domain.ui.UiUnavailable
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

class AccessibilityUiAutomation(private val context: Context, private val maxJpegBytes: Int = 140_000) : UiAutomation {
    private fun service(): DeskbotAccessibilityService = DeskbotAccessibilityService.instance ?: run {
        ServiceEnabler.enableAccessibility(context); throw UiUnavailable()
    }

    override suspend fun tree(): UiNode? = service().rootInActiveWindow?.let { map(it, 0, intArrayOf(0)) }

    override suspend fun tap(x: Int, y: Int, durationMs: Long) = gesture(Path().apply { moveTo(x.toFloat(), y.toFloat()) }, durationMs)

    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long) =
        gesture(Path().apply { moveTo(x1.toFloat(), y1.toFloat()); lineTo(x2.toFloat(), y2.toFloat()) }, durationMs)

    private suspend fun gesture(path: Path, durationMs: Long): Boolean {
        val s = service()
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1))).build()
        return suspendCancellableCoroutine { cont ->
            val ok = s.dispatchGesture(g, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(d: GestureDescription?) { if (cont.isActive) cont.resume(true) }
                override fun onCancelled(d: GestureDescription?) { if (cont.isActive) cont.resume(false) }
            }, null)
            if (!ok && cont.isActive) cont.resume(false)
        }
    }

    override suspend fun global(action: GlobalAction): Boolean = service().performGlobalAction(when (action) {
        GlobalAction.Back -> AccessibilityService.GLOBAL_ACTION_BACK
        GlobalAction.Home -> AccessibilityService.GLOBAL_ACTION_HOME
        GlobalAction.Recents -> AccessibilityService.GLOBAL_ACTION_RECENTS
        GlobalAction.Notifications -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
        GlobalAction.QuickSettings -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
        GlobalAction.LockScreen -> AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
        GlobalAction.PowerDialog -> AccessibilityService.GLOBAL_ACTION_POWER_DIALOG
        GlobalAction.TakeScreenshot -> AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
    })

    override suspend fun setText(text: String): Boolean {
        val root = service().rootInActiveWindow ?: return false
        val field = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: firstEditable(root) ?: return false
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    override suspend fun clickNode(query: UiQuery): Boolean {
        val root = service().rootInActiveWindow ?: return false
        var seen = 0
        fun walk(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (UiTree.matches(shallow(n), query) && seen++ == query.index) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { c -> walk(c)?.let { return it } }
            return null
        }
        var target = walk(root) ?: return false
        while (!target.isClickable) target = target.parent ?: return false
        return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    override suspend fun screenshot(): ByteArray? {
        val s = service()
        return suspendCancellableCoroutine { cont ->
            s.takeScreenshot(Display.DEFAULT_DISPLAY, context.mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val hw = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                    val bmp = hw?.copy(Bitmap.Config.ARGB_8888, false)
                    result.hardwareBuffer.close()
                    cont.resume(bmp?.let { shrink(it) })
                }
                override fun onFailure(errorCode: Int) { cont.resume(null) }
            })
        }
    }

    private fun shrink(bitmap: Bitmap): ByteArray {
        val scale = QualityPlanner.scaleFor(bitmap.width, bitmap.height, 960)
        val b = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
        fun jpeg(q: Int) = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, q, it) }.toByteArray()
        return jpeg(QualityPlanner.pick(maxJpegBytes) { jpeg(it).size })
    }

    override suspend fun gesture(strokes: List<com.rishitgoklani.hermes_front_mobile.domain.ui.Stroke>): Boolean {
        if (strokes.size > 1 && MultiTouchPolicy.blocked(android.os.Build.MANUFACTURER, android.os.Build.BRAND)) throw UiUnavailable(MultiTouchPolicy.REASON)
        val s = service(); val b = GestureDescription.Builder()
        strokes.take(GestureDescription.getMaxStrokeCount().coerceAtMost(10)).forEach { st ->
            val path = Path().apply { st.points.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat()) } }
            b.addStroke(GestureDescription.StrokeDescription(path, st.startMs, st.durationMs.coerceAtLeast(1)))
        }
        return suspendCancellableCoroutine { cont ->
            val ok = s.dispatchGesture(b.build(), object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(d: GestureDescription?) { if (cont.isActive) cont.resume(true) }
                override fun onCancelled(d: GestureDescription?) { if (cont.isActive) cont.resume(false) }
            }, null)
            if (!ok && cont.isActive) cont.resume(false)
        }
    }

    override suspend fun screenSize(): Pair<Int, Int> = context.resources.displayMetrics.let { it.widthPixels to it.heightPixels }

    private fun firstEditable(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (n.isEditable) return n
        for (i in 0 until n.childCount) n.getChild(i)?.let { c -> firstEditable(c)?.let { return it } }
        return null
    }

    private fun shallow(n: AccessibilityNodeInfo): UiNode {
        val r = Rect().also(n::getBoundsInScreen)
        return UiNode(
            text = n.text?.toString().orEmpty(), desc = n.contentDescription?.toString().orEmpty(), id = n.viewIdResourceName.orEmpty(),
            cls = n.className?.toString().orEmpty(), pkg = n.packageName?.toString().orEmpty(),
            left = r.left, top = r.top, right = r.right, bottom = r.bottom, clickable = n.isClickable, scrollable = n.isScrollable,
            editable = n.isEditable, enabled = n.isEnabled, focused = n.isFocused, checked = if (n.isCheckable) n.isChecked else null,
        )
    }

    private fun map(n: AccessibilityNodeInfo, depth: Int, count: IntArray): UiNode {
        count[0]++
        val kids = if (depth > 30 || count[0] > 2500) emptyList() else (0 until n.childCount).mapNotNull { i -> n.getChild(i)?.takeIf { it.isVisibleToUser }?.let { map(it, depth + 1, count) } }
        return shallow(n).copy(children = kids)
    }
}
