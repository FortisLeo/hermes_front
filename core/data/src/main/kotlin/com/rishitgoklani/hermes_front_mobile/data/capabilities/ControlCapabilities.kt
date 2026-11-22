package com.rishitgoklani.hermes_front_mobile.data.capabilities

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.provider.Settings
import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

fun volumeCapability(context: Context) = Capability(
    "volume", "Get or set media volume as a percent (0-100). Omit level to just read it.",
    """{"type":"object","properties":{"level":{"type":"integer","minimum":0,"maximum":100}}}""",
) { args ->
    val audio = context.getSystemService(AudioManager::class.java)
    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    Percent.arg(args, "level")?.let { audio.setStreamVolume(AudioManager.STREAM_MUSIC, Percent.toRange(it, max), 0) }
    val now = Percent.fromRange(audio.getStreamVolume(AudioManager.STREAM_MUSIC), max)
    ActionResult.completed("Volume $now%", buildJsonObject { put("level", now) })
}

fun brightnessCapability(context: Context) = Capability(
    "brightness", "Get or set screen brightness as a percent (0-100). Omit level to just read it.",
    """{"type":"object","properties":{"level":{"type":"integer","minimum":0,"maximum":100}}}""",
) { args ->
    val resolver = context.contentResolver
    Percent.arg(args, "level")?.let {
        if (!Settings.System.canWrite(context)) return@Capability ActionResult.failed("WRITE_SETTINGS not granted")
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, Percent.toRange(it, 255, 1))
    }
    val now = Percent.fromRange(Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128), 255, 1)
    ActionResult.completed("Brightness $now%", buildJsonObject { put("level", now) })
}

fun torchCapability(context: Context) = Capability(
    "torch", "Turn the flashlight on or off.",
    """{"type":"object","properties":{"on":{"type":"boolean"}},"required":["on"]}""",
) { args ->
    val on = args["on"]?.jsonPrimitive?.booleanOrNull ?: return@Capability ActionResult.failed("on (boolean) is required")
    val manager = context.getSystemService(CameraManager::class.java)
    val id = manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        ?: return@Capability ActionResult.failed("No flash unit")
    manager.setTorchMode(id, on)
    ActionResult.completed(if (on) "Torch on" else "Torch off")
}
