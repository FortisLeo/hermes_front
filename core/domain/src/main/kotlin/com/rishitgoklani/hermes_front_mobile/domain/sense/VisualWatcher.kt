package com.rishitgoklani.hermes_front_mobile.domain.sense

import com.rishitgoklani.hermes_front_mobile.domain.CameraFacing
import com.rishitgoklani.hermes_front_mobile.domain.CameraPort
import com.rishitgoklani.hermes_front_mobile.domain.vision.FrameThumbnailer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Camera change watcher (#116): every [periodS] seconds grabs a small frame from the front or back camera, diffs thumbnails, reports changes. */
class VisualWatcher(
    private val camera: CameraPort,
    private val thumbnailer: FrameThumbnailer,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val diagnostics: (String) -> Unit = {},
    private val onChange: suspend (score: Int, facing: CameraFacing) -> Unit,
) {
    data class Status(val running: Boolean, val facing: CameraFacing, val periodS: Int, val threshold: Double, val checks: Int, val events: Int, val lastScore: Double?)

    private var job: Job? = null
    private var state = Status(false, CameraFacing.Front, 0, 0.0, 0, 0, null)
    val running get() = job?.isActive == true
    val status get() = state.copy(running = running)

    fun start(facing: CameraFacing, periodS: Int, threshold: Double = DEFAULT_THRESHOLD, cooldownMs: Long = 30_000) {
        stop()
        state = Status(true, facing, periodS, threshold, 0, 0, null)
        val detector = ChangeDetector(threshold, cooldownMs, clock)
        job = scope.launch {
            var prev: ByteArray? = null
            while (true) {
                try {
                    val t = thumbnailer.thumb(withTimeout(12_000) { camera.capture(facing, 40_000) })
                    prev?.takeIf { it.size == t.size }?.let { state = state.copy(lastScore = FrameDiff.score(it, t)); diagnostics("diff=%.1f".format(state.lastScore)) }
                    prev = t
                    state = state.copy(checks = state.checks + 1)
                    detector.update(t)?.let { state = state.copy(events = state.events + 1); onChange(it.toInt(), facing) }
                } catch (e: TimeoutCancellationException) { diagnostics("capture timed out") }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { diagnostics("capture failed: ${e.message}") }
                delay(periodS * 1000L)
            }
        }
    }

    fun stop() { job?.cancel(); job = null }

    companion object { const val DEFAULT_THRESHOLD = 14.0 }
}
