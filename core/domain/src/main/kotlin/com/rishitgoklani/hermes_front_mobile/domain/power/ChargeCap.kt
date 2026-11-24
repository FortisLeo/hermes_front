package com.rishitgoklani.hermes_front_mobile.domain.power

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

/** Current battery reading; [percent] is -1 when unknown. */
data class BatteryStatus(val percent: Int, val charging: Boolean)

/** Port: reads the sticky battery state. Shared by charge cap and geofence polling. */
fun interface BatteryStatusReader { fun read(): BatteryStatus }

@Serializable data class ChargeCapConfig(val capPercent: Int = 0, val alertedThisSession: Boolean = false)

/**
 * Software charge cap (#152). Without a charge-control kernel interface the app cannot stop charging, so it alerts once
 * per charge session when the level reaches the cap, and re-arms when unplugged or after the level falls 5 points below the cap.
 */
class ChargeCapGuard(private val store: StateStore) {
    private val key = "charge_cap"
    fun config(): ChargeCapConfig = runCatching { StateJson.decodeFromString(ChargeCapConfig.serializer(), store.load(key) ?: "") }.getOrDefault(ChargeCapConfig())
    fun setCap(percent: Int) { require(percent == 0 || percent in 50..100) { "cap must be 0 (off) or 50-100" }; save(ChargeCapConfig(percent, false)) }

    /** True exactly when an alert should fire now. */
    fun shouldAlert(s: BatteryStatus): Boolean {
        val c = config()
        if (c.capPercent == 0 || s.percent < 0) return false
        if (!s.charging || s.percent <= c.capPercent - 5) { if (c.alertedThisSession) save(c.copy(alertedThisSession = false)); return false }
        if (s.percent >= c.capPercent && !c.alertedThisSession) { save(c.copy(alertedThisSession = true)); return true }
        return false
    }
    private fun save(c: ChargeCapConfig) = store.save(key, StateJson.encodeToString(ChargeCapConfig.serializer(), c))
}

/** Periodic task: checks the battery each tick (cheap sticky-intent read) and alerts with a notification and speech. */
class ChargeCapTask(private val guard: ChargeCapGuard, private val battery: BatteryStatusReader, private val poster: NotificationPoster, private val speaker: SpeechSynthesizer) : PeriodicTask {
    override val name = "charge_cap"
    override suspend fun tick(nowMs: Long) {
        val s = battery.read()
        if (guard.shouldAlert(s)) {
            val msg = "Battery is at ${s.percent} percent. Unplug the charger to protect the battery."
            poster.post("Charge limit reached", msg); speaker.enqueue(msg)
        }
    }
}

fun chargeLimitCapability(guard: ChargeCapGuard, battery: BatteryStatusReader, task: ChargeCapTask) = Capability("charge_limit",
    "Software charge cap: action=set (percent 50-100) | off | status | check. Alerts with a notification and speech when charging reaches the cap; it cannot stop charging itself (hardware cap needs a root script).",
    """{"type":"object","properties":{"action":{"type":"string","enum":["set","off","status","check"]},"percent":{"type":"integer"}},"required":["action"]}""") { a: JsonObject ->
    fun status(msg: String) = ActionResult.completed(msg, buildJsonObject { put("cap_percent", guard.config().capPercent); put("battery_percent", battery.read().percent); put("charging", battery.read().charging) })
    when (a["action"]?.jsonPrimitive?.contentOrNull) {
        "set" -> {
            val p = a["percent"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return@Capability ActionResult.failed("percent required")
            if (p !in 50..100) return@Capability ActionResult.failed("percent must be 50-100")
            guard.setCap(p); status("Charge alert at $p percent")
        }
        "off" -> { guard.setCap(0); status("Charge cap off") }
        "status" -> guard.config().capPercent.let { status(if (it == 0) "Charge cap off" else "Charge alert at $it percent") }
        "check" -> { task.tick(System.currentTimeMillis()); status("Checked") }
        else -> ActionResult.failed("action must be set, off, status or check")
    }
}
