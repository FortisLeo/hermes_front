package com.rishitgoklani.hermes_front_mobile

import android.app.Application
import com.rishitgoklani.hermes_front_mobile.data.camera.CameraXCameraPort
import com.rishitgoklani.hermes_front_mobile.data.capabilities.OverlayManager
import com.rishitgoklani.hermes_front_mobile.data.capabilities.alarmCapability
import com.rishitgoklani.hermes_front_mobile.data.capabilities.appCapabilities
import com.rishitgoklani.hermes_front_mobile.data.capabilities.appOpenCapability
import com.rishitgoklani.hermes_front_mobile.data.capabilities.batteryCapability
import com.rishitgoklani.hermes_front_mobile.data.capabilities.brightnessCapability
import com.rishitgoklani.hermes_front_mobile.data.capabilities.calendarCapabilities
import com.rishitgoklani.hermes_front_mobile.data.capabilities.clipboardCapabilities
import com.rishitgoklani.hermes_front_mobile.data.capabilities.contactCapabilities
import com.rishitgoklani.hermes_front_mobile.data.capabilities.deviceInfoCapability
import com.rishitgoklani.hermes_front_mobile.data.capabilities.hapticsCapability
import com.rishitgoklani.hermes_front_mobile.data.capabilities.locationCapability
import com.rishitgoklani.hermes_front_mobile.data.capabilities.mediaCapabilities
import com.rishitgoklani.hermes_front_mobile.data.capabilities.networkInfoCapability
import com.rishitgoklani.hermes_front_mobile.data.capabilities.overlayCapabilities
import com.rishitgoklani.hermes_front_mobile.data.capabilities.sensorCapabilities
import com.rishitgoklani.hermes_front_mobile.data.capabilities.settingsCapabilities
import com.rishitgoklani.hermes_front_mobile.data.capabilities.torchCapability
import com.rishitgoklani.hermes_front_mobile.data.capabilities.volumeCapability
import com.rishitgoklani.hermes_front_mobile.data.http.httpRequestCapability
import com.rishitgoklani.hermes_front_mobile.data.notifications.AndroidNotificationPoster
import com.rishitgoklani.hermes_front_mobile.data.notifications.ListenerNotificationSource
import com.rishitgoklani.hermes_front_mobile.data.shell.ProcessCommandRunner
import com.rishitgoklani.hermes_front_mobile.data.ui.AccessibilityUiAutomation
import com.rishitgoklani.hermes_front_mobile.domain.SpeechSynthesizer
import com.rishitgoklani.hermes_front_mobile.domain.behavior.QuickActionsRepository
import com.rishitgoklani.hermes_front_mobile.domain.bridge.CapabilityRegistry
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.TimerScheduler
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.cameraCaptureCapability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.faceCapabilities
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.fileCapabilities
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.notificationCapabilities
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.quickActionsCapability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.shellCapability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.timerCapabilities
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.ttsSayCapability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.uiCapabilities
import com.rishitgoklani.hermes_front_mobile.domain.bridge.shell.AuditLog
import com.rishitgoklani.hermes_front_mobile.domain.bridge.shell.DenyList
import com.rishitgoklani.hermes_front_mobile.data.capabilities.AmbientController
import com.rishitgoklani.hermes_front_mobile.data.capabilities.HighlightOverlay
import com.rishitgoklani.hermes_front_mobile.data.capabilities.ambientCapability
import com.rishitgoklani.hermes_front_mobile.data.capabilities.auditLogCapability
import com.rishitgoklani.hermes_front_mobile.data.capabilities.highlightCapability
import com.rishitgoklani.hermes_front_mobile.domain.CameraPort
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.SelfCheck
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.killSwitchCapability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.presenceCapability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.routineCapabilities
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.wakeCapability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.selfTestCapability
import com.rishitgoklani.hermes_front_mobile.domain.routines.RoutineRepository
import com.rishitgoklani.hermes_front_mobile.domain.safety.KillSwitch
import com.rishitgoklani.hermes_front_mobile.domain.sense.FaceCounter
import com.rishitgoklani.hermes_front_mobile.domain.voice.WakeController
import java.io.File
import com.rishitgoklani.hermes_front_mobile.domain.macro.recorded
import com.rishitgoklani.hermes_front_mobile.domain.face.FaceController
import com.rishitgoklani.hermes_front_mobile.domain.notifications.NotificationHistory
import com.rishitgoklani.hermes_front_mobile.domain.persona.Persona
import com.rishitgoklani.hermes_front_mobile.domain.persona.PersonaRepository
import com.rishitgoklani.hermes_front_mobile.domain.system.FileSandbox
import com.rishitgoklani.hermes_front_mobile.domain.system.TimerBook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Everything the tool catalog needs from the container (keeps [CapabilityCatalog.build] readable). */
class CatalogDeps(
    val app: Application, val tts: SpeechSynthesizer, val scope: CoroutineScope, val audit: AuditLog, val auditFile: File, val face: FaceController,
    val personas: PersonaRepository, val quickActions: QuickActionsRepository, val onPersona: suspend (Persona) -> Unit,
    val wake: WakeController, val modelReady: () -> Boolean, val camera: CameraPort, val faceCounter: FaceCounter, val kill: KillSwitch,
    val routines: RoutineRepository, val ambient: AmbientController, val selfChecks: List<SelfCheck>,
    val a2ui: com.rishitgoklani.hermes_front_mobile.domain.a2ui.A2uiStore, val onScreenShown: (com.rishitgoklani.hermes_front_mobile.domain.a2ui.Surface) -> Unit,
    val places: com.rishitgoklani.hermes_front_mobile.domain.sense.PlaceRepository, val isSafeMode: () -> Boolean,
    val visualWatcher: com.rishitgoklani.hermes_front_mobile.data.capabilities.VisualWatcher, val soundWatcher: com.rishitgoklani.hermes_front_mobile.data.capabilities.SoundWatcher,
    val lockDisplay: Triple<() -> Boolean, (Boolean) -> Unit, () -> Unit>,
    val macros: com.rishitgoklani.hermes_front_mobile.domain.macro.MacroRecorder, val runCapability: () -> com.rishitgoklani.hermes_front_mobile.domain.behavior.RunCapability,
    val registry: () -> CapabilityRegistry,
    val onFaceSeen: () -> Unit = {},
    val pip: () -> (() -> Boolean)? = { null },
    val avatar: com.rishitgoklani.hermes_front_mobile.domain.avatar.AvatarController? = null,
)

