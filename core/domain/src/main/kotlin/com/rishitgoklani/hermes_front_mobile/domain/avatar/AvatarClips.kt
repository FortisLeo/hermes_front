package com.rishitgoklani.hermes_front_mobile.domain.avatar

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** VRM Animation (.vrma) check: a GLB that carries the VRMC_vrm_animation extension. Size capped so a bad file cannot fill the phone. */
object VrmaFile {
    const val MAX_BYTES = 8 * 1024 * 1024
    fun check(glb: ByteArray): String? {
        if (glb.size > MAX_BYTES) return "clip larger than 8 MB"
        if (glb.size < 20 || String(glb, 0, 4, Charsets.US_ASCII) != "glTF") return "not a GLB/VRMA file"
        fun le(o: Int) = (glb[o].toInt() and 255) or (glb[o + 1].toInt() and 255 shl 8) or (glb[o + 2].toInt() and 255 shl 16) or (glb[o + 3].toInt() and 255 shl 24)
        val len = le(12); if (len <= 0 || 20 + len > glb.size) return "corrupt GLB header"
        val root = runCatching { Json.parseToJsonElement(String(glb, 20, len, Charsets.UTF_8)).jsonObject }.getOrNull() ?: return "unreadable clip metadata"
        val ext = root["extensions"] as? JsonObject
        return if (ext?.containsKey("VRMC_vrm_animation") == true) null else "no VRMC_vrm_animation extension: not a VRM Animation clip"
    }

    /** File-safe clip name from a path: letters, digits, dash, underscore only; no extension. */
    fun nameFrom(path: String): String = path.substringAfterLast('/').substringBeforeLast('.').filter { it.isLetterOrDigit() || it in "-_" }.take(40)
}

fun avatarImportClipCapability(stage: suspend (String) -> ByteArray?, install: suspend (String, ByteArray) -> Unit) = Capability(
    "avatar_import_clip", "Import a VRM Animation clip (.vrma, e.g. a dance) from a file path on the phone so avatar_clip can play it.",
    """{"type":"object","properties":{"path":{"type":"string"},"name":{"type":"string"}},"required":["path"]}""",
) { a ->
    val path = a["path"]?.jsonPrimitive?.contentOrNull ?: return@Capability ActionResult.failed("path required")
    val name = (a["name"]?.jsonPrimitive?.contentOrNull?.let { VrmaFile.nameFrom(it) } ?: VrmaFile.nameFrom(path)).ifEmpty { return@Capability ActionResult.failed("clip needs a name") }
    val bytes = stage(path) ?: return@Capability ActionResult.failed("cannot read $path")
    VrmaFile.check(bytes)?.let { return@Capability ActionResult.failed("Clip rejected: $it") }
    install(name, bytes); ActionResult.completed("Imported clip '$name'")
}

fun avatarClipCapability(avatar: AvatarController, clips: () -> List<String>) = Capability(
    "avatar_clip", "Play an imported VRM Animation clip on the avatar (action=play with name, stop, or list).",
    """{"type":"object","properties":{"action":{"type":"string","enum":["play","stop","list"]},"name":{"type":"string"}},"required":["action"]}""",
) { a ->
    when (a["action"]?.jsonPrimitive?.contentOrNull) {
        "list" -> ActionResult.completed(clips().joinToString().ifEmpty { "no clips imported" })
        "stop" -> { avatar.stopClip(); ActionResult.completed("Clip stopped") }
        "play" -> { val n = a["name"]?.jsonPrimitive?.contentOrNull.orEmpty(); if (n !in clips()) ActionResult.failed("unknown clip '$n' (have: ${clips().joinToString()})") else { avatar.playClip(n); ActionResult.completed("Playing $n") } }
        else -> ActionResult.failed("action must be play, stop or list")
    }
}
