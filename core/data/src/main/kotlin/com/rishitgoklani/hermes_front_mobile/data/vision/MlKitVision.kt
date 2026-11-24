package com.rishitgoklani.hermes_front_mobile.data.vision

import android.graphics.BitmapFactory
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.rishitgoklani.hermes_front_mobile.domain.vision.DetectedObject
import com.rishitgoklani.hermes_front_mobile.domain.vision.ImageTextReader
import com.rishitgoklani.hermes_front_mobile.domain.vision.ObjectRecognizer
import com.rishitgoklani.hermes_front_mobile.domain.vision.ObjectScene
import com.rishitgoklani.hermes_front_mobile.domain.vision.TextBox
import com.rishitgoklani.hermes_front_mobile.domain.vision.TextRead
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }.addOnFailureListener { c.resumeWithException(it) }
}

private fun image(jpeg: ByteArray): InputImage {
    val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: error("could not decode frame")
    return InputImage.fromBitmap(bmp, 0)
}

/** On-device OCR (ML Kit Latin text recognition, no network). */
class MlKitTextReader : ImageTextReader {
    private val client by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    override suspend fun read(jpeg: ByteArray): TextRead {
        val t = client.process(image(jpeg)).await()
        val lines = t.textBlocks.flatMap { it.lines }.mapNotNull { l -> l.boundingBox?.let { TextBox(l.text, it.left, it.top, it.right, it.bottom) } }
        return TextRead(t.text, lines)
    }
}

/** On-device object detection (multiple objects, coarse category) plus whole-frame image labels (ML Kit). */
class MlKitObjectRecognizer : ObjectRecognizer {
    private val detector by lazy { ObjectDetection.getClient(ObjectDetectorOptions.Builder().setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE).enableMultipleObjects().enableClassification().build()) }
    private val labeler by lazy { ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.6f).build()) }
    override suspend fun recognize(jpeg: ByteArray): ObjectScene {
        val img = image(jpeg)
        val objects = detector.process(img).await().map { o -> val l = o.labels.maxByOrNull { it.confidence }; DetectedObject(l?.text ?: "object", l?.confidence ?: 0f) }
        val labels = labeler.process(img).await().map { DetectedObject(it.text, it.confidence) }.sortedByDescending { it.confidence }
        return ObjectScene(objects, labels)
    }
}
