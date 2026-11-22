package com.rishitgoklani.hermes_front_mobile.domain.face

enum class Mood { Neutral, Happy, Sad, Angry, Surprised, Worried, Scared, Confused, Bored, Sleepy, Shy, Proud, Love, Focused, Disgusted }

enum class Reaction { Cry, Laugh, Giggle, Wink, Nod, ShakeNo, Blink, EyeRoll, Startle, Sigh, Yawn, Celebrate, Dizzy, HeartEyes, StarEyes, MindBlown, Glance, LookAround }

/** Overlays drawn by the engine on top of any face. */
enum class Effect { Tears, Blush, Hearts, Sparkles, Zzz, QuestionMark, AngerMark, Spirals, SweatDrop }

sealed interface Expr {
    data class MoodExpr(val mood: Mood, val intensity: Float) : Expr
    data class ReactionExpr(val reaction: Reaction, val intensity: Float) : Expr
}

object ExpressionNames {
    private fun norm(s: String) = s.trim().lowercase().replace(Regex("[\\s_-]+"), "")
    private val moods = Mood.entries.associateBy { norm(it.name) }
    private val reactions = Reaction.entries.associateBy { norm(it.name) } + mapOf("look" to Reaction.LookAround, "lookaround" to Reaction.LookAround, "glanceatuser" to Reaction.Glance, "shakeno" to Reaction.ShakeNo)

    fun parse(name: String, intensity: Float = 1f): Expr? {
        val k = norm(name); val i = intensity.coerceIn(0f, 1f)
        moods[k]?.let { return Expr.MoodExpr(it, i) }
        reactions[k]?.let { return Expr.ReactionExpr(it, i) }
        return null
    }

    val allMoodNames get() = Mood.entries.map { it.name.lowercase() }
    val allReactionNames get() = Reaction.entries.map { it.name.lowercase() }
}

/** Result of stripping `<expr NAME 0.8/>` tags from a reply: speakable text plus the tags with their sentence offsets. */
data class TaggedText(val text: String, val tags: List<Pair<Int, Expr>>)

object ExpressionTags {
    private val tag = Regex("""<expr\s+([a-zA-Z_ -]+?)(?:\s+([0-9]*\.?[0-9]+))?\s*/>""")

    fun parse(raw: String): TaggedText {
        val tags = mutableListOf<Pair<Int, Expr>>()
        val out = StringBuilder(); var last = 0
        for (m in tag.findAll(raw)) {
            out.append(raw, last, m.range.first); last = m.range.last + 1
            ExpressionNames.parse(m.groupValues[1], m.groupValues[2].toFloatOrNull() ?: 1f)?.let { tags += out.length to it }
        }
        out.append(raw, last, raw.length)
        return TaggedText(out.toString().replace(Regex("""[ \t]{2,}"""), " ").replace(Regex("""\s+([.,!?])"""), "$1").trim(), tags)
    }
}

/** Fallback chains: what an unsupported/shared reaction looks like in terms of a mood plus engine effects. */
object ExpressionFallbacks {
    data class Look(val mood: Mood, val effects: Set<Effect> = emptySet(), val bounce: Boolean = false, val closedArcs: Boolean = false)

    fun forReaction(r: Reaction): Look = when (r) {
        Reaction.Cry -> Look(Mood.Sad, setOf(Effect.Tears))
        Reaction.Laugh -> Look(Mood.Happy, bounce = true, closedArcs = true)
        Reaction.Giggle -> Look(Mood.Happy, setOf(Effect.Blush), bounce = true)
        Reaction.Wink -> Look(Mood.Happy)
        Reaction.Nod, Reaction.ShakeNo, Reaction.Blink, Reaction.Glance, Reaction.LookAround -> Look(Mood.Neutral)
        Reaction.EyeRoll -> Look(Mood.Bored)
        Reaction.Startle -> Look(Mood.Surprised, setOf(Effect.SweatDrop))
        Reaction.Sigh -> Look(Mood.Sad)
        Reaction.Yawn -> Look(Mood.Sleepy, setOf(Effect.Zzz))
        Reaction.Celebrate -> Look(Mood.Happy, setOf(Effect.Sparkles), bounce = true)
        Reaction.Dizzy -> Look(Mood.Neutral, setOf(Effect.Spirals))
        Reaction.HeartEyes -> Look(Mood.Love, setOf(Effect.Hearts))
        Reaction.StarEyes -> Look(Mood.Happy, setOf(Effect.Sparkles))
        Reaction.MindBlown -> Look(Mood.Surprised, setOf(Effect.Sparkles))
    }

    fun forMood(m: Mood): Set<Effect> = when (m) {
        Mood.Love -> setOf(Effect.Hearts); Mood.Shy -> setOf(Effect.Blush); Mood.Confused -> setOf(Effect.QuestionMark)
        Mood.Angry -> setOf(Effect.AngerMark); Mood.Sleepy -> setOf(Effect.Zzz); Mood.Worried, Mood.Scared -> setOf(Effect.SweatDrop)
        else -> emptySet()
    }
}
