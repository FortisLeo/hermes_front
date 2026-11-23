package com.rishitgoklani.hermes_front_mobile

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.rishitgoklani.hermes_front_mobile.data.SettingsStore
import com.rishitgoklani.hermes_front_mobile.data.bridge.BridgeProtocol
import com.rishitgoklani.hermes_front_mobile.data.bridge.BridgeServer
import com.rishitgoklani.hermes_front_mobile.data.hermes.HermesClient
import com.rishitgoklani.hermes_front_mobile.data.persona.FilePersonaRepository
import com.rishitgoklani.hermes_front_mobile.data.persona.FileQuickActionsRepository
import com.rishitgoklani.hermes_front_mobile.data.shell.FileAuditLog
import com.rishitgoklani.hermes_front_mobile.data.wake.ModelDownloader
import com.rishitgoklani.hermes_front_mobile.data.wake.ModelStore
import com.rishitgoklani.hermes_front_mobile.data.wake.VoskKeywordSpotter
import com.rishitgoklani.hermes_front_mobile.data.camera.CameraXCameraPort
import com.rishitgoklani.hermes_front_mobile.data.capabilities.AmbientController
import com.rishitgoklani.hermes_front_mobile.data.capabilities.AndroidFaceCounter
import com.rishitgoklani.hermes_front_mobile.data.events.DeviceEventSource
import com.rishitgoklani.hermes_front_mobile.data.events.FileRoutineRepository
import com.rishitgoklani.hermes_front_mobile.data.events.MotionSource
import com.rishitgoklani.hermes_front_mobile.data.ui.AccessibilityUiAutomation
import com.rishitgoklani.hermes_front_mobile.data.ui.DeskbotAccessibilityService
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.SelfCheck
import com.rishitgoklani.hermes_front_mobile.domain.routines.DeviceEvent
import com.rishitgoklani.hermes_front_mobile.domain.routines.RoutineEngine
import com.rishitgoklani.hermes_front_mobile.domain.safety.KillSwitch
import com.rishitgoklani.hermes_front_mobile.domain.sense.MotionKind
import com.rishitgoklani.hermes_front_mobile.domain.voice.ConversationLoop
import com.rishitgoklani.hermes_front_mobile.domain.voice.VoiceLoopConfig
import com.rishitgoklani.hermes_front_mobile.domain.voice.WakeController
import com.rishitgoklani.hermes_front_mobile.data.speech.AndroidSpeechSynthesizer
import com.rishitgoklani.hermes_front_mobile.data.speech.AndroidSpeechToText
import com.rishitgoklani.hermes_front_mobile.data.ui.ServiceEnabler
import com.rishitgoklani.hermes_front_mobile.domain.HermesConfig
import com.rishitgoklani.hermes_front_mobile.domain.HermesGateway
import com.rishitgoklani.hermes_front_mobile.domain.RunVoiceTurn
import com.rishitgoklani.hermes_front_mobile.domain.SettingsRepository
import com.rishitgoklani.hermes_front_mobile.domain.SpeechSynthesizer
import com.rishitgoklani.hermes_front_mobile.domain.behavior.DebugSnapshot
import com.rishitgoklani.hermes_front_mobile.domain.behavior.DebugSource
import com.rishitgoklani.hermes_front_mobile.domain.behavior.RunCapability
import com.rishitgoklani.hermes_front_mobile.domain.face.FaceController
import com.rishitgoklani.hermes_front_mobile.domain.persona.Persona
import com.rishitgoklani.hermes_front_mobile.domain.persona.PersonaTheme
import com.rishitgoklani.hermes_front_mobile.ui.chat.ChatViewModel
import com.rishitgoklani.hermes_front_mobile.ui.settings.DebugViewModel
import com.rishitgoklani.hermes_front_mobile.ui.settings.SettingsViewModel
import com.rishitgoklani.hermes_front_mobile.ui.voice.VoiceViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** The only place implementations are bound to ports. Capability wiring lives in [CapabilityCatalog]. */
class AppContainer(private val app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings: SettingsRepository = SettingsStore(app)

    @Volatile private var current = HermesConfig()
    @Volatile private var bridgeToken = ""
    init {
        settings.config.onEach { current = it }.launchIn(scope)
        settings.bridgeToken.onEach { bridgeToken = it }.launchIn(scope)
        ServiceEnabler.enableAccessibility(app); ServiceEnabler.enableNotificationListener(app)
    }

    val hermes: HermesGateway = HermesClient({ current }, { "Current local date and time on the user's phone: " + com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.LocalTime.describe(java.time.ZonedDateTime.now()) })
    private val tts = AndroidSpeechSynthesizer(app)
    val speaker: SpeechSynthesizer get() = tts
    val mouth: com.rishitgoklani.hermes_front_mobile.domain.face.MouthSource get() = tts
    /** Set by MainActivity while it exists; asks it to enter picture-in-picture. */
    @Volatile var pipRequest: (() -> Boolean)? = null
    val avatar = com.rishitgoklani.hermes_front_mobile.domain.avatar.AvatarController()
    val avatarDir: File get() = File(app.filesDir, "avatar")
    /** Changes whenever a new model is installed, so the viewer reloads. */
    val avatarModelVersion = kotlinx.coroutines.flow.MutableStateFlow(File(File(app.filesDir, "avatar"), "model.vrm").lastModified())
    val avatarCatalog = com.rishitgoklani.hermes_front_mobile.data.avatar.OsaHttpCatalog()
    /** The one way a model becomes the avatar (license-checked by the installer): write atomically, turn the avatar on, reload the viewer. */
    val avatarInstaller = com.rishitgoklani.hermes_front_mobile.domain.avatar.AvatarInstaller { bytes ->
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { avatarDir.mkdirs(); val tmp = File(avatarDir, "model.vrm.tmp"); tmp.writeBytes(bytes); check(tmp.renameTo(File(avatarDir, "model.vrm"))) { "could not save model" } }
        avatar.setNodes(emptyList()); avatarModelVersion.value = System.currentTimeMillis(); avatar.setEnabled(true)
    }
    val audit = FileAuditLog(File(app.filesDir, "audit.log"))

    // Face, personas, quick actions
    val face = FaceController(scope)
    val personas = FilePersonaRepository(File(app.filesDir, "personas"))
    val quickActions = FileQuickActionsRepository(File(app.filesDir, "quick_actions.json"))
    private val _theme = MutableStateFlow(PersonaTheme())
    val theme: StateFlow<PersonaTheme> = _theme

    suspend fun applyPersona(p: Persona) {
        face.resetTweaks(); face.tweak { p.tweaks() }; face.setMood(p.mood())
        tts.setVoice(p.voiceRate, p.voicePitch); _theme.value = p.theme
    }

    // Safety: kill switch halts voice, speech and refuses bridge calls until reset locally.
    val kill: KillSwitch = KillSwitch(onTrigger = {
        wake.stop(); tts.stop(); face.setMood(com.rishitgoklani.hermes_front_mobile.domain.face.Mood.Scared)
        audit.record("kill_switch", "triggered", "active")
    })
    fun resetKill() { kill.reset(); scope.launch { applyPersona(personas.active()) } }

    // Always-listening voice (wake word + barge-in) with camera attached to "what is this" style requests
    private val prefs = app.getSharedPreferences("deskbot", android.content.Context.MODE_PRIVATE)
    val modelStore = ModelStore(File(app.filesDir, "vosk"), ModelDownloader.SMALL_EN_SHA256, { to -> ModelDownloader.download(ModelDownloader.SMALL_EN_URL, to) })
    private val camera = CameraXCameraPort(app)
    val runVoiceTurn = RunVoiceTurn(AndroidSpeechToText(app), hermes, tts, camera, { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }, audit = audit)
    private val loop = ConversationLoop(VoskKeywordSpotter(modelStore), runVoiceTurn, tts, VoiceLoopConfig(),
        onWake = { face.react(com.rishitgoklani.hermes_front_mobile.domain.face.Reaction.Glance) }, onKill = { kill.trigger("voice: $it") })
    val wake: WakeController = WakeController(loop, scope, { prefs.edit().putBoolean("wake", it).apply() })
    init { scope.launch { wake.events.collect { e -> if (e is com.rishitgoklani.hermes_front_mobile.domain.VoiceEvent.Expression) face.apply(e.expr) } } }
    fun showWhenLocked(): Boolean = prefs.getBoolean("show_locked", false)
    fun resumeWakeIfEnabled() { if (prefs.getBoolean("wake", false) && !kill.isActive() && !safeMode.value) wake.start(save = false) }

    // Event routines, motion, ambient light
    val routines = FileRoutineRepository(File(app.filesDir, "routines.json"))
    private val ambient = AmbientController(app, scope)
    private val motion = MotionSource(app) { k ->
        val r = when (k) { MotionKind.PickUp -> com.rishitgoklani.hermes_front_mobile.domain.face.Reaction.Startle; MotionKind.Knock -> com.rishitgoklani.hermes_front_mobile.domain.face.Reaction.Glance
            MotionKind.PutDown -> com.rishitgoklani.hermes_front_mobile.domain.face.Reaction.Sigh; MotionKind.Tilt -> com.rishitgoklani.hermes_front_mobile.domain.face.Reaction.Dizzy }
        face.react(r); scope.launch { engine.onEvent(DeviceEvent.Motion(k.name.lowercase())) }
    }
    private val uiForChecks = AccessibilityUiAutomation(app)
    private val selfChecks = listOf(
        SelfCheck("bridge") { if (bridgeToken.isEmpty()) "token not set" else null },
        SelfCheck("speech engine") { if (tts.isReady()) null else "TTS not ready" },
        SelfCheck("accessibility") { runCatching { uiForChecks.screenSize() }.fold({ null }, { it.message }) },
        SelfCheck("notification access") { if (com.rishitgoklani.hermes_front_mobile.data.notifications.DeskbotNotificationListener.instance != null) null else "listener not connected" },
        SelfCheck("camera") { runCatching { camera.capture(com.rishitgoklani.hermes_front_mobile.domain.CameraFacing.Front, 140_000).size }.fold({ if (it > 1000) null else "empty frame" }, { it.message }) },
        SelfCheck("hermes") { if (debugSource.snapshot().hermesHealthy) null else "API server unreachable" },
        SelfCheck("kill switch") { if (kill.isActive()) "ACTIVE: ${kill.reason}" else null },
    )

    // Safe mode after repeated crashes; places for geofence routines
    private val launchStore = com.rishitgoklani.hermes_front_mobile.data.events.FileLaunchStore(File(app.filesDir, "launches.txt"))
    private val crashGuard = com.rishitgoklani.hermes_front_mobile.domain.safety.CrashLoopGuard(launchStore)
    val safeMode = MutableStateFlow(crashGuard.onLaunch())
    init { scope.launch { kotlinx.coroutines.delay(60_000); crashGuard.markHealthy() } }
    fun exitSafeMode() { crashGuard.markHealthy(); safeMode.value = false; startSensing(); resumeWakeIfEnabled() }
    val places = com.rishitgoklani.hermes_front_mobile.data.events.FilePlaceRepository(File(app.filesDir, "places.json"))

    private val macroStore = com.rishitgoklani.hermes_front_mobile.data.events.FileMacroStore(File(app.filesDir, "macros.json"))
    val macros = com.rishitgoklani.hermes_front_mobile.domain.macro.MacroRecorder(onChange = macroStore::save).also { it.load(macroStore.load()) }
    private val visualWatcher = com.rishitgoklani.hermes_front_mobile.data.capabilities.VisualWatcher(camera, scope) { score -> audit.record("visual_change", score.toString(), "event"); engine.onEvent(DeviceEvent.VisualChange(score)) }
    private val soundWatcher = com.rishitgoklani.hermes_front_mobile.data.capabilities.SoundWatcher(scope, { runCatching { com.rishitgoklani.hermes_front_mobile.data.capabilities.soundLevelCapability().executor.execute(kotlinx.serialization.json.JsonObject(emptyMap())).data?.get("peak_dbfs")?.let { (it as kotlinx.serialization.json.JsonPrimitive).content.toDouble() } }.getOrNull() }) { label, db -> audit.record("sound_spike", "$label $db", "event"); engine.onEvent(DeviceEvent.Sound(label, db)) }

    // Generative UI
    val a2ui = com.rishitgoklani.hermes_front_mobile.domain.a2ui.A2uiStore()
    private val _screenRequests = kotlinx.coroutines.flow.MutableSharedFlow<com.rishitgoklani.hermes_front_mobile.domain.a2ui.Surface>(extraBufferCapacity = 8)
    val screenRequests: kotlinx.coroutines.flow.SharedFlow<com.rishitgoklani.hermes_front_mobile.domain.a2ui.Surface> = _screenRequests

    val capabilities: com.rishitgoklani.hermes_front_mobile.domain.bridge.CapabilityRegistry = CapabilityCatalog.build(CatalogDeps(
        app, tts, scope, audit, File(app.filesDir, "audit.log"), face, personas, quickActions, ::applyPersona, wake, modelStore::isReady, camera,
        AndroidFaceCounter(), kill, routines, ambient, selfChecks, a2ui,
        { surface -> _screenRequests.tryEmit(surface); runCatching { app.startActivity(android.content.Intent(app, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) } }, places, { safeMode.value }, visualWatcher, soundWatcher,
        Triple({ prefs.getBoolean("show_locked", false) }, { v: Boolean -> prefs.edit().putBoolean("show_locked", v).apply() }, { runCatching { app.startActivity(android.content.Intent(app, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) } }),
        macros, { runCapability }, { capabilitiesRef() }, onFaceSeen = { scope.launch { engine.onEvent(DeviceEvent.FaceSeen()) } }, avatar = avatar, pip = { pipRequest }, avatarCatalog = avatarCatalog, avatarInstaller = avatarInstaller))
    private fun capabilitiesRef(): com.rishitgoklani.hermes_front_mobile.domain.bridge.CapabilityRegistry = capabilities
    val runCapability: RunCapability = RunCapability(capabilities)
    val engine = RoutineEngine(routines, runCapability, audit = audit)
    val handleUiAction = com.rishitgoklani.hermes_front_mobile.domain.a2ui.HandleUiAction(a2ui, runCapability, hermes, tts)
    val statusProvider = com.rishitgoklani.hermes_front_mobile.ui.a2ui.StatusProvider { kind ->
        val r = runCapability("battery"); val d = r.data
        when (kind) {
            "battery" -> (d?.get("percent") as? kotlinx.serialization.json.JsonPrimitive)?.content?.toFloatOrNull()?.let { it to "${it.toInt()}%" }
            "temperature" -> (d?.get("temp_c") as? kotlinx.serialization.json.JsonPrimitive)?.content?.toFloatOrNull()?.let { it to "${it.toInt()} °C" }
            else -> null
        }
    }

    private val placeTracker = com.rishitgoklani.hermes_front_mobile.domain.sense.PlaceTracker()
    private val greeter = com.rishitgoklani.hermes_front_mobile.domain.face.ArrivalGreeter(hour = { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) })
    private val fusion = com.rishitgoklani.hermes_front_mobile.domain.sense.ArrivalFusion()
    init {
        // Avatar safety: if the process died twice in a row while the 3D avatar was on (the phone is memory-starved), fall back to the eyes face.
        val d = com.rishitgoklani.hermes_front_mobile.domain.avatar.AvatarSafety.onStart(prefs.getBoolean("avatar_mode", false), prefs.getInt("avatar_unclean", 0))
        prefs.edit().putBoolean("avatar_mode", d.avatarOn).putInt("avatar_unclean", d.uncleanCount).apply()
        avatar.setEnabled(prefs.getBoolean("avatar_mode", false))
        scope.launch { kotlinx.coroutines.delay(60_000); prefs.edit().putInt("avatar_unclean", 0).apply() }
        scope.launch { avatar.state.collect { prefs.edit().putBoolean("avatar_mode", it.enabled).apply() } }
        scope.launch { face.state.collect { avatar.setMood(it.mood); avatar.look(it.lookX, it.lookY) } }
        scope.launch { tts.openness.collect { avatar.setMouth(it) } }
    }
    private var sensing = false
    fun startSensing() {
        if (safeMode.value || sensing) return; sensing = true
        scope.launch { // geofence polling: only does work while places exist
            while (true) { kotlinx.coroutines.delay(120_000); val ps = places.all(); if (ps.isEmpty() || kill.isActive()) continue
                locationOnce(app)?.let { (lat, lon) -> placeTracker.update(lat, lon, ps).forEach { engine.onEvent(it) } } }
        }
        engine.observe { e ->
            if (!kill.isActive()) greeter.onEvent(e)?.let { g -> face.react(com.rishitgoklani.hermes_front_mobile.domain.face.Reaction.Giggle); tts.enqueue(g) }
            val sig = when (e) {
                is DeviceEvent.FaceSeen -> com.rishitgoklani.hermes_front_mobile.domain.sense.PresenceSignal.Face
                is DeviceEvent.Sound -> com.rishitgoklani.hermes_front_mobile.domain.sense.PresenceSignal.Sound
                is DeviceEvent.Motion -> com.rishitgoklani.hermes_front_mobile.domain.sense.PresenceSignal.Motion
                is DeviceEvent.VisualChange -> com.rishitgoklani.hermes_front_mobile.domain.sense.PresenceSignal.Visual
                else -> null
            }
            sig?.let { s -> fusion.signal(s)?.let { p -> audit.record("presence", if (p.arrived) "arrived" else "departed", "event"); scope.launch { engine.onEvent(p) } } }
        }
        scope.launch { while (true) { kotlinx.coroutines.delay(60_000); fusion.tick()?.let { p -> audit.record("presence", "departed", "event"); engine.onEvent(p) } } }
        motion.start()
        DeskbotAccessibilityService.onKillCombo = { kill.trigger("volume-down x3") }
        DeviceEventSource(app, scope) { e ->
            if (e is DeviceEvent.BatteryLow) { face.setMood(com.rishitgoklani.hermes_front_mobile.domain.face.Mood.Worried); face.react(com.rishitgoklani.hermes_front_mobile.domain.face.Reaction.Sigh) }
            engine.onEvent(e)
        }.start()
    }

    private val bridge = BridgeServer(BridgeProtocol(capabilities, kill::isActive) { bridgeToken })
    fun startBridge() = runCatching { bridge.start() }.onFailure { android.util.Log.w("DeskbotBridge", "start failed: ${it.message}") }
    fun restorePersona() { scope.launch { applyPersona(personas.active()) } }

    private val http = OkHttpClient.Builder().callTimeout(4, TimeUnit.SECONDS).build()
    val debugSource = DebugSource { kotlinx.coroutines.withContext(Dispatchers.IO) {
        val cfg = current
        val health = runCatching { http.newCall(Request.Builder().url(cfg.baseUrl.trimEnd('/') + "/health").build()).execute().use { it.body?.string().orEmpty() } }.getOrNull()
        val version = health?.let { Regex(""""version":\s*"([^"]+)"""").find(it)?.groupValues?.get(1) }.orEmpty()
        val tail = runCatching { File(app.filesDir, "audit.log").readLines().takeLast(40).reversed() }.getOrDefault(emptyList())
        DebugSnapshot(health != null, version, capabilities.list().size, tail, if (bridgeToken.isEmpty()) "bridge token not set" else "")
    } }

    fun voiceViewModel(owner: ViewModelStoreOwner): VoiceViewModel = vm(owner) { VoiceViewModel(runVoiceTurn, face::apply) }
    fun chatViewModel(owner: ViewModelStoreOwner): ChatViewModel = vm(owner) { ChatViewModel(hermes, face::apply) }
    fun settingsViewModel(owner: ViewModelStoreOwner): SettingsViewModel = vm(owner) { SettingsViewModel(settings) }
    fun debugViewModel(owner: ViewModelStoreOwner): DebugViewModel = vm(owner) { DebugViewModel(debugSource) }
    fun avatarLibraryViewModel(owner: ViewModelStoreOwner): com.rishitgoklani.hermes_front_mobile.ui.avatar.AvatarLibraryViewModel = vm(owner) { com.rishitgoklani.hermes_front_mobile.ui.avatar.AvatarLibraryViewModel(avatarCatalog, avatarInstaller) }

    private inline fun <reified T : ViewModel> vm(owner: ViewModelStoreOwner, crossinline create: () -> T): T =
        ViewModelProvider(owner, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
        })[T::class.java]
}

class DeskbotApp : Application() {
    lateinit var container: AppContainer
    override fun onCreate() { super.onCreate(); container = AppContainer(this); container.startBridge(); container.restorePersona(); container.startSensing() }
}
