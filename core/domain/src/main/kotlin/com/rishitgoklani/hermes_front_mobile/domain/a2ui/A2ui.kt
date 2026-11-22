package com.rishitgoklani.hermes_front_mobile.domain.a2ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Where a screen is drawn: beside the face (desk), above other apps (overlay), or the whole display (full). */
enum class Surface { Desk, Overlay, Full }

/** Component catalog: type -> required props, and whether it holds children. The renderer must support every entry. */
object A2uiCatalog {
    data class Spec(val required: Set<String> = emptySet(), val container: Boolean = false)
    val components: Map<String, Spec> = mapOf(
        "Text" to Spec(setOf("text")), "Card" to Spec(container = true), "Row" to Spec(container = true), "Column" to Spec(container = true),
        "Button" to Spec(setOf("label", "action")), "Toggle" to Spec(setOf("label", "bind")), "Slider" to Spec(setOf("label", "bind")),
        "Gauge" to Spec(setOf("label", "value")), "Timer" to Spec(setOf("label")), "ConfirmCard" to Spec(setOf("text", "yes", "no")),
        "AppTile" to Spec(setOf("label", "package")), "NotificationCard" to Spec(setOf("app", "title")), "DeviceToggle" to Spec(setOf("device")),
        "StatusGauge" to Spec(setOf("kind")), "Image" to Spec(setOf("base64")), "Divider" to Spec(), "PersonaView" to Spec(), "SkillCard" to Spec(setOf("name")), "WebEmbed" to Spec(setOf("html")),
    )
    const val MAX_NODES = 200
    const val MAX_DEPTH = 8
    const val MAX_ARGS_SIZE = 200_000
}

object A2uiValidator {
    /** Returns human-readable problems; empty list means the tree is renderable. */
    fun validate(root: JsonElement): List<String> {
        val errors = mutableListOf<String>(); var count = 0
        if (root.toString().length > A2uiCatalog.MAX_ARGS_SIZE) return listOf("screen too large")
        fun walk(e: JsonElement, depth: Int, path: String) {
            if (e !is JsonObject) { errors += "$path: component must be an object"; return }
            if (++count > A2uiCatalog.MAX_NODES) { if (count == A2uiCatalog.MAX_NODES + 1) errors += "more than ${A2uiCatalog.MAX_NODES} components"; return }
            if (depth > A2uiCatalog.MAX_DEPTH) { errors += "$path: nesting deeper than ${A2uiCatalog.MAX_DEPTH}"; return }
            val type = e["type"]?.jsonPrimitive?.contentOrNull
            val spec = A2uiCatalog.components[type]
            if (type == null || spec == null) { errors += "$path: unknown component '${type}' (valid: ${A2uiCatalog.components.keys.joinToString()})"; return }
            spec.required.filter { !e.containsKey(it) }.forEach { errors += "$path ($type): missing '$it'" }
            val children = e["children"]
            if (children != null) {
                if (!spec.container) errors += "$path ($type): cannot have children"
                else if (children !is JsonArray) errors += "$path ($type): children must be an array"
                else children.forEachIndexed { i, c -> walk(c, depth + 1, "$path.children[$i]") }
            }
        }
        walk(root, 1, "root")
        return errors
    }
}

/** What the user is looking at on one surface. [version] bumps on every show so the UI can react to repeats. */
data class Screen(val root: JsonObject, val speak: String = "", val version: Long = 0, val expiresAt: Long? = null, val keepOnAction: Boolean = false)

/** Holds the current screen per surface plus bound control values (toggles/sliders update locally, no model round trip). */
class A2uiStore(private val clock: () -> Long = System::currentTimeMillis) {
    private val _screens = MutableStateFlow<Map<Surface, Screen>>(emptyMap())
    val screens: StateFlow<Map<Surface, Screen>> = _screens
    private val _values = MutableStateFlow<Map<String, JsonElement>>(emptyMap())
    val values: StateFlow<Map<String, JsonElement>> = _values
    private var version = 0L

