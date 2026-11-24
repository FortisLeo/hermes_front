package com.rishitgoklani.hermes_front_mobile.domain.vision

import com.rishitgoklani.hermes_front_mobile.domain.CameraFacing
import com.rishitgoklani.hermes_front_mobile.domain.CameraPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** #119 Time-lapse: one frame every [intervalS] seconds from the chosen camera into a [FrameSink] session folder, until [count] frames or stop. */
class TimeLapseRecorder(private val camera: CameraPort, private val sink: FrameSink, private val scope: CoroutineScope) {
    data class Status(val running: Boolean, val session: String, val facing: CameraFacing, val taken: Int, val total: Int, val lastError: String?)

    private var job: Job? = null
    private var state = Status(false, "", CameraFacing.Front, 0, 0, null)
    val status get() = state.copy(running = job?.isActive == true)

    fun start(facing: CameraFacing, intervalS: Int, count: Int, session: String) {
        stop()
        state = Status(true, session, facing, 0, count, null)
        job = scope.launch {
            try {
                for (i in 0 until count) {
                    try {
                        val jpeg = withTimeout(15_000) { camera.capture(facing, FRAME_BYTES, FRAME_LONG_SIDE) }
                        sink.save(session, "f_%04d.jpg".format(i), jpeg)
                        state = state.copy(taken = i + 1, lastError = null)
                    } catch (e: TimeoutCancellationException) { state = state.copy(lastError = "capture timed out") }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { state = state.copy(lastError = e.message ?: e::class.simpleName) }
                    if (i < count - 1) delay(intervalS * 1000L)
                }
            } finally { state = state.copy(running = false) }
        }
    }

    fun stop() { job?.cancel(); job = null }

    companion object { const val FRAME_BYTES = 400_000; const val FRAME_LONG_SIDE = 1600 }
}
