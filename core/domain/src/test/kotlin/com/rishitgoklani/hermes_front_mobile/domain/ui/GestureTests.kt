package com.rishitgoklani.hermes_front_mobile.domain.ui

import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.uiCapabilities
import com.rishitgoklani.hermes_front_mobile.testing.FakeUiAutomation
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureTests {
    @Test fun pinchOutSpreadsFingersSymmetrically() { val s = GesturePlanner.pinch(500, 1000, 2f, span = 100); assertEquals(2, s.size); assertEquals(400 to 1000, s[0].points.first()); assertEquals(300 to 1000, s[0].points.last()); assertEquals(700 to 1000, s[1].points.last()) }
    @Test fun pinchInMovesFingersTogether() { val s = GesturePlanner.pinch(500, 1000, 0.5f, span = 200); assertEquals(400 to 1000, s[0].points.last()); assertEquals(600 to 1000, s[1].points.last()) }
    @Test fun pinchScaleClamped() { val s = GesturePlanner.pinch(500, 500, 99f, span = 100); assertEquals(1, s[0].points.last().first) }
    @Test fun pinchStaysOnScreen() { val s = GesturePlanner.pinch(360, 800, 5f, span = 200, screenW = 720); assertTrue(s.flatMap { it.points }.all { it.first in 0..720 }) }
    @Test fun multiTouchBlockedOnXiaomiFamilyOnly() { assertTrue(MultiTouchPolicy.blocked("Xiaomi", "Redmi")); assertTrue(MultiTouchPolicy.blocked("Foo", "POCO")); assertTrue(!MultiTouchPolicy.blocked("Google", "google")); assertTrue(!MultiTouchPolicy.blocked(null, null)) }
    @Test fun twoFingerScrollParallel() { val s = GesturePlanner.twoFingerScroll(500, 500, 300); assertEquals(s[0].points.last().second, s[1].points.last().second) }

    private fun cap(ui: FakeUiAutomation, n: String) = uiCapabilities(ui).first { it.name == n }
    @Test fun pinchAndDragToolsDispatchStrokes() = runBlocking {
        val ui = FakeUiAutomation()
        assertEquals("completed", cap(ui, "ui_pinch").executor.execute(Json.parseToJsonElement("""{"scale":3}""").jsonObject).status); assertEquals(2, ui.gestures.single().size); assertEquals(1, ui.gestures.single()[0].points.last().first.coerceAtLeast(1).coerceAtMost(1)) /* clamped to the 1000px-wide fake screen */
        assertEquals("completed", cap(ui, "ui_drag").executor.execute(Json.parseToJsonElement("""{"x1":1,"y1":2,"x2":3,"y2":4}""").jsonObject).status); assertEquals(1, ui.swipes.size); assertEquals(1, ui.gestures.size)
        assertEquals("failed", cap(ui, "ui_drag").executor.execute(Json.parseToJsonElement("""{"x1":1}""").jsonObject).status)
    }
}

class DragViaShellTest {
    @Test fun shellDragPreferredWhenProvided() = runBlocking {
        val ui = FakeUiAutomation(); val calls = mutableListOf<List<Int>>()
        val cap = uiCapabilities(ui) { a, b, c, d -> calls += listOf(a, b, c, d); true }.first { it.name == "ui_drag" }
        assertEquals("completed", cap.executor.execute(Json.parseToJsonElement("""{"x1":1,"y1":2,"x2":3,"y2":4}""").jsonObject).status); assertEquals(listOf(listOf(1, 2, 3, 4)), calls); assertTrue(ui.swipes.isEmpty())
    }
}
