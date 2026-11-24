package com.rishitgoklani.hermes_front_mobile.domain.vision

import com.rishitgoklani.hermes_front_mobile.domain.CameraFacing

/** A recognised run of text with its pixel box. */
data class TextBox(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val height get() = (bottom - top).coerceAtLeast(1)
    val centerY get() = (top + bottom) / 2.0
}

data class TextRead(val text: String, val lines: List<TextBox>)

/** Port: on-device OCR of a JPEG. */
interface ImageTextReader { suspend fun read(jpeg: ByteArray): TextRead }

data class ScannedCode(val format: String, val text: String) {
    /** url, wifi, email, phone, sms, geo or text: lets the speaker say "a link" instead of reading raw payloads. */
    val kind: String get() = CodeKinds.classify(text)
}

/** Port: decode every QR / barcode found in a JPEG. */
interface CodeScanner { suspend fun scan(jpeg: ByteArray): List<ScannedCode> }

data class DetectedObject(val label: String, val confidence: Float)

/** [objects] are individually located things (label may be generic); [labels] are whole-frame guesses (plant, desk, cup...). */
data class ObjectScene(val objects: List<DetectedObject>, val labels: List<DetectedObject>)

/** Port: on-device object detection and labelling. */
interface ObjectRecognizer { suspend fun recognize(jpeg: ByteArray): ObjectScene }

/** Port: persist a frame (time-lapse, scans). Returns where it went. */
interface FrameSink { suspend fun save(session: String, name: String, jpeg: ByteArray): String }

data class RecordedClip(val path: String, val bytes: Long, val durationMs: Long)

/** Port: record a video clip from the phone camera. */
interface VideoRecorderPort {
    suspend fun record(facing: CameraFacing, durationMs: Long, withAudio: Boolean, session: String): RecordedClip
}

/** Port: shrink a JPEG to a small grayscale thumbnail (same length for same-sized input) for frame differencing. */
fun interface FrameThumbnailer { fun thumb(jpeg: ByteArray): ByteArray }

object CodeKinds {
    fun classify(t: String): String = when {
        t.startsWith("http://", true) || t.startsWith("https://", true) -> "url"
        t.startsWith("WIFI:", true) -> "wifi"
        t.startsWith("mailto:", true) -> "email"
        t.startsWith("tel:", true) -> "phone"
        t.startsWith("smsto:", true) || t.startsWith("sms:", true) -> "sms"
        t.startsWith("geo:", true) -> "geo"
        else -> "text"
    }
}
