package com.rishitgoklani.hermes_front_mobile.testing

import com.rishitgoklani.hermes_front_mobile.domain.CameraFacing
import com.rishitgoklani.hermes_front_mobile.domain.vision.CodeScanner
import com.rishitgoklani.hermes_front_mobile.domain.vision.FrameSink
import com.rishitgoklani.hermes_front_mobile.domain.vision.FrameThumbnailer
import com.rishitgoklani.hermes_front_mobile.domain.vision.ImageTextReader
import com.rishitgoklani.hermes_front_mobile.domain.vision.ObjectRecognizer
import com.rishitgoklani.hermes_front_mobile.domain.vision.ObjectScene
import com.rishitgoklani.hermes_front_mobile.domain.vision.RecordedClip
import com.rishitgoklani.hermes_front_mobile.domain.vision.ScannedCode
import com.rishitgoklani.hermes_front_mobile.domain.vision.TextRead
import com.rishitgoklani.hermes_front_mobile.domain.vision.VideoRecorderPort

class FakeTextReader(var result: TextRead = TextRead("", emptyList())) : ImageTextReader {
    val inputs = mutableListOf<ByteArray>()
    override suspend fun read(jpeg: ByteArray): TextRead { inputs += jpeg; return result }
}

/** Returns the next entry of [perCall] per scan (the last repeats). */
class FakeCodeScanner(private val perCall: List<List<ScannedCode>> = listOf(emptyList())) : CodeScanner {
    var calls = 0
    override suspend fun scan(jpeg: ByteArray): List<ScannedCode> = perCall[minOf(calls++, perCall.lastIndex)]
}

class FakeObjectRecognizer(var scene: ObjectScene = ObjectScene(emptyList(), emptyList())) : ObjectRecognizer {
    override suspend fun recognize(jpeg: ByteArray): ObjectScene = scene
}

class FakeFrameSink : FrameSink {
    val saved = mutableListOf<Triple<String, String, ByteArray>>()
    override suspend fun save(session: String, name: String, jpeg: ByteArray): String { saved += Triple(session, name, jpeg); return "/fake/$session/$name" }
}

class FakeVideoRecorder(private val failure: Throwable? = null) : VideoRecorderPort {
    data class Request(val facing: CameraFacing, val durationMs: Long, val audio: Boolean, val session: String)
    val requests = mutableListOf<Request>()
    override suspend fun record(facing: CameraFacing, durationMs: Long, withAudio: Boolean, session: String): RecordedClip {
        requests += Request(facing, durationMs, withAudio, session); failure?.let { throw it }
        return RecordedClip("/fake/$session.mp4", 2048, durationMs)
    }
}

/** Identity thumbnailer: tests feed ready-made "thumbnails" as camera frames. */
class FakeThumbnailer : FrameThumbnailer { override fun thumb(jpeg: ByteArray): ByteArray = jpeg }
