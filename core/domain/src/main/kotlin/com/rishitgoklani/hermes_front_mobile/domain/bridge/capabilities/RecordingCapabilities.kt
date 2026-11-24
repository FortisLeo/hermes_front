package com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.sense.VisualWatcher
import com.rishitgoklani.hermes_front_mobile.domain.vision.TimeLapseRecorder
import com.rishitgoklani.hermes_front_mobile.domain.vision.VideoRecorderPort
import com.rishitgoklani.hermes_front_mobile.domain.vision.facingArg
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.contentOrNull
private fun JsonObject.int(k: String) = this[k]?.jsonPrimitive?.intOrNull

private const val FACING_PROP = """"facing":{"type":"string","enum":["front","back"],"description":"Default front (faces the user)"}"""

/** #116 Visual change watcher on the front (default) or back camera. */
fun visualWatchCapability(w: VisualWatcher) = Capability(
    "visual_watch", "Watch a camera for scene changes (something appears, moves or disappears): action=start|stop|status, period_s (default 60, min 10), facing=front|back (default front), sensitivity=low|normal|high. Changes fire routine event 'visual_change'; status reports checks, events and last diff score.",
    """{"type":"object","properties":{"action":{"type":"string","enum":["start","stop","status"]},"period_s":{"type":"integer"},$FACING_PROP,"sensitivity":{"type":"string","enum":["low","normal","high"]}},"required":["action"]}""",
) { a ->
    when (a.str("action")) {
        "start" -> {
            val facing = a.facingArg()
            val threshold = when (a.str("sensitivity")) { "high" -> 8.0; "low" -> 24.0; else -> VisualWatcher.DEFAULT_THRESHOLD }
            w.start(facing, (a.int("period_s") ?: 60).coerceIn(10, 3600), threshold)
            ActionResult.completed("Watching the ${facing.name.lowercase()} camera for changes")
        }
        "stop" -> { w.stop(); ActionResult.completed("Stopped") }
        "status" -> w.status.let { s ->
            ActionResult.completed(if (s.running) "watching ${s.facing.name.lowercase()} camera" else "stopped", buildJsonObject {
                put("running", s.running); put("facing", s.facing.name.lowercase()); put("period_s", s.periodS); put("checks", s.checks); put("events", s.events)
                s.lastScore?.let { put("last_score", it) }
            })
        }
        else -> ActionResult.failed("action must be start, stop or status")
    }
}

/** #119 Time-lapse: periodic frames from the front (default) or back camera saved to a folder. */
fun timeLapseCapability(rec: TimeLapseRecorder, clock: () -> Long = System::currentTimeMillis) = Capability(
    "time_lapse", "Time-lapse: action=start|stop|status, facing=front|back (default front), interval_s (default 10, min 2), count (default 60, max 2000), name (folder). Frames f_0000.jpg... go to Deskbot/timelapse/<name>; make a video with `ffmpeg -framerate 10 -i f_%04d.jpg out.mp4` in Termux.",
    """{"type":"object","properties":{"action":{"type":"string","enum":["start","stop","status"]},$FACING_PROP,"interval_s":{"type":"integer"},"count":{"type":"integer"},"name":{"type":"string"}},"required":["action"]}""",
) { a ->
    when (a.str("action")) {
        "start" -> {
            val facing = a.facingArg()
            val interval = (a.int("interval_s") ?: 10).coerceIn(2, 3600); val count = (a.int("count") ?: 60).coerceIn(1, 2000)
            val name = (a.str("name") ?: "lapse_${clock()}").filter { it.isLetterOrDigit() || it == '_' || it == '-' }.ifEmpty { "lapse_${clock()}" }
            rec.start(facing, interval, count, name)
            ActionResult.completed("Time-lapse started: $count frames every ${interval}s from the ${facing.name.lowercase()} camera", buildJsonObject { put("name", name); put("interval_s", interval); put("count", count); put("facing", facing.name.lowercase()) })
        }
        "stop" -> { val s = rec.status; rec.stop(); ActionResult.completed("Stopped after ${s.taken} frames", buildJsonObject { put("name", s.session); put("taken", s.taken) }) }
        "status" -> rec.status.let { s ->
            ActionResult.completed(if (s.running) "${s.taken}/${s.total} frames" else "idle (${s.taken} frames in ${s.session.ifEmpty { "-" }})", buildJsonObject {
                put("running", s.running); put("name", s.session); put("taken", s.taken); put("total", s.total); s.lastError?.let { put("last_error", it) }
            })
        }
        else -> ActionResult.failed("action must be start, stop or status")
    }
}

/** #119 Video clip from the front (default) or back camera. */
fun videoRecordCapability(recorder: VideoRecorderPort, clock: () -> Long = System::currentTimeMillis) = Capability(
    "video_record", "Record a video clip: facing=front|back (default front), duration_s (default 10, max 120), audio=true to include the microphone. Saved under Deskbot/videos; returns data.path.",
    """{"type":"object","properties":{$FACING_PROP,"duration_s":{"type":"integer"},"audio":{"type":"boolean"}}}""",
) { a ->
    val facing = a.facingArg()
    val seconds = (a.int("duration_s") ?: 10).coerceIn(1, 120)
    val audio = a.str("audio").equals("true", ignoreCase = true)
    val clip = recorder.record(facing, seconds * 1000L, audio, "clip_${clock()}")
    ActionResult.completed("Recorded ${clip.durationMs / 1000}s ${facing.name.lowercase()} video, ${clip.bytes / 1024} KB", buildJsonObject {
        put("path", clip.path); put("bytes", clip.bytes); put("duration_ms", clip.durationMs); put("facing", facing.name.lowercase()); put("audio", audio)
    })
}
