package com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.vision.CodeScanner
import com.rishitgoklani.hermes_front_mobile.domain.vision.FrameResult
import com.rishitgoklani.hermes_front_mobile.domain.vision.FrameSink
import com.rishitgoklani.hermes_front_mobile.domain.vision.ImageTextReader
import com.rishitgoklani.hermes_front_mobile.domain.vision.ObjectRecognizer
import com.rishitgoklani.hermes_front_mobile.domain.vision.TableExtractor
import com.rishitgoklani.hermes_front_mobile.domain.vision.VisionFrames
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val SOURCE_PROPS = """"source":{"type":"string","enum":["screen","camera"]},"facing":{"type":"string","enum":["front","back"],"description":"Camera only; default front (faces the user)"}"""
private const val MAX_TEXT = 8000

private fun JsonObject.int(k: String) = this[k]?.jsonPrimitive?.intOrNull
private fun JsonObject.flag(k: String) = this[k]?.jsonPrimitive?.contentOrNull.equals("true", ignoreCase = true)

/** #43 OCR of text in the screen or a camera frame. Works where the accessibility tree is empty (games, images, video frames). */
fun ocrCapability(frames: VisionFrames, reader: ImageTextReader) = Capability(
    "ocr", "Read the text in the current screen (default) or a camera frame (source=camera, facing=front|back) with on-device OCR. Returns data.text and line boxes.",
    """{"type":"object","properties":{$SOURCE_PROPS}}""",
) { a ->
    when (val f = frames.grab(a, defaultSource = "screen")) {
        is FrameResult.Err -> ActionResult.failed(f.message)
        is FrameResult.Ok -> {
            val r = reader.read(f.jpeg)
            if (r.text.isBlank()) ActionResult.completed("No text found on the ${f.origin}", buildJsonObject { put("text", ""); put("lines", 0); put("origin", f.origin) })
            else ActionResult.completed(r.text.take(MAX_TEXT), buildJsonObject {
                put("text", r.text.take(MAX_TEXT)); put("lines", r.lines.size); put("origin", f.origin)
                put("boxes", buildJsonArray { r.lines.take(200).forEach { add(buildJsonObject { put("text", it.text); put("l", it.left); put("t", it.top); put("r", it.right); put("b", it.bottom) }) } })
            })
        }
    }
}

/** #47 Table on screen or in front of the camera to CSV. For charts it returns the axis/label text; add include_image=true to look at the chart itself. */
fun tableToCsvCapability(frames: VisionFrames, reader: ImageTextReader, encode: (ByteArray) -> String) = Capability(
    "table_to_csv", "Read a table on the screen (default) or camera and return it as CSV in data.csv (rows and columns rebuilt from text positions). For charts, data.text holds labels/axis values; set include_image=true to also get the picture in data.image_base64 to describe it.",
    """{"type":"object","properties":{$SOURCE_PROPS,"include_image":{"type":"boolean"}}}""",
) { a ->
    when (val f = frames.grab(a, defaultSource = "screen")) {
        is FrameResult.Err -> ActionResult.failed(f.message)
        is FrameResult.Ok -> {
            val r = reader.read(f.jpeg)
            val rows = TableExtractor.rows(r.lines)
            val csv = TableExtractor.csv(rows)
            ActionResult.completed(if (rows.isEmpty()) "No text found on the ${f.origin}" else "${rows.size} rows, ${rows.maxOf { it.size }} columns", buildJsonObject {
                put("csv", csv.take(MAX_TEXT)); put("rows", rows.size); put("columns", rows.maxOfOrNull { it.size } ?: 0); put("text", r.text.take(MAX_TEXT)); put("origin", f.origin)
                if (a.flag("include_image")) { put("image_base64", encode(f.jpeg)); put("mime", "image/jpeg") }
            })
        }
    }
}

