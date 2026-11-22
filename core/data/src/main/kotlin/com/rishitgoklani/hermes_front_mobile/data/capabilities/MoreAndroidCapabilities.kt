package com.rishitgoklani.hermes_front_mobile.data.capabilities

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.net.Uri
import android.provider.MediaStore
import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pure: which extras/URI an intent_start request may carry. Only http(s), tel/mailto/geo/sms drafts, market and intent targets for installed apps; never file:// or content:// from a caller. */
object IntentRules {
    private val allowedSchemes = setOf("http", "https", "geo", "tel", "mailto", "sms", "smsto", "market", "google.navigation", "whatsapp", "tg", "spotify", "vnd.youtube", "package")
    fun uriProblem(uri: String?): String? { if (uri.isNullOrBlank()) return null; val scheme = runCatching { Uri.parse(uri).scheme?.lowercase() }.getOrNull() ?: return "invalid uri"; return if (scheme in allowedSchemes) null else "scheme '$scheme' not allowed" }
    fun actionAllowed(a: String): Boolean = a.startsWith("android.intent.action.") || a.startsWith("android.settings.") || a == "android.media.action.IMAGE_CAPTURE" || a == "android.media.action.VIDEO_CAPTURE" || a.startsWith("android.provider.")
}

fun intentCapability(context: Context) = Capability("intent_start",
    "Start any Android intent: action (android.intent.action.VIEW/SEND/SENDTO/DIAL, android.settings.*, …), uri, mime type, package, extras {key:string}. Covers deep links, share to a named app, mailto drafts, navigation, settings pages.",
    """{"type":"object","properties":{"action":{"type":"string"},"uri":{"type":"string"},"type":{"type":"string"},"package":{"type":"string"},"extras":{"type":"object"}},"required":["action"]}""") { a ->
    val action = a.str("action").orEmpty(); if (!IntentRules.actionAllowed(action)) return@Capability ActionResult(ActionResult.DENIED, "action not allowed")
    IntentRules.uriProblem(a.str("uri"))?.let { return@Capability ActionResult(ActionResult.DENIED, it) }
    val i = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val uri = a.str("uri")?.let { Uri.parse(it) }; val type = a.str("type")
    if (uri != null && type != null) i.setDataAndType(uri, type) else if (uri != null) i.data = uri else if (type != null) i.type = type
    a.str("package")?.let { i.setPackage(it) }
    (a["extras"] as? JsonObject)?.forEach { (k, v) -> i.putExtra(k, (v as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()) }
    if (i.resolveActivity(context.packageManager) == null) return@Capability ActionResult.failed("no app can handle this intent")
    context.startActivity(i); ActionResult.completed("Started")
}

fun mediaSearchCapability(context: Context) = Capability("media_search",
    "Search photos/videos/audio on the phone by name text and/or age: kind=image|video|audio, query (file name contains), since_days. Returns path, size, date.",
    """{"type":"object","properties":{"kind":{"type":"string","enum":["image","video","audio"]},"query":{"type":"string"},"since_days":{"type":"integer"},"limit":{"type":"integer"}}}""") { a ->
    val uri = when (a.str("kind") ?: "image") { "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI; "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI; else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI }
    val sel = mutableListOf<String>(); val args = mutableListOf<String>()
    a.str("query")?.takeIf { it.isNotBlank() }?.let { sel += "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"; args += "%$it%" }
    a.long("since_days")?.let { sel += "${MediaStore.MediaColumns.DATE_ADDED} >= ?"; args += (System.currentTimeMillis() / 1000 - it * 86400).toString() }
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US); val rows = mutableListOf<String>()
    runCatching {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_ADDED), sel.joinToString(" AND ").ifEmpty { null }, args.toTypedArray().ifEmpty { null }, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { c ->
            while (c.moveToNext() && rows.size < (a.int("limit") ?: 20).coerceIn(1, 100)) rows += "${c.getString(0)}  ${c.getLong(1) / 1024} KB  ${fmt.format(Date(c.getLong(2) * 1000))}"
        }
    }.onFailure { return@Capability ActionResult.failed("media query failed: ${it.message}") }
    ActionResult.completed(if (rows.isEmpty()) "No matches" else rows.joinToString("\n"), buildJsonObject { put("count", rows.size) })
}

