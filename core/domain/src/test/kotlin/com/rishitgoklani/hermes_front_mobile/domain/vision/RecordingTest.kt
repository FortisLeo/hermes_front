package com.rishitgoklani.hermes_front_mobile.domain.vision

import com.rishitgoklani.hermes_front_mobile.domain.CameraFacing
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.presenceCapability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.timeLapseCapability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.videoRecordCapability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.visualWatchCapability
import com.rishitgoklani.hermes_front_mobile.domain.sense.VisualWatcher
import com.rishitgoklani.hermes_front_mobile.testing.FakeCamera
import com.rishitgoklani.hermes_front_mobile.testing.FakeFrameSink
import com.rishitgoklani.hermes_front_mobile.testing.FakeThumbnailer
import com.rishitgoklani.hermes_front_mobile.testing.FakeVideoRecorder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingTest {
    private fun args(vararg p: Pair<String, String>) = JsonObject(p.associate { it.first to JsonPrimitive(it.second) })
    private val still = ByteArray(64) { 100 }
    private val moved = ByteArray(64) { 220.toByte() }

    // --- time-lapse
    @Test fun timeLapseSavesNumberedFramesFromChosenCamera() = runTest {
        val cam = FakeCamera(); val sink = FakeFrameSink()
        val rec = TimeLapseRecorder(cam, sink, backgroundScope)
        rec.start(CameraFacing.Back, intervalS = 5, count = 3, session = "s1")
        advanceTimeBy(11_000); runCurrent()
        assertEquals(listOf("f_0000.jpg", "f_0001.jpg", "f_0002.jpg"), sink.saved.map { it.second })
        assertTrue(cam.requests.all { it.first == CameraFacing.Back })
        assertEquals(3, rec.status.taken); assertFalse(rec.status.running)
    }
    @Test fun timeLapseStopHaltsCapture() = runTest {
        val cam = FakeCamera(); val rec = TimeLapseRecorder(cam, FakeFrameSink(), backgroundScope)
        rec.start(CameraFacing.Front, 5, 100, "s"); runCurrent(); rec.stop(); advanceTimeBy(60_000); runCurrent()
        assertEquals(1, cam.requests.size); assertFalse(rec.status.running)
    }
    @Test fun timeLapseSurvivesCaptureErrorsAndReportsThem() = runTest {
        val rec = TimeLapseRecorder(FakeCamera(failure = IllegalStateException("busy")), FakeFrameSink(), backgroundScope)
        rec.start(CameraFacing.Front, 2, 2, "s"); advanceTimeBy(5_000); runCurrent()
        assertEquals("busy", rec.status.lastError); assertEquals(0, rec.status.taken)
    }
    @Test fun timeLapseToolSanitisesNameAndDefaultsFront() = runTest {
        val cam = FakeCamera(); val rec = TimeLapseRecorder(cam, FakeFrameSink(), backgroundScope)
        val cap = timeLapseCapability(rec) { 7L }
        val r = cap.executor.execute(args("action" to "start", "name" to "../my lapse!", "count" to "1"))
        runCurrent()
        assertEquals("mylapse", r.data!!["name"]!!.jsonPrimitive.content); assertEquals("front", r.data!!["facing"]!!.jsonPrimitive.content)
        assertEquals(CameraFacing.Front, cam.requests.single().first)
        assertEquals(1, cap.executor.execute(args("action" to "status")).data!!["taken"]!!.jsonPrimitive.int)
        assertEquals("failed", cap.executor.execute(args("action" to "nope")).status)
    }
    @Test fun timeLapseToolBackAndClamps() = runTest {
        val rec = TimeLapseRecorder(FakeCamera(), FakeFrameSink(), backgroundScope)
        val r = timeLapseCapability(rec).executor.execute(args("action" to "start", "facing" to "back", "interval_s" to "0", "count" to "99999"))
        assertEquals(2, r.data!!["interval_s"]!!.jsonPrimitive.int); assertEquals(2000, r.data!!["count"]!!.jsonPrimitive.int); assertEquals("back", r.data!!["facing"]!!.jsonPrimitive.content)
        rec.stop()
    }

    // --- video
    @Test fun videoDefaultsFrontNoAudio() = runTest {
        val vr = FakeVideoRecorder()
        val r = videoRecordCapability(vr) { 5L }.executor.execute(args())
        val req = vr.requests.single()
        assertEquals(CameraFacing.Front, req.facing); assertEquals(10_000L, req.durationMs); assertFalse(req.audio); assertEquals("clip_5", req.session)
        assertEquals("/fake/clip_5.mp4", r.data!!["path"]!!.jsonPrimitive.content)
    }
    @Test fun videoBackWithAudioAndCappedDuration() = runTest {
        val vr = FakeVideoRecorder()
        videoRecordCapability(vr).executor.execute(args("facing" to "back", "audio" to "true", "duration_s" to "9999"))
        val req = vr.requests.single(); assertEquals(CameraFacing.Back, req.facing); assertTrue(req.audio); assertEquals(120_000L, req.durationMs)
    }

    // --- visual watch
    private fun TestScope.watcher(cam: FakeCamera, events: MutableList<Pair<Int, CameraFacing>>, now: () -> Long = { testScheduler.currentTime }) =
        VisualWatcher(cam, FakeThumbnailer(), backgroundScope, clock = now) { s, f -> events += s to f }

    @Test fun staticSceneRaisesNoEvent() = runTest {
        val cam = FakeCamera(frames = listOf(still)); val ev = mutableListOf<Pair<Int, CameraFacing>>()
        val w = watcher(cam, ev); w.start(CameraFacing.Front, 10)
        advanceTimeBy(55_000); runCurrent()
        assertTrue(ev.isEmpty()); assertTrue(w.status.checks >= 5)
    }
    @Test fun sceneChangeFiresOnceWithCameraFacing() = runTest {
        val cam = FakeCamera(frames = listOf(still, still, moved, moved)); val ev = mutableListOf<Pair<Int, CameraFacing>>()
        val w = watcher(cam, ev); w.start(CameraFacing.Front, 10)
        advanceTimeBy(45_000); runCurrent()
        assertEquals(1, ev.size); assertEquals(CameraFacing.Front, ev.single().second); assertTrue(ev.single().first >= 100)
        assertEquals(1, w.status.events)
    }
    @Test fun watchesBackCameraWhenAsked() = runTest {
        val cam = FakeCamera(frames = listOf(still)); val w = watcher(cam, mutableListOf())
        w.start(CameraFacing.Back, 10); advanceTimeBy(1); runCurrent()
        assertTrue(cam.requests.all { it.first == CameraFacing.Back }); assertEquals(CameraFacing.Back, w.status.facing)
    }
    @Test fun stopEndsWatching() = runTest {
        val cam = FakeCamera(frames = listOf(still)); val w = watcher(cam, mutableListOf())
        w.start(CameraFacing.Front, 10); runCurrent(); w.stop(); val n = cam.requests.size; advanceTimeBy(60_000); runCurrent()
        assertEquals(n, cam.requests.size); assertFalse(w.running)
    }
    @Test fun cameraErrorsAreReportedNotFatal() = runTest {
        val msgs = mutableListOf<String>()
        val w = VisualWatcher(FakeCamera(failure = IllegalStateException("busy")), FakeThumbnailer(), backgroundScope, diagnostics = { msgs += it }) { _, _ -> }
        w.start(CameraFacing.Front, 10); advanceTimeBy(25_000); runCurrent()
        assertTrue(msgs.size >= 2 && msgs.all { it.contains("busy") }); assertTrue(w.running)
    }
    @Test fun visualWatchToolFrontDefaultAndSensitivity() = runTest {
        val cam = FakeCamera(frames = listOf(still)); val w = watcher(cam, mutableListOf())
        val cap = visualWatchCapability(w)
        val r = cap.executor.execute(args("action" to "start", "sensitivity" to "high", "period_s" to "1"))
        runCurrent()
        assertTrue(r.message.contains("front")); assertEquals(10, w.status.periodS); assertEquals(8.0, w.status.threshold, 0.0)
        assertEquals("front", cap.executor.execute(args("action" to "status")).data!!["facing"]!!.jsonPrimitive.content)
        cap.executor.execute(args("action" to "stop")); assertFalse(w.running)
        cap.executor.execute(args("action" to "start", "facing" to "back")); runCurrent(); assertEquals(CameraFacing.Back, w.status.facing); w.stop()
        assertEquals("failed", cap.executor.execute(args("action" to "x")).status)
    }

    // --- presence
    @Test fun presenceUsesFrontByDefaultAndBackOnRequest() = runTest {
        val cam = FakeCamera(); val cap = presenceCapability(cam, { 1 })
        cap.executor.execute(args()); cap.executor.execute(args("facing" to "back"))
        assertEquals(listOf(CameraFacing.Front, CameraFacing.Back), cam.requests.map { it.first })
    }
}
