package com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.voice.WakeController
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Turn the always-listening wake-word loop on/off or read its state. [modelReady] reports whether the speech model is installed. */
fun wakeCapability(controller: WakeController, modelReady: () -> Boolean) = Capability(
    "wake_control", "Always-listening wake word ('hey bot'): action=on|off|status.",
    """{"type":"object","properties":{"action":{"type":"string","enum":["on","off","status"]}},"required":["action"]}""",
) { a ->
    when (a["action"]?.jsonPrimitive?.contentOrNull) {
        "on" -> { controller.start(); ActionResult.completed("Listening for the wake word", status(controller, modelReady)) }
        "off" -> { controller.stop(); ActionResult.completed("Wake word off", status(controller, modelReady)) }
        "status" -> ActionResult.completed(if (controller.enabled.value) "Listening (${controller.state.value})" else "Off", status(controller, modelReady))
        else -> ActionResult.failed("action must be on, off or status")
    }
}

private fun status(c: WakeController, modelReady: () -> Boolean) = buildJsonObject { put("enabled", c.enabled.value); put("state", c.state.value.name); put("model_ready", modelReady()) }