@SuppressLint("MissingPermission")
fun audioRecordCapability(context: Context, scope: CoroutineScope) = Capability("audio_record",
    "Record microphone audio to an .m4a in Recordings for seconds (1-600, default 10); returns the file path. Blocks until finished.",
    """{"type":"object","properties":{"seconds":{"type":"integer"},"name":{"type":"string"}}}""") { a ->
    val secs = (a.int("seconds") ?: 10).coerceIn(1, 600)
    val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC), "DeskbotRecordings").also { it.mkdirs() }
    val file = File(dir, (a.str("name")?.replace(Regex("[^A-Za-z0-9_-]"), "_") ?: "rec_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())) + ".m4a")
    withContext(Dispatchers.Main) {
        val rec = MediaRecorder(context).apply { setAudioSource(MediaRecorder.AudioSource.MIC); setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); setAudioEncoder(MediaRecorder.AudioEncoder.AAC); setOutputFile(file.path); prepare(); start() }
        try { delay(secs * 1000L) } finally { runCatching { rec.stop() }; rec.release() }
    }
    if (file.length() > 0) ActionResult.completed("Recorded ${file.length() / 1024} KB", buildJsonObject { put("path", file.path); put("bytes", file.length()) }) else ActionResult.failed("empty recording")
}

fun audioRouteCapability(context: Context) = Capability("audio_route",
    "List or choose the audio output: action=list|speaker|bluetooth|wired|default. (Android 12+ communication device routing.)",
    """{"type":"object","properties":{"action":{"type":"string","enum":["list","speaker","bluetooth","wired","default"]}},"required":["action"]}""") { a ->
    val am = context.getSystemService(AudioManager::class.java); val devs = am.availableCommunicationDevices
    fun name(d: AudioDeviceInfo) = when (d.type) { AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker"; AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> "bluetooth"; AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET -> "wired"; AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "earpiece"; else -> "type${d.type}" }
    when (val act = a.str("action")) {
        "list" -> ActionResult.completed(devs.joinToString("\n") { "${name(it)}: ${it.productName}" }.ifEmpty { "none" }, buildJsonObject { put("devices", buildJsonArray { devs.forEach { add(kotlinx.serialization.json.JsonPrimitive(name(it))) } }); put("current", am.communicationDevice?.let(::name) ?: "default") })
        "default" -> { am.clearCommunicationDevice(); ActionResult.completed("Default routing") }
        "speaker", "bluetooth", "wired" -> { val d = devs.firstOrNull { name(it) == act } ?: return@Capability ActionResult.failed("no $act device available"); if (am.setCommunicationDevice(d)) ActionResult.completed("Routed to $act") else ActionResult.failed("route refused") }
        else -> ActionResult.failed("unknown action")
    }
}

/** Periodically appends a sensor sample to a CSV (#270). One logger at a time. */
class SensorLogger(private val context: Context, private val scope: CoroutineScope, private val read: suspend (String) -> String?) {
    private var job: Job? = null
    val running get() = job?.isActive == true
    fun start(sensor: String, periodS: Int, file: File, maxRows: Int = 5000) {
        stop(); file.parentFile?.mkdirs(); if (!file.exists()) file.writeText("time,sensor,value\n")
        job = scope.launch { var n = 0; while (n < maxRows) { read(sensor)?.let { v -> file.appendText("${System.currentTimeMillis()},$sensor,${v.replace(',', ';')}\n") }; n++; delay(periodS * 1000L) } }
    }
    fun stop() { job?.cancel(); job = null }
}

fun sensorLogCapability(logger: SensorLogger, dir: File) = Capability("sensor_log",
    "Log a sensor to CSV every period_s seconds: action=start sensor=light|accelerometer|… period_s=… | stop | status. File: sensor_<name>.csv in the Deskbot folder.",
    """{"type":"object","properties":{"action":{"type":"string","enum":["start","stop","status"]},"sensor":{"type":"string"},"period_s":{"type":"integer"}},"required":["action"]}""") { a ->
    when (a.str("action")) {
        "start" -> { val s = a.str("sensor") ?: return@Capability ActionResult.failed("sensor required"); if (SensorNames.typeOf(s) == null) return@Capability ActionResult.failed("unknown sensor"); val f = File(dir, "sensor_${s.replace(' ', '_')}.csv"); logger.start(s, (a.int("period_s") ?: 30).coerceIn(2, 3600), f); ActionResult.completed("Logging $s to ${f.path}", buildJsonObject { put("path", f.path) }) }
        "stop" -> { logger.stop(); ActionResult.completed("Stopped") }
        "status" -> ActionResult.completed(if (logger.running) "running" else "stopped")
        else -> ActionResult.failed("action must be start, stop or status")
    }
}
