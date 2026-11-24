package com.rishitgoklani.hermes_front_mobile.domain.routines

import com.rishitgoklani.hermes_front_mobile.domain.behavior.RunCapability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** One tool call of a template, run [delayBeforeMs] after the previous step. */
data class TemplateStep(val tool: String, val args: JsonObject = JsonObject(emptyMap()), val delayBeforeMs: Long = 0)

/** A built-in routine: turns params into an ordered plan of tool calls (#226 wake-up, #227 bedtime, #228 focus). Pure; time is only expressed as delays. */
interface RoutineTemplate {
    val id: String
    val description: String
    /** Throws [IllegalArgumentException] with a speakable message on bad params. */
    fun plan(params: JsonObject): List<TemplateStep>
    /** Steps that undo the template when it is cancelled early. */
    fun cleanup(): List<TemplateStep> = emptyList()
}

internal fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.contentOrNull?.trim()?.ifEmpty { null }
internal fun JsonObject.intArg(k: String, default: Int, range: IntRange): Int {
    val v = this[k]?.jsonPrimitive?.contentOrNull ?: return default
    val n = v.toIntOrNull() ?: throw IllegalArgumentException("$k must be a whole number")
    require(n in range) { "$k must be ${range.first}-${range.last}" }; return n
}
private fun args(vararg p: Pair<String, Any>) = buildJsonObject { p.forEach { (k, v) -> if (v is Int) put(k, v) else put(k, v.toString()) } }

/** Gradual brightness ramp, DND off, then a spoken greeting. The briefing (news, weather, calendar) is composed by Hermes and passed as `briefing`. */
class WakeUpTemplate : RoutineTemplate {
    override val id = "wakeup"
    override val description = "Wake-up: brightness ramps up over ramp_min (1-30, default 5), Do Not Disturb off, spoken greeting plus optional briefing text."
    override fun plan(params: JsonObject): List<TemplateStep> {
        val ramp = params.intArg("ramp_min", 5, 1..30); val levels = listOf(10, 30, 55, 80, 100); val gap = ramp * 60_000L / (levels.size - 1)
        val greeting = "Good morning." + (params.str("briefing")?.let { " $it".take(600) } ?: "")
        return listOf(TemplateStep("dnd", args("mode" to "off")), TemplateStep("screen_dim", args("percent" to 0))) +
            levels.mapIndexed { i, l -> TemplateStep("brightness", args("level" to l), if (i == 0) 0 else gap) } +
            TemplateStep("tts_say", args("text" to greeting))
    }
}

/** Dim and warm the screen, DND on, speak a short wind-down (optionally with tomorrow's summary and alarm check text from Hermes). */
class BedtimeTemplate : RoutineTemplate {
    override val id = "bedtime"
    override val description = "Bedtime: warm dim overlay, low brightness, Do Not Disturb (mode priority|alarms|silence, default alarms), spoken goodnight with optional `summary` of tomorrow."
    override fun plan(params: JsonObject): List<TemplateStep> {
        val mode = params.str("dnd") ?: "alarms"; require(mode in setOf("priority", "alarms", "silence")) { "dnd must be priority, alarms or silence" }
        val dim = params.intArg("dim_percent", 40, 0..90)
        val say = "Good night." + (params.str("summary")?.let { " Tomorrow: $it".take(600) } ?: "") + " Do Not Disturb is on."
        return listOf(TemplateStep("brightness", args("level" to 5)), TemplateStep("screen_dim", args("percent" to dim, "tint" to "FF8800")),
            TemplateStep("dnd", args("mode" to mode)), TemplateStep("tts_say", args("text" to say)))
    }
}

