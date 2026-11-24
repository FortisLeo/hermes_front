package com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities

import com.rishitgoklani.hermes_front_mobile.domain.CameraPort
import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.vision.facingArg
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Bridge responses are capped by the Hermes adapter at 256 KB per line, so keep the base64 payload under ~190 KB. */
const val CAMERA_MAX_JPEG_BYTES = 140_000

fun cameraCaptureCapability(camera: CameraPort, encode: (ByteArray) -> String): Capability = Capability(
    name = "camera_capture",
    description = "Take a photo with the phone camera and return it as a base64 JPEG in data.image_base64. Use for 'what is this' / 'look at' questions.",
    inputSchemaJson = """{"type":"object","properties":{"facing":{"type":"string","enum":["front","back"],"description":"Default front (faces the user)"}}}""",
) { args ->
    val facing = args.facingArg()
    val jpeg = camera.capture(facing, CAMERA_MAX_JPEG_BYTES)
    ActionResult.completed("Captured ${jpeg.size / 1024} KB ${facing.name.lowercase()} photo", buildJsonObject {
        put("image_base64", encode(jpeg)); put("mime", "image/jpeg"); put("bytes", jpeg.size)
    })
}