/** Builds the full tool set Hermes can call. One place to add a capability (open/closed: append a line). */
object CapabilityCatalog {
    fun build(d: CatalogDeps): CapabilityRegistry {
        val app = d.app
        val ui = AccessibilityUiAutomation(app)
        val history = NotificationHistory()
        val notifications = ListenerNotificationSource(app, history)
        val poster = AndroidNotificationPoster(app, R.mipmap.ic_launcher)
        val overlay = OverlayManager(app)
        val timers = TimerScheduler { delayMs, action -> val job = d.scope.launch { delay(delayMs); action() }; ({ job.cancel() }) }
        val sandbox = FileSandbox(listOfNotNull(app.filesDir, app.cacheDir, android.os.Environment.getExternalStorageDirectory(), app.getExternalFilesDir(null)))
        val sensorLogger = com.rishitgoklani.hermes_front_mobile.data.capabilities.SensorLogger(app, d.scope) { name -> sensorCapabilities(app).first { it.name == "sensor_read" }.executor.execute(kotlinx.serialization.json.buildJsonObject { put("name", kotlinx.serialization.json.JsonPrimitive(name)) }).let { r -> if (r.status == "completed") r.message else null } }
        val deskbotDir = File(android.os.Environment.getExternalStorageDirectory(), "Deskbot")
        val raw = buildList {
            add(com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.timeNowCapability()); add(ttsSayCapability(d.tts)); add(batteryCapability(app)); add(appOpenCapability(app)); add(deviceInfoCapability())
            add(cameraCaptureCapability(d.camera) { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) })
            add(volumeCapability(app)); add(brightnessCapability(app)); add(torchCapability(app))
            addAll(uiCapabilities(ui) { x1, y1, x2, y2 -> ProcessCommandRunner().run("input draganddrop $x1 $y1 $x2 $y2 800", root = true).exitCode == 0 }); addAll(settingsCapabilities(app)); addAll(appCapabilities(app)); addAll(clipboardCapabilities(app))
            addAll(sensorCapabilities(app)); add(locationCapability(app)); add(networkInfoCapability(app))
            addAll(mediaCapabilities(app)); add(hapticsCapability(app)); add(alarmCapability(app)); addAll(overlayCapabilities(app, overlay))
            add(highlightCapability(app, HighlightOverlay(app), ui))
            add(com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.pipFaceCapability {
                if (d.pip() == null) { app.startActivity(android.content.Intent(app, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)); delay(1500) }
                d.pip()?.invoke() ?: false
            })
            add(com.rishitgoklani.hermes_front_mobile.data.capabilities.drawOverlayCapability(app, com.rishitgoklani.hermes_front_mobile.data.capabilities.DrawOverlay(app)))
            add(com.rishitgoklani.hermes_front_mobile.data.capabilities.faceBubbleCapability(com.rishitgoklani.hermes_front_mobile.data.capabilities.FaceBubble(app, d.face, d.scope, android.content.Intent(app, MainActivity::class.java))))
            addAll(calendarCapabilities(app)); addAll(contactCapabilities(app)); addAll(fileCapabilities(sandbox)); add(httpRequestCapability())
            addAll(timerCapabilities(TimerBook(), timers) { name -> d.tts.enqueue("Your $name timer is done."); poster.post("Timer done", name) })
            addAll(notificationCapabilities(notifications, history, poster))
            addAll(faceCapabilities(d.face, d.personas, d.onPersona)); add(quickActionsCapability(d.quickActions, d.registry))
            d.avatar?.let { add(com.rishitgoklani.hermes_front_mobile.domain.avatar.avatarModeCapability(it)); add(com.rishitgoklani.hermes_front_mobile.domain.avatar.avatarPoseCapability(it)); add(com.rishitgoklani.hermes_front_mobile.domain.avatar.avatarAnimateCapability(it)); add(com.rishitgoklani.hermes_front_mobile.domain.avatar.avatarBonesCapability(it))
                val dir = File(d.app.filesDir, "avatar")
                add(com.rishitgoklani.hermes_front_mobile.domain.avatar.avatarImportCapability(
                    stage = { src -> dir.mkdirs(); val tmp = File(dir, "incoming.vrm"); val r = ProcessCommandRunner().run("cp '${src.replace("'", "")}' '${tmp.path}' && chmod 644 '${tmp.path}'", root = true); if (r.exitCode == 0 && tmp.isFile) tmp.readBytes().also { tmp.delete() } else null },
                    install = { b -> File(dir, "model.vrm").writeBytes(b) }))
                val clipDir = File(dir, "clips")
                val clips = { clipDir.listFiles { f -> f.extension == "vrma" }?.map { it.nameWithoutExtension }?.sorted().orEmpty() }
                add(com.rishitgoklani.hermes_front_mobile.domain.avatar.avatarClipCapability(it, clips))
                add(com.rishitgoklani.hermes_front_mobile.domain.avatar.avatarImportClipCapability(
                    stage = { src -> dir.mkdirs(); val tmp = File(dir, "incoming.vrma"); val r = ProcessCommandRunner().run("cp '${src.replace("'", "")}' '${tmp.path}' && chmod 644 '${tmp.path}'", root = true); if (r.exitCode == 0 && tmp.isFile) tmp.readBytes().also { tmp.delete() } else null },
                    install = { n, b -> clipDir.mkdirs(); File(clipDir, "$n.vrma").writeBytes(b) })) }; add(wakeCapability(d.wake, d.modelReady)); add(presenceCapability(d.camera, d.faceCounter, com.rishitgoklani.hermes_front_mobile.data.capabilities.AndroidFaceLocator()) { x, y -> d.face.look(x, y); d.onFaceSeen() }); add(killSwitchCapability(d.kill))
            add(ambientCapability(d.ambient)); add(auditLogCapability(d.auditFile)); addAll(routineCapabilities(d.routines, d.registry))
            addAll(com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.placeCapabilities(d.places) { locationOnce(app) })
            add(com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.safeModeCapability(d.isSafeMode)); add(com.rishitgoklani.hermes_front_mobile.data.capabilities.soundLevelCapability())
            add(selfTestCapability(d.selfChecks)); addAll(com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.a2uiCapabilities(d.a2ui, d.tts, d.onScreenShown))
            add(shellCapability(ProcessCommandRunner(), DenyList(), d.audit))
            addAll(com.rishitgoklani.hermes_front_mobile.data.net.netCapabilities(sandbox, { wifiSubnet(app) }))
            addAll(com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.toolboxCapabilities(sandbox, File(deskbotDir, "notes.md")) { f, algo -> com.rishitgoklani.hermes_front_mobile.data.net.Downloads.hash(f, algo) })
            add(com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.healthScoreCapability { healthInput(app, d.kill, File(app.filesDir, "audit.log")) })
            add(com.rishitgoklani.hermes_front_mobile.data.capabilities.intentCapability(app)); add(com.rishitgoklani.hermes_front_mobile.data.capabilities.mediaSearchCapability(app))
            add(com.rishitgoklani.hermes_front_mobile.data.capabilities.audioRecordCapability(app, d.scope)); add(com.rishitgoklani.hermes_front_mobile.data.capabilities.audioRouteCapability(app))
            add(com.rishitgoklani.hermes_front_mobile.data.capabilities.sensorLogCapability(sensorLogger, deskbotDir))
            addAll(com.rishitgoklani.hermes_front_mobile.domain.macro.macroCapabilities(d.macros, d.runCapability))
            add(com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.lockDisplayCapability(d.lockDisplay.first, d.lockDisplay.second, d.lockDisplay.third))
            add(com.rishitgoklani.hermes_front_mobile.data.capabilities.visualWatchCapability(d.visualWatcher)); add(com.rishitgoklani.hermes_front_mobile.data.capabilities.soundWatchCapability(d.soundWatcher))
        }
        return CapabilityRegistry(raw.map { it.recorded(d.macros) })
    }
}

/** Current (lat, lon) through the same location tool Hermes uses. */
internal suspend fun locationOnce(app: Application): Pair<Double, Double>? {
    val r = com.rishitgoklani.hermes_front_mobile.data.capabilities.locationCapability(app).executor.execute(kotlinx.serialization.json.JsonObject(emptyMap()))
    val lat = (r.data?.get("lat") as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull(); val lon = (r.data?.get("lon") as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()
    return if (lat != null && lon != null) lat to lon else null
}

/** This phone's /24 on Wi-Fi, e.g. 192.168.0.0/24, or null when not on Wi-Fi. */
@Suppress("DEPRECATION")
internal fun wifiSubnet(app: Application): String? {
    val ip = app.applicationContext.getSystemService(android.net.wifi.WifiManager::class.java).connectionInfo?.ipAddress ?: return null
    if (ip == 0) return null
    return "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.0/24"
}

internal fun healthInput(app: Application, kill: KillSwitch, audit: File): com.rishitgoklani.hermes_front_mobile.domain.system.HealthScore.Input {
    val i = app.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
    val pct = i?.let { com.rishitgoklani.hermes_front_mobile.data.capabilities.BatteryMath.percent(it.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1), it.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)) } ?: -1
    val charging = i?.let { com.rishitgoklani.hermes_front_mobile.data.capabilities.BatteryMath.isCharging(it.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)) } ?: false
    val temp = i?.let { com.rishitgoklani.hermes_front_mobile.data.capabilities.BatteryMath.tempC(it.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0)) } ?: 0.0
    val st = android.os.StatFs(android.os.Environment.getDataDirectory().path); val free = (st.availableBytes * 100 / st.totalBytes).toInt()
    val cm = app.getSystemService(android.net.ConnectivityManager::class.java); val online = cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    val failures = runCatching { audit.readLines().takeLast(100).count { it.split('\t').getOrNull(2) in setOf("failed", "denied") } }.getOrDefault(0)
    return com.rishitgoklani.hermes_front_mobile.domain.system.HealthScore.Input(pct, charging, temp, free, online, failures, kill.isActive())
}
