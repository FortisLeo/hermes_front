package com.rishitgoklani.hermes_front_mobile.domain.behavior

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.CapabilityRegistry
import com.rishitgoklani.hermes_front_mobile.domain.face.Reaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Night schedule; wraps midnight (e.g. 23 -> 7). */
data class SleepSchedule(val startHour: Int = 23, val endHour: Int = 7) {
    fun isSleepTime(hour: Int): Boolean = if (startHour == endHour) false else if (startHour < endHour) hour in startHour until endHour else hour >= startHour || hour < endHour
}

/** Picks the next idle micro-behavior. [roll] is 0..1 and injected so tests are deterministic. */
object IdleBehavior {
    fun next(hour: Int, schedule: SleepSchedule, roll: Float): Reaction? = when {
        schedule.isSleepTime(hour) -> null
        roll < 0.45f -> Reaction.LookAround
        roll < 0.65f -> Reaction.Blink
        roll < 0.8f -> if (hour in 13..15 || hour >= 21) Reaction.Yawn else Reaction.Glance
        roll < 0.9f -> Reaction.Nod
        else -> null
    }
}

@Serializable
data class QuickAction(val id: String, val label: String, val tool: String, val args: JsonObject = JsonObject(emptyMap()))

interface QuickActionsRepository {
    suspend fun all(): List<QuickAction>
    suspend fun set(actions: List<QuickAction>)
}

object DefaultQuickActions {
    val all = listOf(
        QuickAction("battery", "Battery", "battery"),
        QuickAction("timer5", "5 min timer", "timer_start", kotlinx.serialization.json.buildJsonObject { put("name", kotlinx.serialization.json.JsonPrimitive("quick")); put("duration", kotlinx.serialization.json.JsonPrimitive("5 min")) }),
        QuickAction("look", "Look around", "face_set", kotlinx.serialization.json.buildJsonObject { put("reaction", kotlinx.serialization.json.JsonPrimitive("look around")) }),
        QuickAction("torch", "Torch on", "torch", kotlinx.serialization.json.buildJsonObject { put("on", kotlinx.serialization.json.JsonPrimitive(true)) }),
        QuickAction("dim", "Dim 50%", "screen_dim", kotlinx.serialization.json.buildJsonObject { put("percent", kotlinx.serialization.json.JsonPrimitive(50)) }),
        QuickAction("home", "Home", "ui_global", kotlinx.serialization.json.buildJsonObject { put("action", kotlinx.serialization.json.JsonPrimitive("home")) }),
    )
}

/** Use case: run any registered capability by name (used by quick actions and UI actions without a model round trip). */
class RunCapability(private val registry: CapabilityRegistry) {
    suspend operator fun invoke(tool: String, args: JsonObject = JsonObject(emptyMap())): ActionResult {
        val c = registry.get(tool) ?: return ActionResult(ActionResult.UNKNOWN_TOOL, "No tool $tool")
        return try { c.executor.execute(args) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { ActionResult.failed(e.message ?: "failed") }
    }
}

data class DebugSnapshot(val hermesHealthy: Boolean, val hermesVersion: String, val bridgeTools: Int, val auditTail: List<String>, val note: String = "")

fun interface DebugSource { suspend fun snapshot(): DebugSnapshot }
