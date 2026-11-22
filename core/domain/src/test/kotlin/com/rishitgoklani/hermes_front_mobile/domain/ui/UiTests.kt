package com.rishitgoklani.hermes_front_mobile.domain.ui

import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.SwipePlanner
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.uiCapabilities
import com.rishitgoklani.hermes_front_mobile.testing.FakeUiAutomation
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiTests {
    private val tree = UiNode(pkg = "com.app", cls = "FrameLayout", children = listOf(
        UiNode(text = "Wi-Fi", cls = "android.widget.TextView", left = 0, top = 100, right = 200, bottom = 160, clickable = true, id = "com.app:id/title"),
        UiNode(text = "Wi-Fi calling", left = 0, top = 200, right = 200, bottom = 260, clickable = true),
        UiNode(desc = "Search", cls = "android.widget.ImageButton", left = 900, top = 0, right = 1000, bottom = 100, clickable = true),
        UiNode(cls = "android.widget.EditText", editable = true, left = 0, top = 300, right = 500, bottom = 360),
    ))
    private fun caps(ui: FakeUiAutomation) = uiCapabilities(ui).associateBy { it.name }
    private fun args(vararg p: Pair<String, Any>) = JsonObject(p.associate { (k, v) -> k to (if (v is Boolean) JsonPrimitive(v) else if (v is Int) JsonPrimitive(v) else JsonPrimitive(v.toString())) })

    @Test fun findsByTextContainsAndExact() {
        assertEquals(2, UiTree.find(tree, UiQuery(text = "wi-fi")).size)
        assertEquals(1, UiTree.find(tree, UiQuery(text = "wi-fi", exact = true)).size)
    }
    @Test fun findsByDescAndIdSuffix() {
        assertEquals("Search", UiTree.find(tree, UiQuery(desc = "search")).single().desc)
        assertEquals("Wi-Fi", UiTree.find(tree, UiQuery(id = "id/title")).single().text)
    }
    @Test fun emptyQueryMatchesNothing() = assertTrue(UiTree.find(tree, UiQuery()).isEmpty())
    @Test fun compactListsInteractiveNodesWithCoords() {
        val s = UiTree.compact(tree)
        assertTrue(s.contains("\"Wi-Fi\"")); assertTrue(s.contains("(100,130)")); assertTrue(s.contains("edit"))
    }
    @Test fun compactTruncates() = assertTrue(UiTree.compact(tree, maxNodes = 1).contains("more"))
    @Test fun swipePlannerDirections() {
        assertEquals(listOf(500, 1500, 500, 500), SwipePlanner.plan(SwipeDirection.Up, 1000, 2000))
        assertEquals(listOf(200, 1000, 800, 1000), SwipePlanner.plan(SwipeDirection.Right, 1000, 2000))
    }
    @Test fun clickByTextUsesNodeClick() = runBlocking {
        val ui = FakeUiAutomation(tree)
        val r = caps(ui)["ui_click"]!!.executor.execute(args("text" to "Search"))
        assertEquals("completed", r.status); assertEquals(1, ui.clicked.size)
    }
    @Test fun clickFallsBackToTapWhenNodeClickFails() = runBlocking {
        val ui = FakeUiAutomation(tree).also { it.clickNodeResult = false }
        caps(ui)["ui_click"]!!.executor.execute(args("text" to "Search"))
        assertEquals(listOf(950 to 50), ui.taps)
    }
    @Test fun clickByCoordinates() = runBlocking {
        val ui = FakeUiAutomation(tree); caps(ui)["ui_click"]!!.executor.execute(args("x" to 5, "y" to 6)); assertEquals(listOf(5 to 6), ui.taps)
    }
    @Test fun clickWithoutSelectorFails() = runBlocking { assertEquals("failed", caps(FakeUiAutomation(tree))["ui_click"]!!.executor.execute(args()).status) }
    @Test fun clickMissingElementFails() = runBlocking { assertEquals("failed", caps(FakeUiAutomation(tree))["ui_click"]!!.executor.execute(args("text" to "zzz")).status) }
    @Test fun swipeByDirection() = runBlocking {
        val ui = FakeUiAutomation(tree); caps(ui)["ui_swipe"]!!.executor.execute(args("direction" to "up")); assertEquals(listOf(listOf(500, 1500, 500, 500)), ui.swipes)
    }
    @Test fun typeAndGlobal() = runBlocking {
        val ui = FakeUiAutomation(tree)
        caps(ui)["ui_type"]!!.executor.execute(args("text" to "hello")); caps(ui)["ui_global"]!!.executor.execute(args("action" to "home"))
        assertEquals(listOf("hello"), ui.typed); assertEquals(listOf(GlobalAction.Home), ui.globals)
    }
    @Test fun unknownGlobalFails() = runBlocking { assertEquals("failed", caps(FakeUiAutomation(tree))["ui_global"]!!.executor.execute(args("action" to "explode")).status) }
    @Test fun unavailableServiceReportedAsFailed() = runBlocking {
        val r = caps(FakeUiAutomation(tree, available = false))["ui_tree"]!!.executor.execute(args()); assertEquals("failed", r.status); assertTrue(r.message.contains("Accessibility"))
    }
    @Test fun waitForFindsImmediately() = runBlocking { assertEquals("completed", caps(FakeUiAutomation(tree))["ui_wait_for"]!!.executor.execute(args("text" to "Search")).status) }
    @Test fun waitForTimesOut() = runBlocking { assertEquals("failed", caps(FakeUiAutomation(tree))["ui_wait_for"]!!.executor.execute(args("text" to "nope", "timeout_ms" to 400)).status) }
    @Test fun screenStateReportsPackage() = runBlocking { assertTrue(caps(FakeUiAutomation(tree))["screen_state"]!!.executor.execute(args()).message.startsWith("com.app")) }
    @Test fun screenshotReturnsBase64() = runBlocking { assertEquals("completed", caps(FakeUiAutomation(tree))["screenshot"]!!.executor.execute(args()).status) }
}
