package com.rishitgoklani.hermes_front_mobile.domain.persona

import com.rishitgoklani.hermes_front_mobile.domain.face.EyeTweaks
import com.rishitgoklani.hermes_front_mobile.domain.face.Mood
import kotlinx.serialization.Serializable

@Serializable
data class PersonaLicense(val source: String = "", val author: String = "", val license: String = "", val allowedUse: String = "", val ripped: Boolean = false)

@Serializable
data class PersonaTheme(val primaryHex: String = "#7FDBFF", val backgroundHex: String = "#05070A", val onBackgroundHex: String = "#FFFFFF")

@Serializable
data class Persona(
    val id: String, val name: String, val description: String = "",
    val eyeColorHex: String = "#7FDBFF", val eyeSize: Float = 1f, val blinkRateSec: Float = 4f, val pupilScale: Float? = null, val roundness: Float = 0.4f,
    val defaultMood: String = "neutral", val voiceRate: Float = 1f, val voicePitch: Float = 1f, val tone: String = "",
    val theme: PersonaTheme = PersonaTheme(), val license: PersonaLicense = PersonaLicense(license = "Original / MIT", allowedUse = "personal"),
    val builtin: Boolean = false,
) {
    fun tweaks() = EyeTweaks(colorHex = PersonaValidator.parseColor(eyeColorHex), sizeScale = eyeSize, blinkRateSec = blinkRateSec, pupilScale = pupilScale, roundness = roundness)
    fun mood(): Mood = Mood.entries.firstOrNull { it.name.equals(defaultMood, true) } ?: Mood.Neutral
}

/** Port: persistence of persona packs. */
interface PersonaRepository {
    suspend fun all(): List<Persona>
    suspend fun active(): Persona
    suspend fun setActive(id: String)
    suspend fun previousId(): String?
    suspend fun save(persona: Persona)
    suspend fun delete(id: String): Boolean
}

object PersonaValidator {
    private val idRegex = Regex("^[a-z0-9][a-z0-9-]{1,40}$")
    fun parseColor(hex: String): Long? = Regex("^#?([0-9a-fA-F]{6})$").matchEntire(hex.trim())?.groupValues?.get(1)?.toLong(16)?.let { 0xFF000000 or it }

    /** Returns a refusal reason, or null if the pack may be installed. Enforces license tracking (#428). */
    fun check(p: Persona): String? {
        if (!idRegex.matches(p.id)) return "id must be lowercase letters, digits, dashes (2-41 chars)"
        if (p.name.isBlank()) return "name required"
        if (p.license.ripped) return "ripped/unlicensed assets are refused"
        if (p.license.license.isBlank() || p.license.allowedUse.isBlank()) return "license record (license + allowedUse) required"
        if (p.license.license.contains("unknown", true) || p.license.license.contains("all rights reserved", true)) return "license does not allow use"
        if (parseColor(p.eyeColorHex) == null) return "eyeColorHex must be #RRGGBB"
        if (parseColor(p.theme.primaryHex) == null || parseColor(p.theme.backgroundHex) == null) return "theme colors must be #RRGGBB"
        if (p.eyeSize !in 0.5f..1.6f) return "eyeSize must be 0.5-1.6"
        if (p.blinkRateSec !in 1f..15f) return "blinkRateSec must be 1-15"
        if (p.voiceRate !in 0.5f..2f || p.voicePitch !in 0.5f..2f) return "voice rate/pitch must be 0.5-2"
        return null
    }
}

object BuiltinPersonas {
    val all = listOf(
        Persona("default", "Default eyes", "Cyan parametric eyes", builtin = true),
        Persona("sleepy-green", "Sleepy Green", "Calm, slow and green", eyeColorHex = "#4CAF50", blinkRateSec = 6f, defaultMood = "sleepy", voiceRate = 0.9f, voicePitch = 0.9f,
            tone = "Speak softly and slowly.", theme = PersonaTheme("#4CAF50", "#04100A"), builtin = true),
        Persona("cheerful-orange", "Cheerful Orange", "Bright and bouncy", eyeColorHex = "#FFA726", eyeSize = 1.1f, blinkRateSec = 3f, defaultMood = "happy", voiceRate = 1.1f, voicePitch = 1.1f,
            tone = "Upbeat, short, friendly.", theme = PersonaTheme("#FFA726", "#120A02"), builtin = true),
        Persona("night-owl", "Night Owl", "Dim purple for the evening", eyeColorHex = "#B388FF", eyeSize = 0.9f, blinkRateSec = 5f, defaultMood = "focused", voiceRate = 0.95f,
            tone = "Quiet and concise.", theme = PersonaTheme("#B388FF", "#07040F"), builtin = true),
    )
}
