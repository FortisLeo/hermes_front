package com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities

import com.rishitgoklani.hermes_front_mobile.domain.SpeechSynthesizer
import com.rishitgoklani.hermes_front_mobile.domain.a2ui.A2uiCatalog
import com.rishitgoklani.hermes_front_mobile.domain.a2ui.A2uiStore
import com.rishitgoklani.hermes_front_mobile.domain.a2ui.A2uiTemplates
import com.rishitgoklani.hermes_front_mobile.domain.a2ui.A2uiValidator
import com.rishitgoklani.hermes_front_mobile.domain.a2ui.Surface
import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private fun JsonObject.s(k: String) = this[k]?.jsonPrimitive?.contentOrNull

/** [onShown] brings the app to the front / switches to the screens tab. [speaker] speaks the optional spoken line (voice first). */
fun a2uiCapabilities(store: A2uiStore, speaker: SpeechSynthesizer, onShown: (Surface) -> Unit): List<Capability> = listOf(
    Capability("ui_show", "Show an interactive native screen. Give `screen` (component tree, see generative-ui skill) or `template` (${A2uiTemplates.names.joinToString()}) with `data`. surface=desk|overlay|full. Always include `speak`. Components: ${A2uiCatalog.components.keys.joinToString()}.",
        """{"type":"object","properties":{"surface":{"type":"string","enum":["desk","overlay","full"]},"screen":{"type":"object"},"template":{"type":"string"},"data":{"type":"object"},"speak":{"type":"string"},"ttl_s":{"type":"integer","description":"auto-hide after N seconds (default 90, 0 = never)"},"keep":{"type":"boolean","description":"keep showing after a button action (default false: hides)"}}}""") { a ->
        val surface = when (a.s("surface")?.lowercase()) { null, "desk" -> Surface.Desk; "overlay" -> Surface.Overlay; "full" -> Surface.Full; else -> return@Capability ActionResult.failed("surface must be desk, overlay or full") }
        val root: JsonObject = (a["screen"] as? JsonObject)
            ?: a.s("template")?.let { t -> A2uiTemplates.build(t, (a["data"] as? JsonObject) ?: JsonObject(emptyMap())) ?: return@Capability ActionResult.failed("unknown template '$t' (valid: ${A2uiTemplates.names.joinToString()})") }
            ?: return@Capability ActionResult.failed("give screen or template")
        val problems = A2uiValidator.validate(root)
        if (problems.isNotEmpty()) return@Capability ActionResult.failed("invalid screen: " + problems.take(5).joinToString("; "))
        val ttl = (a["ttl_s"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 90L).coerceIn(0L, 3600L) * 1000
        store.show(surface, root, a.s("speak").orEmpty(), ttl, a["keep"]?.jsonPrimitive?.contentOrNull == "true"); a.s("speak")?.takeIf { it.isNotBlank() }?.let { speaker.enqueue(it) }
        onShown(surface); ActionResult.completed("Showing on ${surface.name.lowercase()}")
    },
    Capability("ui_clear", "Remove the screen from a surface (or all when surface is omitted).", """{"type":"object","properties":{"surface":{"type":"string"}}}""") { a ->
        val s = a.s("surface")?.let { n -> Surface.entries.firstOrNull { it.name.equals(n, true) } }; store.clear(s); ActionResult.completed("Cleared")
    },
)
