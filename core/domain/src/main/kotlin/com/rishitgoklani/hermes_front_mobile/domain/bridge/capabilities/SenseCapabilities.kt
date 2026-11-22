package com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities

import com.rishitgoklani.hermes_front_mobile.domain.CameraFacing
import com.rishitgoklani.hermes_front_mobile.domain.CameraPort
import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.safety.KillSwitch
import com.rishitgoklani.hermes_front_mobile.domain.sense.FaceCounter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Is someone in front of the camera? Counts faces only; never identifies anyone and never stores the frame. */
fun presenceCapability(camera: CameraPort, counter: FaceCounter) = Capability(
    "presence_check", "Check whether a person is in front of the front camera (face count only, no identity, frame discarded).",
) {
    val jpeg = camera.capture(CameraFacing.Front, 80_000)
    val n = counter.count(jpeg)
    ActionResult.completed(if (n > 0) "Someone is there ($n)" else "Nobody visible", buildJsonObject { put("present", n > 0); put("faces", n) })
}

/** Kill switch from the Hermes side: status and trigger only. Reset is deliberately local-only. */
fun killSwitchCapability(kill: KillSwitch) = Capability(
    "kill_switch", "Emergency stop status or trigger (action=status|trigger). Only the user can reset it, on the phone.",
    """{"type":"object","properties":{"action":{"type":"string","enum":["status","trigger"]}},"required":["action"]}""",
) { a ->
    when (a["action"]?.jsonPrimitive?.contentOrNull) {
        "status" -> ActionResult.completed(if (kill.isActive()) "ACTIVE: ${kill.reason}" else "Inactive", buildJsonObject { put("active", kill.isActive()) })
        "trigger" -> { kill.trigger("requested by Hermes"); ActionResult.completed("Kill switch triggered") }
        else -> ActionResult.failed("action must be status or trigger")
    }
}

data class SelfCheck(val name: String, val run: suspend () -> String?)  // null = pass, otherwise the failure reason

/** Post-update health check (#404): runs every check with a timeout and reports pass/fail per check. */
fun selfTestCapability(checks: List<SelfCheck>, timeoutMs: Long = 8000) = Capability(
    "self_test", "Run the app self-test: bridge, speech, accessibility, notification access, camera, Hermes link. Returns pass/fail per check.",
) {
    val results = checks.map { c ->
        val failure: String? = try { withTimeout(timeoutMs) { c.run() } }
        catch (e: TimeoutCancellationException) { "timed out after ${timeoutMs / 1000}s" }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { e.message ?: e::class.simpleName }
        c to failure
    }
    ActionResult(if (results.all { it.second == null }) ActionResult.COMPLETED else ActionResult.FAILED,
        results.joinToString("\n") { (c, f) -> (if (f == null) "PASS " else "FAIL ") + c.name + (f?.let { ": $it" } ?: "") },
        buildJsonObject { put("passed", results.count { it.second == null }); put("total", results.size); put("failed", buildJsonArray { results.filter { it.second != null }.forEach { add(kotlinx.serialization.json.JsonPrimitive(it.first.name)) } }) })
}
