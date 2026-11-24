package com.rishitgoklani.hermes_front_mobile.data.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.rishitgoklani.hermes_front_mobile.domain.CameraFacing
import com.rishitgoklani.hermes_front_mobile.domain.CameraPort
import com.rishitgoklani.hermes_front_mobile.domain.QualityPlanner
import android.os.Environment
import com.rishitgoklani.hermes_front_mobile.domain.vision.RecordedClip
import com.rishitgoklani.hermes_front_mobile.domain.vision.VideoRecorderPort
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** CameraX adapter: binds briefly to a private lifecycle, grabs one frame, releases the camera, shrinks the JPEG. */
class CameraXCameraPort(private val context: Context, private val longSide: Int = 1024) : CameraPort, VideoRecorderPort {
    private val mutex = Mutex() // one capture at a time; the camera is a shared resource

    override suspend fun capture(facing: CameraFacing, maxBytes: Int, longSidePx: Int?): ByteArray = mutex.withLock {
        val frame = withContext(Dispatchers.Main) { takeFrame(facing) }
        withContext(Dispatchers.Default) { shrink(frame, maxBytes, longSidePx ?: longSide) }
    }

    /** Records [durationMs] of video from the chosen camera into Deskbot/videos (or app files if shared storage is not writable). Holds the same lock as stills. */
    override suspend fun record(facing: CameraFacing, durationMs: Long, withAudio: Boolean, session: String): RecordedClip = mutex.withLock {
        val dir = File(Environment.getExternalStorageDirectory(), "Deskbot/videos").let { if (it.isDirectory || it.mkdirs()) it else File(context.filesDir, "videos").also { d -> d.mkdirs() } }
        val file = File(dir, "$session.mp4")
        withContext(Dispatchers.Main) {
            val provider = awaitProvider()
            val owner = SimpleOwner().also { it.resume() }
            val recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))).build()
            val capture = VideoCapture.withOutput(recorder)
            val selector = if (facing == CameraFacing.Front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            try {
                provider.unbindAll(); provider.bindToLifecycle(owner, selector, capture)
                val finished = CompletableDeferred<VideoRecordEvent.Finalize>()
                var pending = recorder.prepareRecording(context, FileOutputOptions.Builder(file).build())
                if (withAudio) pending = pending.withAudioEnabled()
                val active = pending.start(ContextCompat.getMainExecutor(context)) { ev -> if (ev is VideoRecordEvent.Finalize) finished.complete(ev) }
                try { delay(durationMs) } finally { active.stop() }
                val end = finished.await()
                if (end.hasError() && end.error != VideoRecordEvent.Finalize.ERROR_NONE && !file.isFile) error("video recording failed (error ${end.error})")
            } finally { provider.unbindAll(); owner.destroy() }
        }
        RecordedClip(file.path, file.length(), durationMs)
    }

    private suspend fun takeFrame(facing: CameraFacing): Bitmap {
        val provider = awaitProvider()
        val owner = SimpleOwner().also { it.resume() }
        val imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
        val selector = if (facing == CameraFacing.Front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        try {
            provider.unbindAll()
            provider.bindToLifecycle(owner, selector, imageCapture)
            // Let auto-exposure settle; the first frame after open is often dark.
            kotlinx.coroutines.delay(700)
            return suspendCancellableCoroutine { cont ->
                imageCapture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bmp = image.use { img ->
                            val raw = img.toBitmap()
                            val rot = img.imageInfo.rotationDegrees
                            if (rot == 0) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rot.toFloat()) }, true)
                        }
                        cont.resume(bmp)
                    }
                    override fun onError(e: ImageCaptureException) = cont.resumeWithException(e)
                })
            }
        } finally {
            provider.unbindAll(); owner.destroy()
        }
    }

    private suspend fun awaitProvider(): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
        val f = ProcessCameraProvider.getInstance(context)
        f.addListener({ runCatching { f.get() }.fold(cont::resume, cont::resumeWithException) }, ContextCompat.getMainExecutor(context))
    }

    private fun shrink(bitmap: Bitmap, maxBytes: Int, longSide: Int): ByteArray {
        val scale = QualityPlanner.scaleFor(bitmap.width, bitmap.height, longSide)
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
        fun jpeg(q: Int) = ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, q, it) }.toByteArray()
        val q = QualityPlanner.pick(maxBytes) { jpeg(it).size }
        return jpeg(q)
    }

    private class SimpleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override fun getLifecycle(): Lifecycle = registry
        fun resume() { registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE); registry.handleLifecycleEvent(Lifecycle.Event.ON_START); registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME) }
        fun destroy() { registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE); registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP); registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY) }
    }
}
