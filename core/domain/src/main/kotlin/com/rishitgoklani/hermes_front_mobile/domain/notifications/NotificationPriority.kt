package com.rishitgoklani.hermes_front_mobile.domain.notifications

import com.rishitgoklani.hermes_front_mobile.domain.state.StateJson
import com.rishitgoklani.hermes_front_mobile.domain.state.StateStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** Silence one app (substring of app label or package), optionally only notifications containing [contains], until [untilMs] (0 = until removed). */
@Serializable data class SilenceRule(val app: String, val contains: String? = null, val untilMs: Long = 0)

/** Persistent per-app silence rules (#104). Expired rules are dropped on read. */
class SilenceRules(private val store: StateStore, private val clock: () -> Long = System::currentTimeMillis) {
    private val key = "notification_silence"

    fun list(): List<SilenceRule> {
        val all = runCatching { StateJson.decodeFromString(ListSerializer(SilenceRule.serializer()), store.load(key) ?: "[]") }.getOrDefault(emptyList())
        val live = all.filter { it.untilMs == 0L || it.untilMs > clock() }
        if (live.size != all.size) save(live)
        return live
    }

    /** Replaces an existing rule for the same app+contains. */
    fun add(rule: SilenceRule) = save(list().filterNot { same(it, rule) } + rule)
    fun remove(app: String, contains: String? = null): Boolean {
        val before = list(); val after = before.filterNot { it.app.equals(app, true) && (contains == null || it.contains.equals(contains, true)) }
        save(after); return after.size != before.size
    }
    fun isSilenced(r: NotificationRecord): Boolean = list().any { matches(it, r) }

    private fun same(a: SilenceRule, b: SilenceRule) = a.app.equals(b.app, true) && a.contains.equals(b.contains, true)
    private fun matches(rule: SilenceRule, r: NotificationRecord) =
        (r.app.contains(rule.app, true) || r.pkg.contains(rule.app, true)) && (rule.contains == null || "${r.title} ${r.text}".contains(rule.contains, true))
    private fun save(l: List<SilenceRule>) = store.save(key, StateJson.encodeToString(ListSerializer(SilenceRule.serializer()), l))
}

enum class Importance { HIGH, NORMAL, LOW }
data class RankedNotification(val record: NotificationRecord, val score: Int, val importance: Importance, val reason: String)

/** Deterministic ranking: same input, same order. Higher score = more important; ties break newest first, then key. */
object NotificationRanker {
    private val urgent = Regex("""(?i)\b(urgent|asap|emergency|important|security alert|suspicious|unauthori[sz]ed|failed|payment due|fraud|verification|otp|one-time|code is|missed call|incoming call|arriving|out for delivery|delivered)\b""")
    private val promo = Regex("""(?i)(\b\d{1,2}% off\b|\bsale\b|\boffer\b|discount|\bdeal\b|coupon|cashback|promo|subscribe|newsletter|recommended for you|you might like)""")
    private val people = listOf("whatsapp", "telegram", "signal", "messaging", "mms", "sms", "dialer", "phone", "incallui", "messenger", "mail", "gm")
    private val noisy = listOf("game", "shop", "amazon", "flipkart", "myntra", "swiggy", "zomato", "news", "facebook", "instagram", "snapchat", "tiktok", "youtube")

    fun score(r: NotificationRecord, nowMs: Long): Pair<Int, String> {
        var s = 30; val why = mutableListOf<String>()
        val text = "${r.title} ${r.text}"; val pkg = r.pkg.lowercase()
        if (urgent.containsMatchIn(text)) { s += 40; why += "urgent wording" }
        if (people.any { pkg.contains(it) }) { s += 20; why += "from a person or call" }
        if (r.canReply) { s += 8; why += "can reply" }
        if (promo.containsMatchIn(text)) { s -= 25; why += "promotional" }
        if (noisy.any { pkg.contains(it) }) { s -= 10; why += "noisy app" }
        val ageMin = ((nowMs - r.time) / 60_000).coerceAtLeast(0)
        if (ageMin <= 10) { s += 10; why += "recent" } else if (ageMin > 24 * 60) { s -= 10; why += "old" }
        return s.coerceIn(0, 100) to (why.joinToString(", ").ifEmpty { "normal" })
    }

    fun importance(score: Int) = when { score >= 70 -> Importance.HIGH; score < 30 -> Importance.LOW; else -> Importance.NORMAL }

    fun rank(list: List<NotificationRecord>, nowMs: Long): List<RankedNotification> =
        list.map { r -> val (s, why) = score(r, nowMs); RankedNotification(r, s, importance(s), why) }
            .sortedWith(compareByDescending<RankedNotification> { it.score }.thenByDescending { it.record.time }.thenBy { it.record.key })
}

/** Use case: rank what is on screen, honour silence rules, optionally dismiss the silenced ones. */
class RankNotifications(private val source: NotificationSource, private val rules: SilenceRules, private val clock: () -> Long = System::currentTimeMillis) {
    data class Result(val ranked: List<RankedNotification>, val silenced: List<NotificationRecord>, val dismissed: Int)

    suspend operator fun invoke(limit: Int = 10, dismissSilenced: Boolean = false): Result {
        val (quiet, loud) = source.active().filter { !it.ongoing }.partition { rules.isSilenced(it) }
        val dismissed = if (dismissSilenced) quiet.count { source.dismiss(it.key) } else 0
        return Result(NotificationRanker.rank(loud, clock()).take(limit.coerceIn(1, 50)), quiet, dismissed)
    }
}
