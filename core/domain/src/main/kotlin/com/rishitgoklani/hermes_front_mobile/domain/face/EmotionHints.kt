package com.rishitgoklani.hermes_front_mobile.domain.face

/** Cheap tone detection (#326): maps what the user said to a face reaction, so the bot reacts before/while the model answers. */
object EmotionHints {
    private val rules: List<Pair<Regex, Expr>> = listOf(
        Regex("""\b(thank you|thanks|thx|cheers)\b""", RegexOption.IGNORE_CASE) to Expr.ReactionExpr(Reaction.HeartEyes, 0.8f),
        Regex("""\b(good job|well done|great job|awesome|amazing|perfect|nice work|you're the best)\b""", RegexOption.IGNORE_CASE) to Expr.MoodExpr(Mood.Proud, 0.9f),
        Regex("""\b(i love you|love you)\b""", RegexOption.IGNORE_CASE) to Expr.MoodExpr(Mood.Love, 1f),
        Regex("""\b(that was wrong|that's wrong|that is wrong|wrong answer|not what i asked|bad bot|you messed up)\b""", RegexOption.IGNORE_CASE) to Expr.MoodExpr(Mood.Sad, 0.8f),
        Regex("""\b(sorry|my bad|apologi[sz]e)\b""", RegexOption.IGNORE_CASE) to Expr.MoodExpr(Mood.Worried, 0.5f),
        Regex("""\b(hello|hi there|good morning|hey there)\b""", RegexOption.IGNORE_CASE) to Expr.ReactionExpr(Reaction.Wink, 1f),
        Regex("""\b(tell me a joke|that's funny|haha|lol)\b""", RegexOption.IGNORE_CASE) to Expr.ReactionExpr(Reaction.Laugh, 0.8f),
        Regex("""\b(good night|going to sleep|i'm tired)\b""", RegexOption.IGNORE_CASE) to Expr.MoodExpr(Mood.Sleepy, 0.9f),
    )
    fun forUtterance(text: String): Expr? = rules.firstOrNull { it.first.containsMatchIn(text) }?.second
}

/** Greeting rules (#325): time-of-day wording, only when the user has been away long enough. */
object Greetings {
    fun greeting(hour: Int, awayMs: Long, name: String? = null): String? {
        if (awayMs < 30 * 60_000L) return null
        val who = name?.let { ", $it" }.orEmpty()
        val base = when (hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..21 -> "Good evening"; else -> "Hello" }
        return if (awayMs > 6 * 3600_000L) "$base$who. Welcome back." else "$base$who."
    }
    fun farewell(hour: Int): String = if (hour >= 21 || hour < 5) "Good night." else "See you later."
}
