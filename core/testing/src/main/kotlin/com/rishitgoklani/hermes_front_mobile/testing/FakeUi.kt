package com.rishitgoklani.hermes_front_mobile.testing

import com.rishitgoklani.hermes_front_mobile.domain.ui.GlobalAction
import com.rishitgoklani.hermes_front_mobile.domain.ui.UiAutomation
import com.rishitgoklani.hermes_front_mobile.domain.ui.UiNode
import com.rishitgoklani.hermes_front_mobile.domain.ui.UiQuery
import com.rishitgoklani.hermes_front_mobile.domain.ui.UiUnavailable

class FakeUiAutomation(var root: UiNode? = null, private val available: Boolean = true, val size: Pair<Int, Int> = 1000 to 2000) : UiAutomation {
    val taps = mutableListOf<Pair<Int, Int>>(); val swipes = mutableListOf<List<Int>>(); val globals = mutableListOf<GlobalAction>()
    val typed = mutableListOf<String>(); val clicked = mutableListOf<UiQuery>(); var clickNodeResult = true
    private fun check() { if (!available) throw UiUnavailable() }
    override suspend fun tree(): UiNode? { check(); return root }
    override suspend fun tap(x: Int, y: Int, durationMs: Long): Boolean { check(); taps += x to y; return true }
    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): Boolean { check(); swipes += listOf(x1, y1, x2, y2); return true }
    override suspend fun global(action: GlobalAction): Boolean { check(); globals += action; return true }
    override suspend fun setText(text: String): Boolean { check(); typed += text; return true }
    override suspend fun clickNode(query: UiQuery): Boolean { check(); clicked += query; return clickNodeResult }
    override suspend fun screenshot(): ByteArray? { check(); return byteArrayOf(9, 9) }
    val gestures = mutableListOf<List<com.rishitgoklani.hermes_front_mobile.domain.ui.Stroke>>()
    override suspend fun gesture(strokes: List<com.rishitgoklani.hermes_front_mobile.domain.ui.Stroke>): Boolean { check(); gestures += strokes; return true }
    override suspend fun screenSize(): Pair<Int, Int> { check(); return size }
}
