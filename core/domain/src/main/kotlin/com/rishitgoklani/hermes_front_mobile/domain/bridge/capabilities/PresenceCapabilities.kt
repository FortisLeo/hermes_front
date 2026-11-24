package com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities

import com.rishitgoklani.hermes_front_mobile.domain.behavior.RunCapability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.CapabilityRegistry
import com.rishitgoklani.hermes_front_mobile.domain.presence.AuditSource
import com.rishitgoklani.hermes_front_mobile.domain.presence.BehaviorHub
import com.rishitgoklani.hermes_front_mobile.domain.presence.Escalation
import com.rishitgoklani.hermes_front_mobile.domain.presence.QuietContextPort
import com.rishitgoklani.hermes_front_mobile.domain.presence.RoutineMiner
import com.rishitgoklani.hermes_front_mobile.domain.presence.TaskState
import com.rishitgoklani.hermes_front_mobile.domain.presence.TaskSupervisor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** #333 Is the bot being held quiet right now, and why? (call, Do Not Disturb, sleep, focus) */
fun quietStatusCapability(quiet: QuietContextPort, hub: BehaviorHub) = Capability("quiet_status", "Whether the bot is holding speech (in a call, Do Not Disturb, sleep hours, focus) and how many spoken notes are waiting.") {
    val c = quiet.current()
    ActionResult.completed(if (c.quiet) "Quiet: ${c.reasons().joinToString()}" else "Not quiet", buildJsonObject { put("quiet", c.quiet); put("reasons", JsonArray(c.reasons().map { JsonPrimitive(it) })); put("held", hub.heldCount) })
}

/** #336 Raise, acknowledge or list escalating alerts (repeat louder until the user reacts). */
fun alertCapability(escalation: Escalation, hub: BehaviorHub) = Capability("alert_escalate", "Important alert that repeats louder until acknowledged. action=raise (id,text) | ack (id optional) | status.",
    """{"type":"object","properties":{"action":{"type":"string","enum":["raise","ack","status"]},"id":{"type":"string"},"text":{"type":"string"}},"required":["action"]}""") { a ->
    val id = a["id"]?.jsonPrimitive?.contentOrNull; val text = a["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    when (a["action"]?.jsonPrimitive?.contentOrNull) {
        "raise" -> if (id.isNullOrBlank() || text.isEmpty()) ActionResult.failed("id and text are required") else { hub.publish(escalation.raise(id, text)); ActionResult.completed("Alert $id raised") }
        "ack" -> if (escalation.acknowledge(id)) ActionResult.completed("Acknowledged") else ActionResult.failed("No such alert")
        "status" -> ActionResult.completed(escalation.activeIds.joinToString().ifEmpty { "No active alerts" })
        else -> ActionResult.failed("action must be raise, ack or status")
    }
}

/** #335 Run a list of tool calls as one pausable task: it pauses when the user speaks and resumes afterwards. */
fun taskCapability(tasks: TaskSupervisor, registry: () -> CapabilityRegistry, run: () -> RunCapability) = Capability("task", "Run steps as an interruptible task (pauses when the user speaks, resumes after). action=start (name, steps:[{tool,args}], max 20) | status | cancel.",
    """{"type":"object","properties":{"action":{"type":"string","enum":["start","status","cancel"]},"name":{"type":"string"},"steps":{"type":"array","items":{"type":"object"}}},"required":["action"]}""") { a ->
    when (a["action"]?.jsonPrimitive?.contentOrNull) {
        "start" -> {
            val raw = (a["steps"] as? JsonArray)?.map { it.jsonObject } ?: return@Capability ActionResult.failed("steps required")
            if (raw.isEmpty() || raw.size > 20) return@Capability ActionResult.failed("1-20 steps required")
            val calls = raw.map { (it["tool"]?.jsonPrimitive?.contentOrNull ?: "") to ((it["args"] as? JsonObject) ?: JsonObject(emptyMap())) }
            calls.firstOrNull { registry().get(it.first) == null }?.let { return@Capability ActionResult.failed("unknown tool '${it.first}'") }
            if (calls.any { it.first == "shell" || it.first == "task" }) return@Capability ActionResult(ActionResult.DENIED, "tasks may not run shell or nest tasks")
            val steps: List<suspend () -> String?> = calls.map { (tool, args) -> { run()(tool, args).let { r -> if (r.status == ActionResult.COMPLETED || r.status == ActionResult.ACCEPTED) null else "$tool: ${r.message}" } } }
            if (tasks.start(a["name"]?.jsonPrimitive?.contentOrNull ?: "task", steps)) ActionResult(ActionResult.ACCEPTED, "Task started with ${steps.size} steps") else ActionResult.failed("A task is already running")
        }
        "status" -> ActionResult.completed(describe(tasks.state.value))
        "cancel" -> if (tasks.cancel()) ActionResult.completed("Cancelled") else ActionResult.failed("No task running")
        else -> ActionResult.failed("action must be start, status or cancel")
    }
}

private fun describe(s: TaskState) = when (s) {
    TaskState.None -> "No task"
    is TaskState.Running -> "Running ${s.name}: ${s.done}/${s.total}"
    is TaskState.Paused -> "Paused ${s.name}: ${s.done}/${s.total}"
    is TaskState.Finished -> "Finished ${s.name}" + (s.failed?.let { " (failed: $it)" } ?: "")
}

/** #338 Proposals mined from the audit log: repeated actions at a repeated time, with a cron expression Hermes can schedule. */
fun routineSuggestCapability(source: AuditSource, miner: RoutineMiner = RoutineMiner(), utcOffsetMs: () -> Long = { 0L }) = Capability("routine_suggest", "Mine the audit log for actions the user repeats at about the same time on several days and propose automating them.") {
    val found = miner.mine(source.lines(), utcOffsetMs())
    ActionResult.completed(
        if (found.isEmpty()) "No repeated actions found" else found.joinToString("\n") { "${it.tool} ${it.args.take(80)} around ${it.time} on ${it.days} days (cron ${it.cron})" },
        buildJsonObject { put("count", found.size); put("proposals", buildJsonArray { found.forEach { p -> add(buildJsonObject { put("tool", p.tool); put("args", p.args); put("time", p.time); put("cron", p.cron); put("days", p.days) }) } }) },
    )
}
