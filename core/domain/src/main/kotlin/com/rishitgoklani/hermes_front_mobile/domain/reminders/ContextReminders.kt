package com.rishitgoklani.hermes_front_mobile.domain.reminders

import com.rishitgoklani.hermes_front_mobile.domain.SpeechSynthesizer
import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.notifications.NotificationPoster
import com.rishitgoklani.hermes_front_mobile.domain.routines.DeviceEvent
import com.rishitgoklani.hermes_front_mobile.domain.state.PeriodicTask
import com.rishitgoklani.hermes_front_mobile.domain.state.StateJson
import com.rishitgoklani.hermes_front_mobile.domain.state.StateStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

enum class ReminderCondition { PLACE_ENTER, CHARGER }
enum class ReminderState { ARMED, NAGGING, DONE, EXPIRED }

/** A reminder that waits for a place or the charger, then nags every [nagEveryMin] minutes (up to [maxNags]) until marked done. */
@Serializable data class ContextReminder(
    val id: String, val text: String, val condition: ReminderCondition, val place: String? = null,
    val nagEveryMin: Int = 10, val maxNags: Int = 12, val state: ReminderState = ReminderState.ARMED, val nags: Int = 0, val lastNagMs: Long = 0,
)

class ContextReminderBook(private val store: StateStore) {
    private val key = "context_reminders"
    fun all(): List<ContextReminder> = runCatching { StateJson.decodeFromString(ListSerializer(ContextReminder.serializer()), store.load(key) ?: "[]") }.getOrDefault(emptyList())
    fun save(list: List<ContextReminder>) = store.save(key, StateJson.encodeToString(ListSerializer(ContextReminder.serializer()), list))
    fun upsert(r: ContextReminder) = save(all().filterNot { it.id == r.id } + r)
    fun remove(id: String): Boolean { val b = all(); val a = b.filterNot { it.id == id }; save(a); return a.size != b.size }
    /** Marks done by id, or by case-insensitive text substring when no id matches. Returns the number marked. */
    fun done(idOrText: String): Int {
        val l = all(); val hit = l.filter { it.state != ReminderState.DONE && it.state != ReminderState.EXPIRED && (it.id == idOrText || it.text.contains(idOrText, true)) }
        if (hit.isEmpty()) return 0
        val ids = hit.map { it.id }.toSet(); save(l.map { if (it.id in ids) it.copy(state = ReminderState.DONE) else it }); return hit.size
    }
}

/** Observes device events to arm-to-nag transitions and is ticked to repeat nags. One engine, registered as an event observer and a [PeriodicTask]. */
class ContextReminderEngine(private val book: ContextReminderBook, private val poster: NotificationPoster, private val speaker: SpeechSynthesizer, private val clock: () -> Long = System::currentTimeMillis) : PeriodicTask {
    override val name = "context_reminders"

    fun onEvent(e: DeviceEvent) {
        val now = clock()
        val all = book.all(); var changed = false
        val next = all.map { r ->
            if (r.state == ReminderState.ARMED && fires(r, e)) { changed = true; nag(r); r.copy(state = ReminderState.NAGGING, nags = 1, lastNagMs = now) } else r
        }
        if (changed) book.save(next)
    }

    override suspend fun tick(nowMs: Long) {
        val all = book.all(); var changed = false
        val next = all.map { r ->
            if (r.state != ReminderState.NAGGING || nowMs - r.lastNagMs < r.nagEveryMin * 60_000L) r
            else if (r.nags >= r.maxNags) { changed = true; r.copy(state = ReminderState.EXPIRED) }
            else { changed = true; nag(r); r.copy(nags = r.nags + 1, lastNagMs = nowMs) }
        }
        if (changed) book.save(next)
    }

    private fun fires(r: ContextReminder, e: DeviceEvent) = when (r.condition) {
        ReminderCondition.PLACE_ENTER -> e is DeviceEvent.Place && e.entered && (r.place == null || e.name.contains(r.place, true))
        ReminderCondition.CHARGER -> e is DeviceEvent.ChargerConnected
    }
    private fun nag(r: ContextReminder) { poster.post("Reminder", r.text); speaker.enqueue("Reminder: ${r.text}") }
}

fun contextReminderCapability(book: ContextReminderBook) = Capability("reminder_when",
    "Reminders that wait for a condition and nag until done: action=add (text, when=place|charger, place name for when=place, nag_every_min default 10) | list | done (id or text) | remove (id). Timed reminders use Hermes cron instead.",
    """{"type":"object","properties":{"action":{"type":"string","enum":["add","list","done","remove"]},"text":{"type":"string"},"when":{"type":"string","enum":["place","charger"]},"place":{"type":"string"},"nag_every_min":{"type":"integer"},"id":{"type":"string"}},"required":["action"]}""") { a: JsonObject ->
    fun s(k: String) = a[k]?.jsonPrimitive?.contentOrNull
    when (s("action")) {
        "add" -> {
            val text = s("text")?.trim().orEmpty(); if (text.isEmpty()) return@Capability ActionResult.failed("text required")
            val cond = when (s("when")) { "place" -> ReminderCondition.PLACE_ENTER; "charger" -> ReminderCondition.CHARGER; else -> return@Capability ActionResult.failed("when must be place or charger") }
            val place = s("place")?.trim()?.ifEmpty { null }
            if (cond == ReminderCondition.PLACE_ENTER && place == null) return@Capability ActionResult.failed("place required when when=place")
            val nag = s("nag_every_min")?.toIntOrNull() ?: 10; if (nag !in 1..240) return@Capability ActionResult.failed("nag_every_min must be 1-240")
            val id = s("id")?.trim()?.ifEmpty { null } ?: (text.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(30).ifEmpty { "reminder" } + "-" + (book.all().size + 1))
            book.upsert(ContextReminder(id, text.take(200), cond, place, nag))
            ActionResult.completed("I'll remind you ${if (cond == ReminderCondition.CHARGER) "when you plug in" else "when you reach $place"}: $text")
        }
        "list" -> book.all().let { l -> ActionResult.completed(if (l.isEmpty()) "No reminders" else l.joinToString("\n") { "${it.id} [${it.state.name.lowercase()}] ${it.text}" }) }
        "done" -> { val q = s("id") ?: s("text") ?: return@Capability ActionResult.failed("id or text required"); val n = book.done(q); if (n > 0) ActionResult.completed("Done: $n") else ActionResult.failed("No active reminder matches") }
        "remove" -> if (book.remove(s("id").orEmpty())) ActionResult.completed("Removed") else ActionResult.failed("No such reminder")
        else -> ActionResult.failed("action must be add, list, done or remove")
    }
}
