package com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.ui.GesturePlanner
import com.rishitgoklani.hermes_front_mobile.domain.ui.GlobalAction
import com.rishitgoklani.hermes_front_mobile.domain.ui.SwipeDirection
import com.rishitgoklani.hermes_front_mobile.domain.ui.UiAutomation
import com.rishitgoklani.hermes_front_mobile.domain.ui.UiNode
import com.rishitgoklani.hermes_front_mobile.domain.ui.UiQuery
import com.rishitgoklani.hermes_front_mobile.domain.ui.UiTree
import com.rishitgoklani.hermes_front_mobile.domain.ui.UiUnavailable
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.contentOrNull
private fun JsonObject.int(k: String) = this[k]?.jsonPrimitive?.intOrNull
private fun JsonObject.bool(k: String) = this[k]?.jsonPrimitive?.booleanOrNull

internal fun queryFrom(a: JsonObject) = UiQuery(text = a.str("text"), exact = a.bool("exact") ?: false, id = a.str("id"), desc = a.str("desc"), index = a.int("index") ?: 0)

private inline fun guarded(block: () -> ActionResult): ActionResult = try { block() } catch (e: UiUnavailable) { ActionResult.failed(e.message ?: "unavailable") }

private const val SCHEMA_QUERY = """"text":{"type":"string"},"exact":{"type":"boolean"},"id":{"type":"string","description":"resource id suffix"},"desc":{"type":"string"},"index":{"type":"integer"}"""

