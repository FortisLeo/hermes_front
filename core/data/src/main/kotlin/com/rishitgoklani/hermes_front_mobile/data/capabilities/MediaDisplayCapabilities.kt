package com.rishitgoklani.hermes_front_mobile.data.capabilities

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.AlarmClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object MediaKeys {
    fun code(action: String): Int? = when (action.lowercase().replace('-', '_')) {
        "play_pause", "toggle" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE; "play" -> KeyEvent.KEYCODE_MEDIA_PLAY; "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
        "next" -> KeyEvent.KEYCODE_MEDIA_NEXT; "previous", "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS; "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
        "rewind" -> KeyEvent.KEYCODE_MEDIA_REWIND; "forward", "fast_forward" -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD; else -> null
    }
    fun stream(name: String): Int? = when (name.lowercase()) {
        "music", "media" -> AudioManager.STREAM_MUSIC; "ring" -> AudioManager.STREAM_RING; "alarm" -> AudioManager.STREAM_ALARM
        "notification" -> AudioManager.STREAM_NOTIFICATION; "system" -> AudioManager.STREAM_SYSTEM; "call" -> AudioManager.STREAM_VOICE_CALL; else -> null
    }
}

fun mediaCapabilities(context: Context): List<Capability> {
    val audio = context.getSystemService(AudioManager::class.java)
    return listOf(
        Capability("media_key", "Control playback in whichever app is playing: play_pause, play, pause, next, previous, stop, rewind, forward.",
            """{"type":"object","properties":{"action":{"type":"string"}},"required":["action"]}""") { a ->
            val code = MediaKeys.code(a.str("action").orEmpty()) ?: return@Capability ActionResult.failed("unknown action")
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)); audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
            ActionResult.completed(a.str("action").orEmpty())
        },
        Capability("stream_volume", "Get/set volume percent for a stream: music, ring, alarm, notification, system, call. mute=true/false toggles mute.",
            """{"type":"object","properties":{"stream":{"type":"string"},"level":{"type":"integer"},"mute":{"type":"boolean"}},"required":["stream"]}""") { a ->
            val s = MediaKeys.stream(a.str("stream").orEmpty()) ?: return@Capability ActionResult.failed("unknown stream")
            val max = audio.getStreamMaxVolume(s)
            a.int("level")?.let { audio.setStreamVolume(s, Percent.toRange(it, max), 0) }
            a.bool("mute")?.let { audio.adjustStreamVolume(s, if (it) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE, 0) }
            val now = Percent.fromRange(audio.getStreamVolume(s), max)
            ActionResult.completed("${a.str("stream")} $now%", buildJsonObject { put("level", now); put("muted", audio.isStreamMute(s)) })
        },
        Capability("mic_mute", "Mute or unmute the microphone (mute=true/false).", """{"type":"object","properties":{"mute":{"type":"boolean"}},"required":["mute"]}""") { a ->
            audio.isMicrophoneMute = a.bool("mute") ?: false; ActionResult.completed("Mic muted=${audio.isMicrophoneMute}")
        },
    )
}

fun hapticsCapability(context: Context) = Capability("vibrate", "Vibrate for duration_ms (default 200) or a pattern like [0,100,50,100].",
    """{"type":"object","properties":{"duration_ms":{"type":"integer"},"pattern":{"type":"array","items":{"type":"integer"}}}}""") { a ->
    val vib = context.getSystemService(VibratorManager::class.java).defaultVibrator
    val pattern = (a["pattern"] as? kotlinx.serialization.json.JsonArray)?.map { (it as kotlinx.serialization.json.JsonPrimitive).content.toLong() }?.toLongArray()
    vib.vibrate(if (pattern != null && pattern.isNotEmpty()) VibrationEffect.createWaveform(pattern, -1) else VibrationEffect.createOneShot((a.long("duration_ms") ?: 200).coerceIn(10, 5000), VibrationEffect.DEFAULT_AMPLITUDE))
    ActionResult.completed("Vibrating")
}

