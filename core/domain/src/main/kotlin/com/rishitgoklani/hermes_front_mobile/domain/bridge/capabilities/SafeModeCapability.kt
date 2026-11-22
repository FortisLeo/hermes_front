package com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Reports whether the app started in safe mode (voice/routines/ambient/sensing disabled after repeated crashes). Exit is local-only. */
fun safeModeCapability(isSafe: () -> Boolean) = Capability("safe_mode", "Is the app in safe mode (started minimal after repeated crashes)? Exit it on the phone (Debug tab).") {
    ActionResult.completed(if (isSafe()) "SAFE MODE: only core tools run" else "Normal", buildJsonObject { put("safe_mode", isSafe()) })
}
