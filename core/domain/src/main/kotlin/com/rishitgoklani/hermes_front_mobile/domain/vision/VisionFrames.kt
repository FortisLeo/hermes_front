package com.rishitgoklani.hermes_front_mobile.domain.vision

import com.rishitgoklani.hermes_front_mobile.domain.CameraFacing
import com.rishitgoklani.hermes_front_mobile.domain.CameraPort
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

sealed interface FrameResult {
    data class Ok(val jpeg: ByteArray, val origin: String) : FrameResult
    data class Err(val message: String) : FrameResult
}

/** `facing` argument shared by every camera tool. Defaults to front: the desk robot faces the user. */
fun JsonObject.facingArg(default: CameraFacing = CameraFacing.Front): CameraFacing =
    when (this["facing"]?.jsonPrimitive?.contentOrNull?.lowercase()) { "back", "rear" -> CameraFacing.Back; "front" -> CameraFacing.Front; else -> default }

/** Where a vision tool gets its pixels: the phone screen or a camera (front or back). */
class VisionFrames(private val camera: CameraPort, private val screen: suspend () -> ByteArray?) {
    /** `source` = "screen" | "camera"; [defaultSource] applies when absent. `facing` only matters for the camera. */
    suspend fun grab(args: JsonObject, defaultSource: String, maxBytes: Int = HIRES_BYTES, longSidePx: Int = HIRES_LONG_SIDE): FrameResult {
        return when (val src = (args["source"]?.jsonPrimitive?.contentOrNull ?: defaultSource).lowercase()) {
            "screen" -> screen()?.let { FrameResult.Ok(it, "screen") } ?: FrameResult.Err("Screenshot unavailable")
            "camera" -> { val f = args.facingArg(); FrameResult.Ok(camera.capture(f, maxBytes, longSidePx), "${f.name.lowercase()} camera") }
            else -> FrameResult.Err("source must be screen or camera, got $src")
        }
    }

    companion object { const val HIRES_BYTES = 600_000; const val HIRES_LONG_SIDE = 1920 }
}
