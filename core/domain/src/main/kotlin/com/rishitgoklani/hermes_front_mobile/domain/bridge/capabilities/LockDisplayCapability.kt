package com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Lock-screen display (#59): let the face/answers show above the keyguard and wake the screen. [apply] brings the activity forward so it takes effect. */
fun lockDisplayCapability(get: () -> Boolean, set: (Boolean) -> Unit, apply: () -> Unit) = Capability("lock_display",
    "Show the Deskbot face and answers on the lock screen: action=on|off|status. Only enable on a dedicated device without private data.",
    """{"type":"object","properties":{"action":{"type":"string","enum":["on","off","status"]}},"required":["action"]}""") { a ->
    when (a["action"]?.jsonPrimitive?.contentOrNull) {
        "on" -> { set(true); apply(); ActionResult.completed("Face shows above the lock screen", buildJsonObject { put("enabled", true) }) }
        "off" -> { set(false); apply(); ActionResult.completed("Lock-screen display off", buildJsonObject { put("enabled", false) }) }
        "status" -> ActionResult.completed(if (get()) "on" else "off", buildJsonObject { put("enabled", get()) })
        else -> ActionResult.failed("action must be on, off or status")
    }
}
