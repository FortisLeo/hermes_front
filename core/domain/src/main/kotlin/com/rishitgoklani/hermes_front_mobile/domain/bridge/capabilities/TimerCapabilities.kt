package com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.system.TimerBook
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Schedules [action] after [delayMs]; returns a cancel handle. Injected so tests control time. */
fun interface TimerScheduler { fun schedule(delayMs: Long, action: () -> Unit): () -> Unit }

private fun JsonObject.s(k: String) = this[k]?.jsonPrimitive?.contentOrNull

fun timerCapabilities(book: TimerBook, scheduler: TimerScheduler, onFire: (name: String) -> Unit): List<Capability> {
    val handles = HashMap<String, () -> Unit>()
    return listOf(
        Capability("timer_start", "Start a named timer. duration accepts '90s', '5 min', '1h 30m', '1:30:00'. Speaks and notifies when done.",
            """{"type":"object","properties":{"name":{"type":"string"},"duration":{"type":"string"}},"required":["duration"]}""") { a ->
            val ms = TimerBook.parseDuration(a.s("duration").orEmpty()) ?: return@Capability ActionResult.failed("Could not parse duration")
            val name = a.s("name")?.ifBlank { null } ?: "timer"
            val entry = try { book.start(name, ms) } catch (e: IllegalArgumentException) { return@Capability ActionResult.failed(e.message ?: "bad duration") }
            handles.remove(name.lowercase())?.invoke()
            handles[name.lowercase()] = scheduler.schedule(ms) { handles.remove(name.lowercase()); book.expire(name); onFire(name) }
            ActionResult.completed("${entry.name}: ${TimerBook.format(ms)}")
        },
        Capability("timer_list", "List running timers with time remaining.") {
            val l = book.list()
            ActionResult.completed(if (l.isEmpty()) "No timers" else l.joinToString("\n") { "${it.first.name}: ${TimerBook.format(it.second)} left" },
                buildJsonObject { put("timers", buildJsonArray { l.forEach { add(buildJsonObject { put("name", it.first.name); put("remaining_ms", it.second) }) } }) })
        },
        Capability("timer_cancel", "Cancel a running timer by name.", """{"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}""") { a ->
            val n = a.s("name").orEmpty()
            handles.remove(n.lowercase())?.invoke()
            if (book.cancel(n)) ActionResult.completed("Cancelled $n") else ActionResult.failed("No timer named $n")
        },
        Capability("stopwatch", "Start or stop a named stopwatch (action=start|stop).",
            """{"type":"object","properties":{"name":{"type":"string"},"action":{"type":"string","enum":["start","stop"]}},"required":["action"]}""") { a ->
            val n = a.s("name")?.ifBlank { null } ?: "stopwatch"
            when (a.s("action")) {
                "start" -> { book.stopwatchStart(n); ActionResult.completed("Started $n") }
                "stop" -> book.stopwatchStop(n)?.let { ActionResult.completed("$n: ${TimerBook.format(it)}") } ?: ActionResult.failed("$n is not running")
                else -> ActionResult.failed("action must be start or stop")
            }
        },
    )
}