/** Timer plus Do Not Disturb plus the blocked-notification filter; everything is undone when the session ends. */
class FocusTemplate : RoutineTemplate {
    override fun cleanup() = listOf(TemplateStep("dnd", args("mode" to "off")), TemplateStep("focus_filter", args("action" to "off")))
    override val id = "focus"
    override val description = "Focus session: minutes (1-180, default 25) with a timer, Do Not Disturb priority and a filter that dismisses notifications from apps not in allow (list of app names). Ends by itself."
    override fun plan(params: JsonObject): List<TemplateStep> {
        val m = params.intArg("minutes", 25, 1..180)
        val allow = (params["allow"] as? JsonArray) ?: JsonArray(emptyList())
        return listOf(
            TemplateStep("focus_filter", buildJsonObject { put("action", "on"); put("minutes", m); put("allow", allow) }),
            TemplateStep("dnd", args("mode" to "priority")),
            TemplateStep("timer_start", args("name" to "focus", "duration" to "${m}m")),
            TemplateStep("tts_say", args("text" to "Focus for $m minutes.")),
            TemplateStep("dnd", args("mode" to "off"), m * 60_000L),
            TemplateStep("focus_filter", args("action" to "off")),
            TemplateStep("tts_say", args("text" to "Focus session finished. Nice work.")),
        )
    }
}

/** Registry: templates are looked up by id; adding one never edits existing classes. */
class RoutineTemplateRegistry(templates: List<RoutineTemplate> = emptyList()) {
    private val byId = LinkedHashMap<String, RoutineTemplate>()
    init { templates.forEach(::register) }
    fun register(t: RoutineTemplate) { require(t.id !in byId) { "Duplicate template: ${t.id}" }; byId[t.id] = t }
    fun get(id: String) = byId[id]
    fun list() = byId.values.toList()
}

/** Runs a template's steps in the background through the normal capability path; a second run of the same template replaces the first. */
class TemplateRunner(private val registry: RoutineTemplateRegistry, private val run: RunCapability, private val scope: CoroutineScope, private val pause: suspend (Long) -> Unit = { delay(it) }) {
    private val jobs = HashMap<String, Job>()
    val results = mutableListOf<String>()

    /** Returns the plan size, or throws [IllegalArgumentException] for an unknown template or bad params. */
    @Synchronized fun start(id: String, params: JsonObject): Int {
        val t = registry.get(id) ?: throw IllegalArgumentException("unknown template '$id' (valid: ${registry.list().joinToString { it.id }})")
        val steps = t.plan(params)
        jobs.remove(id)?.cancel()
        jobs[id] = scope.launch {
            for (s in steps) {
                if (s.delayBeforeMs > 0) pause(s.delayBeforeMs)
                results += "${s.tool}:${run(s.tool, s.args).status}"
            }
            synchronized(this@TemplateRunner) { jobs.remove(id) }
        }
        return steps.size
    }
    @Synchronized fun cancel(id: String): Boolean {
        val job = jobs.remove(id) ?: return false
        job.cancel(); registry.get(id)?.cleanup()?.takeIf { it.isNotEmpty() }?.let { steps -> scope.launch { steps.forEach { results += "${it.tool}:${run(it.tool, it.args).status}" } } }
        return true
    }
    @Synchronized fun running(): Set<String> = jobs.filterValues { it.isActive }.keys
}

fun routineTemplateCapabilities(registry: RoutineTemplateRegistry, runner: TemplateRunner): List<Capability> = listOf(
    Capability("routine_template_list", "List built-in routine templates (wakeup, bedtime, focus) and which are running.") {
        ActionResult.completed(registry.list().joinToString("\n") { "${it.id}${if (it.id in runner.running()) " (running)" else ""}: ${it.description}" })
    },
    Capability("routine_template_run", "Start a built-in routine template by id with optional params (wakeup: ramp_min, briefing; bedtime: dnd, dim_percent, summary; focus: minutes, allow). Runs in the background.",
        """{"type":"object","properties":{"id":{"type":"string","enum":["wakeup","bedtime","focus"]},"params":{"type":"object"}},"required":["id"]}""") { a ->
        val id = a["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        try { val n = runner.start(id, a["params"] as? JsonObject ?: JsonObject(emptyMap())); ActionResult(ActionResult.ACCEPTED, "Started $id ($n steps)") }
        catch (e: IllegalArgumentException) { ActionResult.failed(e.message ?: "bad params") }
    },
    Capability("routine_template_cancel", "Stop a running routine template by id (a focus session also needs focus_filter off and dnd off).",
        """{"type":"object","properties":{"id":{"type":"string"}},"required":["id"]}""") { a ->
        if (runner.cancel(a["id"]?.jsonPrimitive?.contentOrNull.orEmpty())) ActionResult.completed("Stopped") else ActionResult.failed("Not running")
    },
)