/** [dragViaShell] performs a press-hold-drag with the OS `input draganddrop` (root); without it ui_drag falls back to a slow single-finger swipe. */
fun uiCapabilities(ui: UiAutomation, dragViaShell: (suspend (Int, Int, Int, Int) -> Boolean)? = null): List<Capability> = listOf(
    Capability("ui_tree", "Dump the current screen as compact lines (label, id, centre coords, click/edit/scroll flags). Use before ui_click.",
        """{"type":"object","properties":{"max_nodes":{"type":"integer"}}}""") { a ->
        guarded {
            val root = ui.tree() ?: return@guarded ActionResult.failed("No active window")
            ActionResult.completed(UiTree.compact(root, a.int("max_nodes") ?: 120), buildJsonObject { put("package", root.pkg) })
        }
    },
    Capability("ui_click", "Tap an element found by text/id/desc (preferred), or tap raw x,y. Set long=true for long press.",
        """{"type":"object","properties":{$SCHEMA_QUERY,"x":{"type":"integer"},"y":{"type":"integer"},"long":{"type":"boolean"}}}""") { a ->
        guarded {
            val long = a.bool("long") ?: false
            val x = a.int("x"); val y = a.int("y")
            if (x != null && y != null) {
                return@guarded if (ui.tap(x, y, if (long) 800 else 50)) ActionResult.completed("Tapped $x,$y") else ActionResult.failed("Tap failed")
            }
            val q = queryFrom(a)
            if (q.text == null && q.id == null && q.desc == null) return@guarded ActionResult.failed("Give text/id/desc or x,y")
            val root = ui.tree() ?: return@guarded ActionResult.failed("No active window")
            val hit = UiTree.find(root, q).getOrNull(q.index) ?: return@guarded ActionResult.failed("No element matches")
            val ok = if (long) ui.tap(hit.centerX, hit.centerY, 800) else (ui.clickNode(q) || ui.tap(hit.centerX, hit.centerY))
            if (ok) ActionResult.completed("Clicked \"${hit.label.take(40)}\"") else ActionResult.failed("Click failed")
        }
    },
    Capability("ui_swipe", "Swipe by direction (scrolls content the opposite way) or explicit coordinates.",
        """{"type":"object","properties":{"direction":{"type":"string","enum":["up","down","left","right"]},"x1":{"type":"integer"},"y1":{"type":"integer"},"x2":{"type":"integer"},"y2":{"type":"integer"},"duration_ms":{"type":"integer"}}}""") { a ->
        guarded {
            val dur = (a.int("duration_ms") ?: 300).toLong()
            val (w, h) = ui.screenSize()
            val coords = if (a.int("x1") != null) listOf(a.int("x1")!!, a.int("y1") ?: 0, a.int("x2") ?: 0, a.int("y2") ?: 0)
            else SwipePlanner.plan(parseDirection(a.str("direction")) ?: return@guarded ActionResult.failed("direction or coordinates required"), w, h)
            if (ui.swipe(coords[0], coords[1], coords[2], coords[3], dur)) ActionResult.completed("Swiped") else ActionResult.failed("Swipe failed")
        }
    },
    Capability("ui_pinch", "Two-finger pinch/zoom around x,y: scale>1 zooms in, <1 zooms out (default centre of screen).",
        """{"type":"object","properties":{"x":{"type":"integer"},"y":{"type":"integer"},"scale":{"type":"number"},"span":{"type":"integer"}}}""") { a ->
        guarded {
            val (w, h) = ui.screenSize(); val scale = (a["scale"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()) ?: 2f
            if (ui.gesture(GesturePlanner.pinch(a.int("x") ?: (w / 2), a.int("y") ?: (h / 2), scale, a.int("span") ?: 200, screenW = w))) ActionResult.completed("Pinch x$scale") else ActionResult.failed("Gesture failed")
        }
    },
    Capability("ui_drag", "Long-press at x1,y1 then drag to x2,y2 (drag-and-drop, reorder).",
        """{"type":"object","properties":{"x1":{"type":"integer"},"y1":{"type":"integer"},"x2":{"type":"integer"},"y2":{"type":"integer"}},"required":["x1","y1","x2","y2"]}""") { a ->
        guarded {
            val c = listOf("x1", "y1", "x2", "y2").map { a.int(it) ?: return@guarded ActionResult.failed("$it required") }
            val ok = dragViaShell?.invoke(c[0], c[1], c[2], c[3]) ?: ui.swipe(c[0], c[1], c[2], c[3], 900)
            if (ok) ActionResult.completed("Dragged") else ActionResult.failed("Drag failed")
        }
    },
    Capability("ui_type", "Type text into the focused input field (replaces its content).",
        """{"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}""") { a ->
        guarded {
            val t = a.str("text") ?: return@guarded ActionResult.failed("text required")
            if (ui.setText(t)) ActionResult.completed("Typed ${t.length} chars") else ActionResult.failed("No focused editable field")
        }
    },
    Capability("ui_global", "System navigation: back, home, recents, notifications, quick_settings, lock, power_dialog.",
        """{"type":"object","properties":{"action":{"type":"string","enum":["back","home","recents","notifications","quick_settings","lock","power_dialog"]}},"required":["action"]}""") { a ->
        guarded {
            val act = parseGlobal(a.str("action")) ?: return@guarded ActionResult.failed("unknown action")
            if (ui.global(act)) ActionResult.completed(act.name.lowercase()) else ActionResult.failed("Action refused")
        }
    },
    Capability("ui_wait_for", "Wait until text appears on screen (or gone=true: disappears). Returns when found or after timeout_ms (default 8000).",
        """{"type":"object","properties":{"text":{"type":"string"},"gone":{"type":"boolean"},"timeout_ms":{"type":"integer"}},"required":["text"]}""") { a ->
        guarded {
            val text = a.str("text") ?: return@guarded ActionResult.failed("text required")
            val gone = a.bool("gone") ?: false
            val deadline = (a.int("timeout_ms") ?: 8000).coerceAtMost(30_000)
            var waited = 0
            while (waited <= deadline) {
                val present = ui.tree()?.let { UiTree.find(it, UiQuery(text = text)).isNotEmpty() } ?: false
                if (present != gone) return@guarded ActionResult.completed(if (gone) "'$text' gone" else "'$text' visible")
                delay(400); waited += 400
            }
            ActionResult.failed("timeout waiting for '$text'")
        }
    },
    Capability("screen_state", "Foreground app package, whether a keyboard looks open, and screen size.") {
        guarded {
            val root = ui.tree()
            val (w, h) = ui.screenSize()
            ActionResult.completed("${root?.pkg ?: "unknown"} ${w}x$h", buildJsonObject {
                put("package", root?.pkg ?: ""); put("keyboard", root?.let(UiTree::keyboardLikely) ?: false); put("width", w); put("height", h)
            })
        }
    },
    Capability("screenshot", "Capture the screen as base64 JPEG (data.image_base64).") {
        guarded {
            val bytes = ui.screenshot() ?: return@guarded ActionResult.failed("Screenshot unavailable")
            ActionResult.completed("Screenshot ${bytes.size / 1024} KB", buildJsonObject {
                put("image_base64", java.util.Base64.getEncoder().encodeToString(bytes)); put("mime", "image/jpeg")
            })
        }
    },
)

fun parseDirection(s: String?): SwipeDirection? = SwipeDirection.entries.firstOrNull { it.name.equals(s, true) }
fun parseGlobal(s: String?): GlobalAction? = when (s?.lowercase()) {
    "back" -> GlobalAction.Back; "home" -> GlobalAction.Home; "recents" -> GlobalAction.Recents
    "notifications" -> GlobalAction.Notifications; "quick_settings" -> GlobalAction.QuickSettings
    "lock" -> GlobalAction.LockScreen; "power_dialog" -> GlobalAction.PowerDialog; else -> null
}

/** Swipe coordinates for a direction: "up" means finger moves up (content scrolls down). */
object SwipePlanner {
    fun plan(dir: SwipeDirection, w: Int, h: Int): List<Int> {
        val cx = w / 2; val cy = h / 2
        return when (dir) {
            SwipeDirection.Up -> listOf(cx, (h * 0.75).toInt(), cx, (h * 0.25).toInt())
            SwipeDirection.Down -> listOf(cx, (h * 0.25).toInt(), cx, (h * 0.75).toInt())
            SwipeDirection.Left -> listOf((w * 0.8).toInt(), cy, (w * 0.2).toInt(), cy)
            SwipeDirection.Right -> listOf((w * 0.2).toInt(), cy, (w * 0.8).toInt(), cy)
        }
    }
}
