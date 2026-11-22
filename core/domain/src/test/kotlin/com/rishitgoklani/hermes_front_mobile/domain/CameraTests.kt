package com.rishitgoklani.hermes_front_mobile.domain

import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.CAMERA_MAX_JPEG_BYTES
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.cameraCaptureCapability
import com.rishitgoklani.hermes_front_mobile.testing.FakeCamera
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraTests {
    private val enc = { b: ByteArray -> b.joinToString(",") }

    @Test fun defaultsToFrontAndReturnsBase64() = runBlocking {
        val cam = FakeCamera()
        val r = cameraCaptureCapability(cam, enc).executor.execute(JsonObject(emptyMap()))
        assertEquals("completed", r.status)
        assertEquals("1,2,3", r.data!!["image_base64"]!!.jsonPrimitive.content)
        assertEquals(listOf(CameraFacing.Front to CAMERA_MAX_JPEG_BYTES), cam.requests)
    }
    @Test fun backFacingHonoured() = runBlocking {
        val cam = FakeCamera()
        cameraCaptureCapability(cam, enc).executor.execute(JsonObject(mapOf("facing" to JsonPrimitive("BACK"))))
        assertEquals(CameraFacing.Back, cam.requests.single().first)
    }
    @Test fun cameraFailureBubblesToProtocolAsException() {
        val cap = cameraCaptureCapability(FakeCamera(failure = IllegalStateException("busy")), enc)
        try { runBlocking { cap.executor.execute(JsonObject(emptyMap())) }; throw AssertionError("expected") } catch (e: IllegalStateException) { assertEquals("busy", e.message) }
    }
    @Test fun qualityPicksFirstFitting() = assertEquals(65, QualityPlanner.pick(1000) { q -> q * 15 })
    @Test fun qualityFallsBackToLowest() = assertEquals(25, QualityPlanner.pick(10) { 9999 })
    @Test fun scaleOnlyShrinks() { assertEquals(0.5f, QualityPlanner.scaleFor(2000, 1000, 1000), 0.001f); assertEquals(1f, QualityPlanner.scaleFor(800, 600, 1024), 0f) }
}
