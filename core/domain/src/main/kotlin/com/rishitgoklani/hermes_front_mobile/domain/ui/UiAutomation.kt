package com.rishitgoklani.hermes_front_mobile.domain.ui

enum class GlobalAction { Back, Home, Recents, Notifications, QuickSettings, LockScreen, PowerDialog, TakeScreenshot }
enum class SwipeDirection { Up, Down, Left, Right }

/** Port over the accessibility service. All calls throw [UiUnavailable] when the service is not enabled/connected. */
interface UiAutomation {
    suspend fun tree(): UiNode?
    suspend fun tap(x: Int, y: Int, durationMs: Long = 50): Boolean
    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 300): Boolean
    suspend fun global(action: GlobalAction): Boolean
    suspend fun setText(text: String): Boolean
    suspend fun clickNode(query: UiQuery): Boolean
    suspend fun screenshot(): ByteArray?
    suspend fun screenSize(): Pair<Int, Int>
    /** Several simultaneous/timed strokes in one gesture (pinch, drag-and-drop, two-finger scroll). */
    suspend fun gesture(strokes: List<Stroke>): Boolean
}

/** One finger path: points are visited linearly over [durationMs], starting [startMs] after the gesture begins. */
data class Stroke(val points: List<Pair<Int, Int>>, val startMs: Long = 0, val durationMs: Long = 300)

object GesturePlanner {
    /** Two fingers move symmetrically around (cx,cy). scale>1 spreads (zoom in), <1 pinches (zoom out). */
    fun pinch(cx: Int, cy: Int, scale: Float, span: Int = 200, durationMs: Long = 400, screenW: Int = Int.MAX_VALUE): List<Stroke> {
        val s = span.coerceAtLeast(40); val maxHalf = minOf(cx, screenW - cx).coerceAtLeast(1)   // keep every point on screen
        val end = (s * scale.coerceIn(0.2f, 5f)).toInt().coerceAtMost(maxHalf - 1); val start = s.coerceAtMost(maxHalf - 1)
        return listOf(Stroke(listOf(cx - start to cy, cx - end to cy), 0, durationMs), Stroke(listOf(cx + start to cy, cx + end to cy), 0, durationMs))
    }
    fun twoFingerScroll(cx: Int, cy: Int, dy: Int, durationMs: Long = 400): List<Stroke> =
        listOf(Stroke(listOf(cx - 60 to cy, cx - 60 to cy + dy), 0, durationMs), Stroke(listOf(cx + 60 to cy, cx + 60 to cy + dy), 0, durationMs))
}

class UiUnavailable(message: String = "Accessibility service is not enabled") : Exception(message)
