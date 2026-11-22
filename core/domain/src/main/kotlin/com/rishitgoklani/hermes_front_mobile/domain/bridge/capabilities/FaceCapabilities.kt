package com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.face.Expr
import com.rishitgoklani.hermes_front_mobile.domain.face.ExpressionNames
import com.rishitgoklani.hermes_front_mobile.domain.face.FaceController
import com.rishitgoklani.hermes_front_mobile.domain.persona.Persona
import com.rishitgoklani.hermes_front_mobile.domain.persona.PersonaRepository
import com.rishitgoklani.hermes_front_mobile.domain.persona.PersonaValidator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private fun JsonObject.s(k: String) = this[k]?.jsonPrimitive?.contentOrNull
private fun JsonObject.f(k: String) = this[k]?.jsonPrimitive?.floatOrNull

/** [onPersonaChanged] applies a persona to face/theme/voice (app wiring); [reset] restores default tweaks. */
fun faceCapabilities(face: FaceController, personas: PersonaRepository, onPersonaChanged: suspend (Persona) -> Unit): List<Capability> = listOf(
    Capability("face_set", "Set the bot's face: mood (persists) and/or reaction (1-3 s), intensity 0-1, gaze look_x/look_y -1..1 (look_x=null resets).",
        """{"type":"object","properties":{"mood":{"type":"string"},"reaction":{"type":"string"},"intensity":{"type":"number"},"look_x":{"type":"number"},"look_y":{"type":"number"},"duration_ms":{"type":"integer","description":"reaction length, default 2000"}}}""") { a ->
        val i = a.f("intensity") ?: 1f; val applied = mutableListOf<String>()
        a.s("mood")?.let { n -> (ExpressionNames.parse(n, i) as? Expr.MoodExpr)?.let { face.apply(it); applied += "mood ${n}" } ?: return@Capability ActionResult.failed("unknown mood '$n' (moods: ${ExpressionNames.allMoodNames.joinToString()})") }
        a.s("reaction")?.let { n -> (ExpressionNames.parse(n, i) as? Expr.ReactionExpr)?.let { face.react(it.reaction, it.intensity, (a.f("duration_ms")?.toLong() ?: 2000L).coerceIn(300L, 15_000L)); applied += "reaction ${n}" } ?: return@Capability ActionResult.failed("unknown reaction '$n' (reactions: ${ExpressionNames.allReactionNames.joinToString()})") }
        if (a.containsKey("look_x") || a.containsKey("look_y")) { face.look(a.f("look_x"), a.f("look_y")); applied += "look" }
        if (applied.isEmpty()) ActionResult.failed("give mood, reaction or look_x/look_y") else ActionResult.completed(applied.joinToString(", "))
    },
    Capability("face_tweak", "Adjust the default eyes: color (#RRGGBB), size (0.5-1.6), openness (0.1-1), blink_rate_s (1-15), pupil (0.3-1.5), roundness (0-0.5); reset=true restores defaults.",
        """{"type":"object","properties":{"color":{"type":"string"},"size":{"type":"number"},"openness":{"type":"number"},"blink_rate_s":{"type":"number"},"pupil":{"type":"number"},"roundness":{"type":"number"},"reset":{"type":"boolean"}}}""") { a ->
        if (a["reset"]?.jsonPrimitive?.booleanOrNull == true) { face.resetTweaks(); return@Capability ActionResult.completed("Face reset") }
        val color = a.s("color")?.let { PersonaValidator.parseColor(it) ?: return@Capability ActionResult.failed("color must be #RRGGBB") }
        val size = a.f("size")?.also { if (it !in 0.5f..1.6f) return@Capability ActionResult.failed("size must be 0.5-1.6") }
        val blink = a.f("blink_rate_s")?.also { if (it !in 1f..15f) return@Capability ActionResult.failed("blink_rate_s must be 1-15") }
        face.tweak { t -> t.copy(colorHex = color ?: t.colorHex, sizeScale = size ?: t.sizeScale, openness = a.f("openness")?.coerceIn(0.1f, 1f) ?: t.openness,
            blinkRateSec = blink ?: t.blinkRateSec, pupilScale = a.f("pupil")?.coerceIn(0.3f, 1.5f) ?: t.pupilScale, roundness = a.f("roundness")?.coerceIn(0f, 0.5f) ?: t.roundness) }
        ActionResult.completed("Face updated")
    },
    Capability("persona_list", "List installed persona packs and which is active.") {
        val all = personas.all(); val active = personas.active().id
        ActionResult.completed(all.joinToString("\n") { (if (it.id == active) "* " else "  ") + "${it.id}: ${it.name}" + (if (it.description.isNotBlank()) " - ${it.description}" else "") },
            buildJsonObject { put("active", active); put("count", all.size) })
    },
    Capability("persona_set", "Switch to a persona pack by id (face, theme, voice, tone change together). id='previous' switches back.",
        """{"type":"object","properties":{"id":{"type":"string"}},"required":["id"]}""") { a ->
        var id = a.s("id").orEmpty()
        if (id == "previous") id = personas.previousId() ?: return@Capability ActionResult.failed("No previous persona")
        val p = personas.all().firstOrNull { it.id == id } ?: return@Capability ActionResult.failed("No persona '$id'")
        personas.setActive(p.id); onPersonaChanged(p)
        ActionResult.completed("Persona ${p.name}", buildJsonObject { put("tone", p.tone) })
    },
    Capability("persona_save", "Install or update a persona pack from JSON (id, name, eyeColorHex, theme, voice, license{source,author,license,allowedUse}). Packs without a valid license record or marked ripped are refused.",
        """{"type":"object","properties":{"persona":{"type":"object"}},"required":["persona"]}""") { a ->
        val p = runCatching { Json { ignoreUnknownKeys = true }.decodeFromJsonElement(Persona.serializer(), a["persona"] ?: return@Capability ActionResult.failed("persona required")).copy(builtin = false) }
            .getOrElse { return@Capability ActionResult.failed("invalid persona JSON: ${it.message?.take(80)}") }
        PersonaValidator.check(p)?.let { return@Capability ActionResult(ActionResult.DENIED, it) }
        personas.save(p); ActionResult.completed("Saved ${p.id}")
    },
    Capability("persona_delete", "Delete a user-created persona pack by id (built-ins cannot be deleted; the active pack falls back to default).",
        """{"type":"object","properties":{"id":{"type":"string"}},"required":["id"]}""") { a ->
        if (personas.delete(a.s("id").orEmpty())) ActionResult.completed("Deleted") else ActionResult.failed("Not found or built-in")
    },
)