/** #114 QR / barcodes. Retries a few frames (people are slow to hold a code still) before giving up. */
fun scanCodeCapability(frames: VisionFrames, scanner: CodeScanner, retryDelayMs: Long = 400) = Capability(
    "scan_code", "Scan QR codes and barcodes from the camera (default, facing=front|back) or the screen. Tries up to attempts (default 3, max 6) frames. Returns data.codes [{format,text,kind}].",
    """{"type":"object","properties":{$SOURCE_PROPS,"attempts":{"type":"integer"}}}""",
) { a ->
    val attempts = (a.int("attempts") ?: 3).coerceIn(1, 6)
    var origin = ""
    for (i in 1..attempts) {
        when (val f = frames.grab(a, defaultSource = "camera")) {
            is FrameResult.Err -> return@Capability ActionResult.failed(f.message)
            is FrameResult.Ok -> {
                origin = f.origin
                val codes = scanner.scan(f.jpeg)
                if (codes.isNotEmpty()) return@Capability ActionResult.completed(
                    codes.joinToString("; ") { "${it.kind}: ${it.text.take(200)}" },
                    buildJsonObject { put("codes", JsonArray(codes.map { c -> buildJsonObject { put("format", c.format); put("text", c.text); put("kind", c.kind) } })); put("attempts", i); put("origin", origin) })
            }
        }
        if (i < attempts) delay(retryDelayMs)
    }
    ActionResult.completed("No code found on the $origin", buildJsonObject { put("codes", JsonArray(emptyList())); put("attempts", attempts); put("origin", origin) })
}

/** #114 Document scanner: high-resolution frame, OCR text, and the image saved to the scans folder. */
fun scanDocumentCapability(frames: VisionFrames, reader: ImageTextReader, sink: FrameSink, clock: () -> Long = System::currentTimeMillis) = Capability(
    "scan_document", "Scan a document: capture a high-resolution frame from the camera (default front; use facing=back for a page on the desk) or screen, OCR it, save the JPEG. Returns data.text and data.path.",
    """{"type":"object","properties":{$SOURCE_PROPS}}""",
) { a ->
    when (val f = frames.grab(a, defaultSource = "camera")) {
        is FrameResult.Err -> ActionResult.failed(f.message)
        is FrameResult.Ok -> {
            val r = reader.read(f.jpeg)
            val path = sink.save("scans", "scan_${clock()}.jpg", f.jpeg)
            ActionResult.completed(if (r.text.isBlank()) "Saved scan, no text found" else "Saved scan: ${r.text.take(160)}",
                buildJsonObject { put("text", r.text.take(MAX_TEXT)); put("path", path); put("lines", r.lines.size); put("origin", f.origin) })
        }
    }
}

/** #115 Count things: detected objects grouped by label, plus whole-frame labels (plants, devices, furniture). */
fun countObjectsCapability(frames: VisionFrames, recognizer: ObjectRecognizer) = Capability(
    "count_objects", "Recognise and count objects in a camera frame (default front, facing=back for the desk) or the screen. Optional label filters the count. Returns data.total, data.counts, data.labels.",
    """{"type":"object","properties":{$SOURCE_PROPS,"label":{"type":"string"}}}""",
) { a ->
    when (val f = frames.grab(a, defaultSource = "camera")) {
        is FrameResult.Err -> ActionResult.failed(f.message)
        is FrameResult.Ok -> {
            val scene = recognizer.recognize(f.jpeg)
            val wanted = a["label"]?.jsonPrimitive?.contentOrNull?.lowercase()
            val counts = scene.objects.groupingBy { it.label.ifBlank { "object" }.lowercase() }.eachCount().filterKeys { wanted == null || it.contains(wanted) }
            val labels = scene.labels.filter { wanted == null || it.label.lowercase().contains(wanted) }
            val total = if (wanted == null) scene.objects.size else counts.values.sum()
            val spoken = when {
                counts.isNotEmpty() -> "I count $total: " + counts.entries.joinToString(", ") { "${it.value} ${it.key}" }
                labels.isNotEmpty() -> "No separate objects, but I see " + labels.take(4).joinToString(", ") { it.label }
                else -> "I could not recognise any objects"
            }
            ActionResult.completed(spoken, buildJsonObject {
                put("total", total); put("origin", f.origin)
                put("counts", buildJsonObject { counts.forEach { (k, v) -> put(k, v) } })
                put("labels", buildJsonArray { labels.take(10).forEach { add(JsonPrimitive("${it.label} (${(it.confidence * 100).toInt()}%)")) } })
            })
        }
    }
}
