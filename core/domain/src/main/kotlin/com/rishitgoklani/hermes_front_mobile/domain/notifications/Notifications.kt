package com.rishitgoklani.hermes_front_mobile.domain.notifications

data class NotificationRecord(
    val key: String, val pkg: String, val app: String, val title: String, val text: String,
    val time: Long, val actions: List<String> = emptyList(), val ongoing: Boolean = false, val canReply: Boolean = false,
)

/** Port over the notification listener service. */
interface NotificationSource {
    suspend fun active(): List<NotificationRecord>
    suspend fun dismiss(key: String): Boolean
    suspend fun dismissAll(): Boolean
    /** Fire a notification action by title; [reply] is used for inline-reply actions. */
    suspend fun act(key: String, actionTitle: String, reply: String? = null): Boolean
}

/** Port for posting our own notifications (reminders, alerts, status). */
interface NotificationPoster {
    fun post(title: String, text: String, actions: List<String> = emptyList()): Int
}

/** Bounded in-memory history, newest last; searchable and summarised for digests. */
class NotificationHistory(private val capacity: Int = 500) {
    private val items = ArrayDeque<NotificationRecord>()

    @Synchronized fun add(r: NotificationRecord) {
        if (r.ongoing) return
        items.removeAll { it.key == r.key && it.time == r.time }
        items.addLast(r)
        while (items.size > capacity) items.removeFirst()
    }

    @Synchronized fun search(query: String? = null, app: String? = null, sinceMs: Long = 0, limit: Int = 50): List<NotificationRecord> =
        items.filter { it.time >= sinceMs }
            .filter { app == null || it.app.contains(app, true) || it.pkg.contains(app, true) }
            .filter { query == null || it.title.contains(query, true) || it.text.contains(query, true) }
            .takeLast(limit).reversed()

    @Synchronized fun digest(sinceMs: Long): Map<String, Int> =
        items.filter { it.time >= sinceMs }.groupingBy { it.app }.eachCount().toList().sortedByDescending { it.second }.toMap()

    @Synchronized fun size() = items.size
}

/** Finds verification codes in notification text (4-8 digits near a keyword, or a lone 6-digit group). */
object OtpExtractor {
    private val keyword = Regex("""(?i)(code|otp|verification|passcode|pin|password)""")
    private val digits = Regex("""(?<![\d])(\d{4,8})(?![\d])""")
    private val spaced = Regex("""(?<![\d])(\d{3})[ -](\d{3})(?![\d])""")

    fun extract(text: String): String? {
        spaced.find(text)?.let { if (keyword.containsMatchIn(text)) return it.groupValues[1] + it.groupValues[2] }
        val m = digits.findAll(text).toList()
        if (m.isEmpty()) return null
        if (keyword.containsMatchIn(text)) return m.first().value
        return m.singleOrNull { it.value.length == 6 }?.value
    }
}
