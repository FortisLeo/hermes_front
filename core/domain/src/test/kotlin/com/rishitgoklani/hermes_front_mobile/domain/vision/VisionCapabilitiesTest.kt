package com.rishitgoklani.hermes_front_mobile.domain.vision

import com.rishitgoklani.hermes_front_mobile.domain.CameraFacing
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.countObjectsCapability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.ocrCapability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.scanCodeCapability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.scanDocumentCapability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.tableToCsvCapability
import com.rishitgoklani.hermes_front_mobile.testing.FakeCamera
import com.rishitgoklani.hermes_front_mobile.testing.FakeCodeScanner
import com.rishitgoklani.hermes_front_mobile.testing.FakeFrameSink
import com.rishitgoklani.hermes_front_mobile.testing.FakeObjectRecognizer
import com.rishitgoklani.hermes_front_mobile.testing.FakeTextReader
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionCapabilitiesTest {
    private fun args(vararg p: Pair<String, String>) = JsonObject(p.associate { it.first to JsonPrimitive(it.second) })
    private val screenBytes = byteArrayOf(9, 9)
    private fun frames(cam: FakeCamera, screen: ByteArray? = screenBytes) = VisionFrames(cam) { screen }
    private val read = TextRead("Name Qty\nApple 3", listOf(TextBox("Name", 10, 10, 60, 30), TextBox("Qty", 200, 10, 240, 30), TextBox("Apple", 10, 50, 70, 70), TextBox("3", 200, 50, 210, 70)))

    @Test fun ocrReadsScreenByDefault() = runTest {
        val cam = FakeCamera(); val reader = FakeTextReader(read)
        val r = ocrCapability(frames(cam), reader).executor.execute(args())
        assertEquals("completed", r.status); assertTrue(r.message.contains("Apple 3"))
        assertTrue(cam.requests.isEmpty()); assertEquals(screenBytes.toList(), reader.inputs.single().toList())
        assertEquals("screen", r.data!!["origin"]!!.jsonPrimitive.content); assertEquals(4, r.data!!["boxes"]!!.jsonArray.size)
    }
    @Test fun ocrFrontCameraWithHighResolution() = runTest {
        val cam = FakeCamera()
        ocrCapability(frames(cam), FakeTextReader(read)).executor.execute(args("source" to "camera"))
        assertEquals(CameraFacing.Front, cam.requests.single().first); assertEquals(VisionFrames.HIRES_LONG_SIDE, cam.longSides.single())
    }
    @Test fun ocrBackCameraHonoured() = runTest {
        val cam = FakeCamera()
        ocrCapability(frames(cam), FakeTextReader(read)).executor.execute(args("source" to "camera", "facing" to "back"))
        assertEquals(CameraFacing.Back, cam.requests.single().first)
    }
    @Test fun ocrEmptyIsSpeakable() = runTest {
        val r = ocrCapability(frames(FakeCamera()), FakeTextReader()).executor.execute(args())
        assertEquals("completed", r.status); assertTrue(r.message.startsWith("No text"))
    }
    @Test fun ocrScreenshotUnavailableFails() = runTest {
        val r = ocrCapability(frames(FakeCamera(), screen = null), FakeTextReader(read)).executor.execute(args())
        assertEquals("failed", r.status)
    }
    @Test fun badSourceFails() = runTest {
        assertEquals("failed", ocrCapability(frames(FakeCamera()), FakeTextReader(read)).executor.execute(args("source" to "tv")).status)
    }

    @Test fun tableBecomesCsv() = runTest {
        val r = tableToCsvCapability(frames(FakeCamera()), FakeTextReader(read)) { "b64" }.executor.execute(args())
        assertEquals("Name,Qty\nApple,3", r.data!!["csv"]!!.jsonPrimitive.content)
        assertEquals(2, r.data!!["rows"]!!.jsonPrimitive.int); assertNull(r.data!!["image_base64"])
    }
    @Test fun tableCanIncludeImageForCharts() = runTest {
        val r = tableToCsvCapability(frames(FakeCamera()), FakeTextReader(read)) { "b64" }.executor.execute(args("include_image" to "true"))
        assertEquals("b64", r.data!!["image_base64"]!!.jsonPrimitive.content)
    }
    @Test fun tableFromFrontCamera() = runTest {
        val cam = FakeCamera()
        tableToCsvCapability(frames(cam), FakeTextReader(read)) { "" }.executor.execute(args("source" to "camera"))
        assertEquals(CameraFacing.Front, cam.requests.single().first)
    }

    @Test fun scanCodeRetriesUntilFound() = runTest {
        val cam = FakeCamera(); val scanner = FakeCodeScanner(listOf(emptyList(), emptyList(), listOf(ScannedCode("QR_CODE", "https://example.com"))))
        val r = scanCodeCapability(frames(cam), scanner, retryDelayMs = 1).executor.execute(args())
        assertEquals(3, scanner.calls); assertEquals(3, cam.requests.size); assertTrue(cam.requests.all { it.first == CameraFacing.Front })
        assertEquals("url", r.data!!["codes"]!!.jsonArray[0].jsonObject["kind"]!!.jsonPrimitive.content)
        assertEquals(3, r.data!!["attempts"]!!.jsonPrimitive.int)
    }
    @Test fun scanCodeGivesUpAfterAttempts() = runTest {
        val scanner = FakeCodeScanner()
        val r = scanCodeCapability(frames(FakeCamera()), scanner, retryDelayMs = 1).executor.execute(args("attempts" to "2", "facing" to "back"))
        assertEquals(2, scanner.calls); assertTrue(r.message.startsWith("No code")); assertEquals(0, r.data!!["codes"]!!.jsonArray.size)
    }
    @Test fun scanCodeAttemptsAreCapped() = runTest {
        val scanner = FakeCodeScanner()
        scanCodeCapability(frames(FakeCamera()), scanner, retryDelayMs = 1).executor.execute(args("attempts" to "99"))
        assertEquals(6, scanner.calls)
    }
    @Test fun codeKindsClassified() {
        assertEquals("wifi", ScannedCode("QR_CODE", "WIFI:S:x;T:WPA;P:y;;").kind); assertEquals("phone", ScannedCode("QR_CODE", "tel:123").kind)
        assertEquals("text", ScannedCode("EAN_13", "4006381333931").kind)
    }

    @Test fun documentScanSavesAndReads() = runTest {
        val cam = FakeCamera(); val sink = FakeFrameSink()
        val r = scanDocumentCapability(frames(cam), FakeTextReader(read), sink) { 42L }.executor.execute(args("facing" to "back"))
        assertEquals("/fake/scans/scan_42.jpg", r.data!!["path"]!!.jsonPrimitive.content); assertEquals(CameraFacing.Back, cam.requests.single().first)
        assertEquals("scans", sink.saved.single().first)
    }
    @Test fun documentScanDefaultsToFrontCamera() = runTest {
        val cam = FakeCamera()
        scanDocumentCapability(frames(cam), FakeTextReader(read), FakeFrameSink()).executor.execute(args())
        assertEquals(CameraFacing.Front, cam.requests.single().first)
    }

    @Test fun countsGroupedByLabel() = runTest {
        val scene = ObjectScene(listOf(DetectedObject("Plant", .9f), DetectedObject("plant", .8f), DetectedObject("Cup", .7f), DetectedObject("", .6f)), listOf(DetectedObject("Desk", .95f)))
        val cam = FakeCamera()
        val r = countObjectsCapability(frames(cam), FakeObjectRecognizer(scene)).executor.execute(args())
        assertEquals(4, r.data!!["total"]!!.jsonPrimitive.int); assertEquals(2, r.data!!["counts"]!!.jsonObject["plant"]!!.jsonPrimitive.int)
        assertEquals(1, r.data!!["counts"]!!.jsonObject["object"]!!.jsonPrimitive.int); assertTrue(r.message.startsWith("I count 4"))
        assertEquals(CameraFacing.Front, cam.requests.single().first)
    }
    @Test fun countFilterByLabel() = runTest {
        val scene = ObjectScene(listOf(DetectedObject("Plant", .9f), DetectedObject("Cup", .7f)), emptyList())
        val r = countObjectsCapability(frames(FakeCamera()), FakeObjectRecognizer(scene)).executor.execute(args("label" to "cup"))
        assertEquals(1, r.data!!["total"]!!.jsonPrimitive.int)
    }
    @Test fun countFallsBackToLabels() = runTest {
        val r = countObjectsCapability(frames(FakeCamera()), FakeObjectRecognizer(ObjectScene(emptyList(), listOf(DetectedObject("Desk", .9f))))).executor.execute(args())
        assertTrue(r.message.contains("Desk")); assertEquals(0, r.data!!["total"]!!.jsonPrimitive.int)
    }
}
