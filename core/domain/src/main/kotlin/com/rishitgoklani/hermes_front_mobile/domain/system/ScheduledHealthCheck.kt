package com.rishitgoklani.hermes_front_mobile.domain.system

import com.rishitgoklani.hermes_front_mobile.domain.SpeechSynthesizer
import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.notifications.NotificationPoster
import com.rishitgoklani.hermes_front_mobile.domain.state.PeriodicTask
import com.rishitgoklani.hermes_front_mobile.domain.state.StateJson
import com.rishitgoklani.hermes_front_mobile.domain.state.StateStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Serializable data class HealthCheckConfig(
    val enabled: Boolean = false, val intervalMin: Int = 60, val minScore: Int = 70, val speak: Boolean = false,
    val lastRunMs: Long = 0, val lastScore: Int = -1, val lastIssue: String = "", val lastAlertMs: Long = 0, val lastAlertIssue: String = "",
)

/**
 * Scheduled health checks (#188): runs the same [HealthScore] as the health_score tool every [HealthCheckConfig.intervalMin]
 * and alerts when it falls below the threshold. The same issue is not re-alerted for [REALERT_MS] so a stuck problem does not nag hourly.
 */
class ScheduledHealthCheck(
    private val read: suspend () -> HealthScore.Input, private val store: StateStore, private val poster: NotificationPoster, private val speaker: SpeechSynthesizer,
) : PeriodicTask {
    override val name = "health_check"
    private val key = "health_check"

    fun config(): HealthCheckConfig = runCatching { StateJson.decodeFromString(HealthCheckConfig.serializer(), store.load(key) ?: "") }.getOrDefault(HealthCheckConfig())
    fun configure(c: HealthCheckConfig) {
        require(c.intervalMin in 5..1440) { "interval_min must be 5-1440" }; require(c.minScore in 1..100) { "min_score must be 1-100" }
        store.save(key, StateJson.encodeToString(HealthCheckConfig.serializer(), c))
    }

    override suspend fun tick(nowMs: Long) {
        val c = config()
        if (!c.enabled || (c.lastRunMs != 0L && nowMs - c.lastRunMs < c.intervalMin * 60_000L)) return
        run(nowMs)
    }

    /** Runs one check now, ignoring the interval; returns the result. */
    suspend fun run(nowMs: Long): HealthScore.Result {
        val r = HealthScore.compute(read())
        var c = config().copy(lastRunMs = nowMs, lastScore = r.score, lastIssue = r.issue)
        if (r.score < c.minScore && (r.issue != c.lastAlertIssue || nowMs - c.lastAlertMs >= REALERT_MS)) {
            val msg = "Health ${r.score} out of 100: ${r.issue}"
            poster.post("Deskbot health check", msg); if (c.speak) speaker.enqueue(msg)
            c = c.copy(lastAlertMs = nowMs, lastAlertIssue = r.issue)
        } else if (r.score >= c.minScore) c = c.copy(lastAlertIssue = "")
        store.save(key, StateJson.encodeToString(HealthCheckConfig.serializer(), c))
        return r
    }

    companion object { const val REALERT_MS = 6 * 3_600_000L }
}

fun healthCheckCapability(check: ScheduledHealthCheck, clock: () -> Long = System::currentTimeMillis) = Capability("health_check",
    "Scheduled device health checks: action=enable (interval_min 5-1440 default 60, min_score default 70, speak) | disable | status | run. Alerts by notification when the health score drops below min_score.",
    """{"type":"object","properties":{"action":{"type":"string","enum":["enable","disable","status","run"]},"interval_min":{"type":"integer"},"min_score":{"type":"integer"},"speak":{"type":"boolean"}},"required":["action"]}""") { a: JsonObject ->
    fun int(k: String) = a[k]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
    fun status(): ActionResult { val c = check.config(); return ActionResult.completed(
        if (c.enabled) "Checking every ${c.intervalMin} min, alert below ${c.minScore}" + (if (c.lastScore >= 0) ", last ${c.lastScore}/100" else "") else "Scheduled checks off",
        buildJsonObject { put("enabled", c.enabled); put("interval_min", c.intervalMin); put("min_score", c.minScore); put("last_score", c.lastScore); put("last_issue", c.lastIssue) }) }
    when (a["action"]?.jsonPrimitive?.contentOrNull) {
        "enable" -> {
            val old = check.config()
            val next = old.copy(enabled = true, intervalMin = int("interval_min") ?: old.intervalMin.takeIf { old.enabled } ?: 60, minScore = int("min_score") ?: old.minScore.takeIf { old.enabled } ?: 70,
                speak = a["speak"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: old.speak, lastRunMs = 0)
            try { check.configure(next) } catch (e: IllegalArgumentException) { return@Capability ActionResult.failed(e.message ?: "invalid") }
            status()
        }
        "disable" -> { check.configure(check.config().copy(enabled = false)); status() }
        "status" -> status()
        "run" -> check.run(clock()).let { ActionResult.completed("${it.score}/100" + if (it.issue != "none") " (${it.issue})" else "", buildJsonObject { put("score", it.score); put("issue", it.issue) }) }
        else -> ActionResult.failed("action must be enable, disable, status or run")
    }
}
