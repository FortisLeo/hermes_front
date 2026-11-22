package com.rishitgoklani.hermes_front_mobile.domain.macro

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionExecutor
import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.behavior.RunCapability
import com.rishitgoklani.hermes_front_mobile.domain.routines.ToolCall
import kotlinx.serialization.json.JsonObject

/** Records tool calls while active; saved macros replay through the same capability path (verify/audit apply). */
class MacroRecorder(
    private val ignored: Set<String> = setOf("macro_record", "macro_play", "macro_list", "macro_delete", "kill_switch", "ui_tree", "screenshot", "screen_state"),
    /** Called with all saved macros after every change so they can be persisted. */
    private val onChange: (Map<String, List<ToolCall>>) -> Unit = {},
) {
    private var name: String? = null
    private val steps = mutableListOf<ToolCall>()
    private val saved = LinkedHashMap<String, List<ToolCall>>()

    val recording get() = name != null
    fun start(n: String) { name = n; steps.clear() }
    fun stop(): Pair<String, List<ToolCall>>? { val n = name ?: return null; val s = steps.toList(); saved[n] = s; name = null; steps.clear(); onChange(all()); return n to s }
    fun record(tool: String, args: JsonObject) { if (name != null && tool !in ignored && steps.size < 200) steps += ToolCall(tool, args) }
    fun get(n: String) = saved[n]
    fun names() = saved.keys.toList()
    fun delete(n: String): Boolean = (saved.remove(n) != null).also { if (it) onChange(all()) }
    fun load(m: Map<String, List<ToolCall>>) { saved.putAll(m) }
    fun all(): Map<String, List<ToolCall>> = saved.toMap()
}

/** Wraps a capability so each successful call is recorded while a macro is being recorded. */
fun Capability.recorded(recorder: MacroRecorder): Capability = copy(executor = ActionExecutor { args ->
    val r = executor.execute(args); if (r.status == ActionResult.COMPLETED) recorder.record(name, args); r
})

fun macroCapabilities(recorder: MacroRecorder, run: () -> RunCapability): List<Capability> = listOf(
    Capability("macro_record", "Record the tool calls you make into a named macro: action=start name=… | stop.", """{"type":"object","properties":{"action":{"type":"string","enum":["start","stop"]},"name":{"type":"string"}},"required":["action"]}""") { a ->
        when ((a["action"] as? kotlinx.serialization.json.JsonPrimitive)?.content) {
            "start" -> { val n = (a["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.trim().orEmpty(); if (n.isEmpty()) ActionResult.failed("name required") else { recorder.start(n); ActionResult.completed("Recording '$n'") } }
            "stop" -> recorder.stop()?.let { ActionResult.completed("Saved '${it.first}' with ${it.second.size} steps") } ?: ActionResult.failed("not recording")
            else -> ActionResult.failed("action must be start or stop")
        }
    },
    Capability("macro_play", "Replay a saved macro by name (stops at the first failing step).", """{"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}""") { a ->
        val n = (a["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty(); val steps = recorder.get(n) ?: return@Capability ActionResult.failed("no macro '$n'")
        var done = 0; for (s in steps) { val r = run()(s.tool, s.args); if (r.status != ActionResult.COMPLETED) return@Capability ActionResult.failed("step ${done + 1} (${s.tool}) failed: ${r.message.take(80)}"); done++ }
        ActionResult.completed("Played $done steps")
    },
    Capability("macro_list", "List saved macros.") { ActionResult.completed(if (recorder.names().isEmpty()) "No macros" else recorder.names().joinToString("\n") { "$it (${recorder.get(it)!!.size} steps)" }) },
    Capability("macro_delete", "Delete a macro by name.", """{"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}""") { a ->
        if (recorder.delete((a["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty())) ActionResult.completed("Deleted") else ActionResult.failed("no such macro")
    },
)