fun alarmCapability(context: Context) = Capability("alarm_set", "Set a clock alarm at hour:minute (24h) with optional label and repeat days (1=Sun..7=Sat).",
    """{"type":"object","properties":{"hour":{"type":"integer"},"minute":{"type":"integer"},"label":{"type":"string"},"days":{"type":"array","items":{"type":"integer"}}},"required":["hour","minute"]}""") { a ->
    val h = a.int("hour") ?: return@Capability ActionResult.failed("hour required"); val m = a.int("minute") ?: return@Capability ActionResult.failed("minute required")
    if (h !in 0..23 || m !in 0..59) return@Capability ActionResult.failed("invalid time")
    val intent = Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, h).putExtra(AlarmClock.EXTRA_MINUTES, m).putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        .putExtra(AlarmClock.EXTRA_MESSAGE, a.str("label") ?: "Deskbot").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    (a["days"] as? kotlinx.serialization.json.JsonArray)?.let { arr -> intent.putIntegerArrayListExtra(AlarmClock.EXTRA_DAYS, ArrayList(arr.map { (it as kotlinx.serialization.json.JsonPrimitive).content.toInt() })) }
    context.startActivity(intent); ActionResult.completed("Alarm set for %02d:%02d".format(h, m))
}

/** Floating overlays (text caption, dimmer). Needs SYSTEM_ALERT_WINDOW. All view work happens on the main thread. */
class OverlayManager(private val context: Context) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private var text: View? = null
    private var dim: View? = null

    private fun params(gravity: Int, touchable: Boolean = false) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT,
    ).apply { this.gravity = gravity }

    suspend fun showText(message: String, durationMs: Long, top: Boolean) {
        withContext(Dispatchers.Main) {
            clearText()
            val tv = TextView(context).apply { this.text = message; textSize = 22f; setTextColor(Color.WHITE); setBackgroundColor(0xCC000000.toInt()); setPadding(32, 24, 32, 24); gravity = Gravity.CENTER }
            wm.addView(tv, params(if (top) Gravity.TOP else Gravity.BOTTOM)); text = tv
        }
        if (durationMs > 0) { delay(durationMs); withContext(Dispatchers.Main) { clearText() } }
    }

    suspend fun dim(percent: Int, tint: Int = Color.BLACK) = withContext(Dispatchers.Main) {
        dim?.let { runCatching { wm.removeView(it) } }; dim = null
        if (percent > 0) {
            val v = View(context).apply { setBackgroundColor((tint and 0x00FFFFFF) or ((percent.coerceIn(0, 90) * 255 / 100) shl 24)) }
            wm.addView(v, WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT))
            dim = v
        }
    }

    private fun clearText() { text?.let { runCatching { wm.removeView(it) } }; text = null }
    suspend fun clearAll() = withContext(Dispatchers.Main) { clearText(); dim?.let { runCatching { wm.removeView(it) } }; dim = null }
}

fun overlayCapabilities(context: Context, overlay: OverlayManager): List<Capability> = listOf(
    Capability("overlay_text", "Show a caption over any app (click-through). duration_ms 0 = until overlay_clear.",
        """{"type":"object","properties":{"text":{"type":"string"},"duration_ms":{"type":"integer"},"position":{"type":"string","enum":["top","bottom"]}},"required":["text"]}""") { a ->
        if (!android.provider.Settings.canDrawOverlays(context)) return@Capability ActionResult.failed("Overlay permission not granted")
        overlay.showText(a.str("text").orEmpty(), a.long("duration_ms") ?: 5000, a.str("position") != "bottom"); ActionResult.completed("Shown")
    },
    Capability("screen_dim", "Dim the screen with a dark overlay (percent 0-90; 0 removes). Optional tint hex like 'FF8800' for a warm night filter.",
        """{"type":"object","properties":{"percent":{"type":"integer"},"tint":{"type":"string"}},"required":["percent"]}""") { a ->
        if (!android.provider.Settings.canDrawOverlays(context)) return@Capability ActionResult.failed("Overlay permission not granted")
        val tint = a.str("tint")?.let { runCatching { Color.parseColor("#" + it.trimStart('#')) }.getOrNull() } ?: Color.BLACK
        overlay.dim(a.int("percent") ?: 0, tint); ActionResult.completed("Dim ${a.int("percent")}%")
    },
    Capability("overlay_clear", "Remove all Deskbot overlays.") { overlay.clearAll(); ActionResult.completed("Cleared") },
)
