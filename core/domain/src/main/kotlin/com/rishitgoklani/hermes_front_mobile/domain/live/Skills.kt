package com.rishitgoklani.hermes_front_mobile.domain.live

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** One Hermes skill: a guide for a whole area (screen, apps, battery...). [id] is its folder, [name] the name in its header. */
data class SkillInfo(val id: String, val name: String, val description: String)

/** Port: the skill guides written for Hermes, bundled with the app so the live model can read the same recipes. */
interface SkillSource {
    fun list(): List<SkillInfo>
    /** The full guide, or null when there is no such skill. */
    fun read(id: String): String?
}

/** Reads the small header at the top of a SKILL.md (`name:` and `description:`). */
object SkillParser {
    fun parse(id: String, text: String): SkillInfo {
        val header = if (text.startsWith("---")) text.lineSequence().drop(1).takeWhile { it.trim() != "---" }.toList() else emptyList()
        fun field(key: String) = header.firstOrNull { it.startsWith("$key:") }?.substringAfter(":")?.trim()?.removeSurrounding("\"")?.removeSurrounding("'").orEmpty()
        return SkillInfo(id, field("name").ifBlank { id }, field("description"))
    }
}

/** Tool behind `read_skill`: returns a skill guide by folder id or by name, tolerant of case and of the `deskbot-` prefix. */
class ReadSkill(private val source: SkillSource, private val maxChars: Int = MAX_CHARS) {
    operator fun invoke(args: JsonObject): ActionResult {
        val wanted = (args["name"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty().lowercase()
        val all = source.list()
        val skill = all.firstOrNull { it.id.lowercase() == wanted || it.name.lowercase() == wanted || it.name.lowercase().removePrefix("deskbot-") == wanted.removePrefix("deskbot-") }
            ?: return ActionResult.failed(if (wanted.isEmpty()) "Give the skill name. Available: ${all.joinToString { it.id }}" else "No skill '$wanted'. Available: ${all.joinToString { it.id }}")
        val text = source.read(skill.id) ?: return ActionResult.failed("Skill ${skill.id} could not be read")
        val body = if (text.length <= maxChars) text else text.take(maxChars) + "\n… [skill truncated]"
        return ActionResult.completed(body)
    }
    companion object { const val MAX_CHARS = 9_000 }
}

/** The `read_skill` tool as the live conversation sees it. */
object LiveSkillTool {
    const val NAME = "read_skill"
    fun create(source: SkillSource): LocalTool {
        val reader = ReadSkill(source)
        return LocalTool(
            LiveToolDecl(NAME, "Read a skill guide: step-by-step recipes for one area of the phone. Read it before a multi-step task in that area.",
                """{"type":"object","properties":{"name":{"type":"string","description":"The skill, e.g. screen, apps, battery-thermal"}},"required":["name"]}"""),
            maxResultChars = ReadSkill.MAX_CHARS + 100,
        ) { reader(it) }
    }
}
