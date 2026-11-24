package com.rishitgoklani.hermes_front_mobile.domain.presence

import com.rishitgoklani.hermes_front_mobile.domain.BotState
import com.rishitgoklani.hermes_front_mobile.domain.face.Expr
import com.rishitgoklani.hermes_front_mobile.domain.face.Mood
import com.rishitgoklani.hermes_front_mobile.domain.face.Reaction
import com.rishitgoklani.hermes_front_mobile.domain.routines.DeviceEvent

/** #322 Status display: the face shows what the bot is doing; a low battery colours the resting face. */
class StatusDisplay(private val lowBatteryPercent: Int = 20) : Behavior {
    override val id = "status-display"
    private var state = BotState.Idle
    private var batteryLow = false

    override suspend fun react(input: Input): List<Cue> = when (input) {
        is Input.State -> { state = input.state; listOf(Cue.Express(face())) }
        is Input.Device -> when (val e = input.event) {
            is DeviceEvent.BatteryLow -> if (e.level <= lowBatteryPercent || e.level < 0) { batteryLow = true; listOf(Cue.Express(face()), Cue.Express(Expr.ReactionExpr(Reaction.Sigh, 0.8f))) } else emptyList()
            is DeviceEvent.ChargerConnected -> if (batteryLow) { batteryLow = false; listOf(Cue.Express(face()), Cue.Express(Expr.ReactionExpr(Reaction.HeartEyes, 0.6f))) } else emptyList()
            else -> emptyList()
        }
        else -> emptyList()
    }

    /** Error beats everything, working/thinking show effort, and only a resting face is tinted by battery. */
    private fun face(): Expr = when (state) {
        BotState.Error -> Expr.MoodExpr(Mood.Sad, 1f)
        BotState.Working -> Expr.MoodExpr(Mood.Focused, 1f)
        BotState.Thinking -> Expr.MoodExpr(Mood.Focused, 0.6f)
        BotState.Listening -> Expr.MoodExpr(Mood.Surprised, 0.3f)
        BotState.Speaking -> Expr.MoodExpr(Mood.Happy, 0.6f)
        BotState.Idle -> if (batteryLow) Expr.MoodExpr(Mood.Worried, 0.7f) else Expr.MoodExpr(Mood.Neutral, 1f)
    }
}

enum class Tone { Grateful, Praise, Affection, Frustrated, Upset, Apology, Joking, Sleepy, Greeting, Farewell, Neutral }

/** Port: classify how the user sounds. The default is local and deterministic; a model-backed one could replace it. */
fun interface ToneClassifier { fun classify(text: String): Tone }

/** #326 Local tone detection: ordered keyword rules plus punctuation shape (shouting, "!!!", "?!"). No model call. */
class KeywordToneClassifier : ToneClassifier {
    private val rules: List<Pair<Tone, Regex>> = listOf(
        Tone.Affection to Regex("""\b(i love you|love you|you're (so )?(sweet|cute|the best))\b"""),
        Tone.Grateful to Regex("""\b(thank you|thanks|thx|cheers|appreciate)\b"""),
        Tone.Praise to Regex("""\b(good job|well done|great job|nice work|awesome|amazing|perfect|brilliant|nailed it)\b"""),
        Tone.Frustrated to Regex("""\b(stupid|useless|shut up|idiot|dumb|hate this|annoying|not again|you never|so slow|ridiculous|damn|wtf)\b"""),
        Tone.Upset to Regex("""\b(that was wrong|that's wrong|that is wrong|wrong answer|not what i asked|bad bot|you messed up|that's not right|doesn't work)\b"""),
        Tone.Apology to Regex("""\b(sorry|my bad|apologi[sz]e)\b"""),
        Tone.Joking to Regex("""\b((ha){2,}h?|lol|lmao|that's funny|tell me a joke)\b"""),
        Tone.Farewell to Regex("""\b(good ?night|goodbye|bye( bye)?|see you|talk later|going to (bed|sleep))\b"""),
        Tone.Sleepy to Regex("""\b(i'm tired|so tired|sleepy|exhausted)\b"""),
        Tone.Greeting to Regex("""\b(hello|hi there|good morning|hey there|good evening)\b"""),
    )

    override fun classify(text: String): Tone {
        val t = text.trim()
        if (t.isEmpty()) return Tone.Neutral
        val lower = t.lowercase()
        rules.firstOrNull { it.second.containsMatchIn(lower) }?.let { return it.first }
        val letters = t.filter { it.isLetter() }
        val shouting = letters.length >= 6 && letters.count { it.isUpperCase() } >= letters.length * 0.8
        if (shouting || Regex("""[!?]{3,}|\?!|!\?""").containsMatchIn(t)) return Tone.Frustrated
        return Tone.Neutral
    }
}

/** #326 Emotional response: reacts on the face to the user's tone, and softly owns the bot's own errors. */
class ToneResponder(private val classifier: ToneClassifier = KeywordToneClassifier()) : Behavior {
    override val id = "tone-response"

    override suspend fun react(input: Input): List<Cue> = when (input) {
        is Input.Heard -> response(classifier.classify(input.text))
        is Input.State -> if (input.state == BotState.Error) listOf(Cue.Express(Expr.MoodExpr(Mood.Sad, 0.8f))) else emptyList()
        else -> emptyList()
    }

    private fun response(tone: Tone): List<Cue> = when (tone) {
        Tone.Grateful -> listOf(Cue.Express(Expr.ReactionExpr(Reaction.HeartEyes, 0.8f)))
        Tone.Praise -> listOf(Cue.Express(Expr.MoodExpr(Mood.Proud, 0.9f)), Cue.Express(Expr.ReactionExpr(Reaction.Celebrate, 0.6f)))
        Tone.Affection -> listOf(Cue.Express(Expr.MoodExpr(Mood.Love, 1f)))
        Tone.Frustrated -> listOf(Cue.Express(Expr.MoodExpr(Mood.Worried, 0.8f)), Cue.Speak("Sorry, let me try to fix that.", Urgency.Low))
        Tone.Upset -> listOf(Cue.Express(Expr.MoodExpr(Mood.Sad, 0.8f)))
        Tone.Apology -> listOf(Cue.Express(Expr.ReactionExpr(Reaction.Nod, 0.6f)))
        Tone.Joking -> listOf(Cue.Express(Expr.ReactionExpr(Reaction.Laugh, 0.8f)))
        Tone.Sleepy -> listOf(Cue.Express(Expr.MoodExpr(Mood.Sleepy, 0.9f)))
        Tone.Greeting -> listOf(Cue.Express(Expr.ReactionExpr(Reaction.Wink, 1f)))
        Tone.Farewell, Tone.Neutral -> emptyList()
    }
}