    /** [ttlMs] > 0 makes the screen expire; [keepOnAction] keeps it after a button action (default: any action hides it). */
    @Synchronized fun show(surface: Surface, root: JsonObject, speak: String = "", ttlMs: Long = 0, keepOnAction: Boolean = false) {
        _screens.update { it + (surface to Screen(root, speak, ++version, if (ttlMs > 0) clock() + ttlMs else null, keepOnAction)) }
    }
    /** Drops expired screens; returns true if anything was removed. Call periodically from the UI. */
    fun sweep(): Boolean { val now = clock(); val before = _screens.value; val after = before.filterValues { it.expiresAt == null || it.expiresAt > now }; if (after.size != before.size) { _screens.value = after; return true }; return false }
    /** After a user action: hide every screen that does not ask to stay. */
    fun onActionDone() { _screens.update { m -> m.filterValues { it.keepOnAction } } }
    fun clear(surface: Surface? = null) { _screens.update { if (surface == null) emptyMap() else it - surface } }
    fun setValue(bind: String, v: JsonElement) { _values.update { it + (bind to v) } }
    fun value(bind: String): JsonElement? = _values.value[bind]
    fun snapshotValues(): JsonObject = JsonObject(_values.value)
}

/** Saved screen templates (#415): briefing, status, timer, persona gallery. */
object A2uiTemplates {
    val names = listOf("briefing", "status", "timer", "persona_gallery")

    private fun text(t: String, style: String = "body") = buildJsonObject { put("type", "Text"); put("text", t); put("style", style) }

    fun build(name: String, data: JsonObject): JsonObject? = when (name) {
        "briefing" -> buildJsonObject {
            put("type", "Column"); put("children", buildJsonArray {
                add(text(data.str("title") ?: "Good day", "title"))
                (data["items"] as? JsonArray)?.forEach { add(buildJsonObject { put("type", "Card"); put("children", buildJsonArray { add(text(it.jsonPrimitive.content)) }) }) }
                add(buildJsonObject { put("type", "Button"); put("label", "Thanks"); put("action", "dismiss") })
            })
        }
        "status" -> buildJsonObject {
            put("type", "Column"); put("children", buildJsonArray {
                add(text("Status", "title"))
                add(buildJsonObject { put("type", "Row"); put("children", buildJsonArray {
                    add(buildJsonObject { put("type", "StatusGauge"); put("kind", "battery") }); add(buildJsonObject { put("type", "StatusGauge"); put("kind", "temperature") }) }) })
                add(buildJsonObject { put("type", "Row"); put("children", buildJsonArray {
                    add(buildJsonObject { put("type", "DeviceToggle"); put("device", "torch") }); add(buildJsonObject { put("type", "DeviceToggle"); put("device", "ambient") }); add(buildJsonObject { put("type", "DeviceToggle"); put("device", "dim") }) }) })
            })
        }
        "timer" -> buildJsonObject {
            put("type", "Column"); put("children", buildJsonArray {
                add(buildJsonObject { put("type", "Timer"); put("label", data.str("label") ?: "Timer"); data["ends_at_ms"]?.let { put("ends_at_ms", it) } })
                add(buildJsonObject { put("type", "Button"); put("label", "Cancel"); put("action", "tool:timer_cancel {\"name\":\"${data.str("label") ?: "timer"}\"}") })
            })
        }
        "persona_gallery" -> buildJsonObject {
            put("type", "Column"); put("children", buildJsonArray {
                add(text("Personas", "title"))
                (data["personas"] as? JsonArray)?.forEach { p -> val o = p.jsonObject; add(buildJsonObject { put("type", "Card"); put("children", buildJsonArray {
                    add(text(o.str("name") ?: o.str("id") ?: "?", "title")); add(buildJsonObject { put("type", "Button"); put("label", "Use"); put("action", "tool:persona_set {\"id\":\"${o.str("id")}\"}") }) }) }) }
            })
        }
        else -> null
    }

    private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.contentOrNull
}

/** Splits a button action: `tool:<name> {json}` runs a bridge tool directly; `dismiss` clears; anything else goes to Hermes as a structured turn. */
sealed interface ParsedAction {
    data class Tool(val name: String, val args: JsonObject) : ParsedAction
    data object Dismiss : ParsedAction
    data class ToHermes(val id: String) : ParsedAction

    companion object {
        fun parse(action: String): ParsedAction {
            val a = action.trim()
            if (a == "dismiss") return Dismiss
            if (a.startsWith("tool:")) {
                val rest = a.removePrefix("tool:").trim(); val sp = rest.indexOf(' ')
                val name = if (sp < 0) rest else rest.substring(0, sp)
                val args = if (sp < 0) JsonObject(emptyMap()) else runCatching { kotlinx.serialization.json.Json.parseToJsonElement(rest.substring(sp + 1)).jsonObject }.getOrNull()
                return if (name.isNotEmpty() && args != null) Tool(name, args) else ToHermes(a)
            }
            return ToHermes(a)
        }
    }
}
