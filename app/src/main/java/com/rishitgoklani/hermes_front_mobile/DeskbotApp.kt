package com.rishitgoklani.hermes_front_mobile

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
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
class AppContainer(private val app: Application, val crashLog: com.rishitgoklani.hermes_front_mobile.domain.diagnostics.CrashLog) {
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
    // Gemini Live backend (optional): one streaming session instead of STT -> Hermes -> TTS. Settings (incl. the API key) live in DataStore only.
    val liveSettings = com.rishitgoklani.hermes_front_mobile.data.live.LiveSettingsStore(app)
    private val liveOut = com.rishitgoklani.hermes_front_mobile.data.live.AudioTrackLiveOutput(scope)
    /** The mouth follows whichever voice is sounding: Android TTS word progress or the Live voice's loudness. */
    val mouth: com.rishitgoklani.hermes_front_mobile.domain.face.MouthSource = object : com.rishitgoklani.hermes_front_mobile.domain.face.MouthSource {
        override val openness: StateFlow<Float> = kotlinx.coroutines.flow.combine(tts.openness, liveOut.level) { a, b -> maxOf(a, b) }.stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, 0f)
    }
    /** Set by MainActivity while it exists; asks it to enter picture-in-picture. */
    @Volatile var pipRequest: (() -> Boolean)? = null
    val avatar = com.rishitgoklani.hermes_front_mobile.domain.avatar.AvatarController()
    val avatarDir: File get() = File(app.filesDir, "avatar")
    val avatarLibraryDir: File get() = File(app.filesDir, "avatars")
    val avatarCatalog = com.rishitgoklani.hermes_front_mobile.data.avatar.OsaHttpCatalog(cacheDir = File(app.cacheDir, "avatar-thumbs"))
    // Avatar library: Room rows + optimized model files, the active slot in DataStore, and the use cases on top.
    private val avatarDb = com.rishitgoklani.hermes_front_mobile.data.avatar.DeskbotDatabases.create(app)
    val avatarLibrary: com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarLibrary = com.rishitgoklani.hermes_front_mobile.data.avatar.RoomAvatarLibrary(avatarDb.avatars())
    val avatarFiles: com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarFiles = com.rishitgoklani.hermes_front_mobile.data.avatar.FileAvatarFiles(avatarLibraryDir)
    val avatarSlot: com.rishitgoklani.hermes_front_mobile.domain.avatar.library.ActiveSlotStore = com.rishitgoklani.hermes_front_mobile.data.avatar.DataStoreActiveSlotStore.create(app)
    private val importAvatar = com.rishitgoklani.hermes_front_mobile.domain.avatar.library.ImportAvatar(avatarLibrary, avatarFiles, com.rishitgoklani.hermes_front_mobile.domain.avatar.library.GlbOptimizer(com.rishitgoklani.hermes_front_mobile.data.avatar.BitmapImageTranscoder()), { java.util.UUID.randomUUID().toString() }, System::currentTimeMillis)
    val selectAvatar = com.rishitgoklani.hermes_front_mobile.domain.avatar.library.SelectAvatar(avatarLibrary, avatarSlot, System::currentTimeMillis)
    val cycleAvatar = com.rishitgoklani.hermes_front_mobile.domain.avatar.library.CycleAvatar(avatarLibrary, avatarSlot, selectAvatar)
    val removeAvatar = com.rishitgoklani.hermes_front_mobile.domain.avatar.library.RemoveAvatar(avatarLibrary, avatarFiles, avatarSlot)
    private val markProven = com.rishitgoklani.hermes_front_mobile.domain.avatar.library.MarkProven(avatarLibrary)
    /** The one way a model becomes an avatar: optimize, store, add to the library, show it. */
    val avatarInstaller: com.rishitgoklani.hermes_front_mobile.domain.avatar.ModelInstaller = com.rishitgoklani.hermes_front_mobile.domain.avatar.library.LibraryInstaller(importAvatar, selectAvatar)
    val avatarSnapshots = com.rishitgoklani.hermes_front_mobile.ui.avatar.AvatarSnapshots()
    /** A model proves itself once the viewer showed it and the app survived 20 s; until then a crash marks it BROKEN at the next start. */
    private fun proveLater(id: String) { scope.launch { kotlinx.coroutines.delay(20_000); markProven(id) } }
    val audit = FileAuditLog(File(app.filesDir, "audit.log"))

    // Face, personas, quick actions
    val face = FaceController(scope)
    val personas = FilePersonaRepository(File(app.filesDir, "personas"))
    val quickActions = FileQuickActionsRepository(File(app.filesDir, "quick_actions.json"))
    private val _theme = MutableStateFlow(PersonaTheme())
    val theme: StateFlow<PersonaTheme> = _theme

    @Volatile private var activePersona: Persona? = null
    suspend fun applyPersona(p: Persona) {
        activePersona = p
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
    private val liveRun: com.rishitgoklani.hermes_front_mobile.domain.live.RunLiveSession by lazy {
        val guard = { if (kill.isActive()) "Kill switch active: reset it on the phone" else null }
        val tools = com.rishitgoklani.hermes_front_mobile.domain.live.LiveToolBridge(capabilities, runCapability, com.rishitgoklani.hermes_front_mobile.domain.live.LivePrompt.HOT_TOOLS, denied = guard)
        com.rishitgoklani.hermes_front_mobile.domain.live.RunLiveSession(
            session = com.rishitgoklani.hermes_front_mobile.data.live.GeminiLiveClient(apiKey = { liveSettings.settings.first().apiKey }),
            mic = com.rishitgoklani.hermes_front_mobile.data.live.AudioRecordLiveInput(), speaker = liveOut, tools = tools,
            settings = { liveSettings.settings.first() },
            prompt = { val p = activePersona; com.rishitgoklani.hermes_front_mobile.domain.live.LivePrompt.build(p?.name ?: "Deskbot", p?.tone.orEmpty(), p?.description.orEmpty()) },
            frame = { facing -> camera.capture(facing, 140_000) }, audit = audit)
    }
    private val liveLoop = com.rishitgoklani.hermes_front_mobile.domain.voice.LiveConversationLoop(VoskKeywordSpotter(modelStore), { liveRun }, VoiceLoopConfig(),
        onWake = { face.react(com.rishitgoklani.hermes_front_mobile.domain.face.Reaction.Glance) }, onKill = { kill.trigger("voice: $it") })
    val wake: WakeController = WakeController(
        com.rishitgoklani.hermes_front_mobile.domain.voice.SwitchingVoiceLoop(loop, liveLoop, { liveSettings.settings.first().ready }), scope, { prefs.edit().putBoolean("wake", it).apply() })
    // The backend is chosen when the loop starts, so switching it in Settings restarts a running loop.
    init { scope.launch { liveSettings.settings.map { it.ready }.distinctUntilChanged().drop(1).collect { if (wake.enabled.value) { wake.stop(); wake.start() } } } }
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
    private val visualWatcher = com.rishitgoklani.hermes_front_mobile.data.capabilities.AndroidFrameThumbnailer().let { thumbs -> com.rishitgoklani.hermes_front_mobile.domain.sense.VisualWatcher(camera, thumbs, scope, diagnostics = { android.util.Log.d("DeskbotVisual", it) }) { score, _ -> audit.record("visual_change", score.toString(), "event"); engine.onEvent(DeviceEvent.VisualChange(score)) } }
    private val soundWatcher = com.rishitgoklani.hermes_front_mobile.data.capabilities.SoundWatcher(scope, { runCatching { com.rishitgoklani.hermes_front_mobile.data.capabilities.soundLevelCapability().executor.execute(kotlinx.serialization.json.JsonObject(emptyMap())).data?.get("peak_dbfs")?.let { (it as kotlinx.serialization.json.JsonPrimitive).content.toDouble() } }.getOrNull() }) { label, db -> audit.record("sound_spike", "$label $db", "event"); engine.onEvent(DeviceEvent.Sound(label, db)) }

    // Generative UI
    val a2ui = com.rishitgoklani.hermes_front_mobile.domain.a2ui.A2uiStore()
    private val _screenRequests = kotlinx.coroutines.flow.MutableSharedFlow<com.rishitgoklani.hermes_front_mobile.domain.a2ui.Surface>(extraBufferCapacity = 8)
    val screenRequests: kotlinx.coroutines.flow.SharedFlow<com.rishitgoklani.hermes_front_mobile.domain.a2ui.Surface> = _screenRequests

    // Self-contained features (ranking, now playing, charge cap, health checks, reminders, routine templates) share one notification source/poster.
    private val notificationHistory = com.rishitgoklani.hermes_front_mobile.domain.notifications.NotificationHistory()
    private val notificationSource = com.rishitgoklani.hermes_front_mobile.data.notifications.ListenerNotificationSource(app, notificationHistory)
    private val notificationPoster = com.rishitgoklani.hermes_front_mobile.data.notifications.AndroidNotificationPoster(app, R.mipmap.ic_launcher)
    private val extras = ExtraFeatures(app, scope, tts, kill, { runCapability }, notificationSource, com.rishitgoklani.hermes_front_mobile.data.notifications.AndroidNotificationPoster(app, R.mipmap.ic_launcher, 5000), File(app.filesDir, "audit.log"))

    val capabilities: com.rishitgoklani.hermes_front_mobile.domain.bridge.CapabilityRegistry = CapabilityCatalog.build(CatalogDeps(
        app, tts, scope, audit, File(app.filesDir, "audit.log"), face, personas, quickActions, ::applyPersona, wake, modelStore::isReady, camera,
        AndroidFaceCounter(), kill, routines, ambient, selfChecks, a2ui,
        { surface -> _screenRequests.tryEmit(surface); if (!(surface == com.rishitgoklani.hermes_front_mobile.domain.a2ui.Surface.Overlay && android.provider.Settings.canDrawOverlays(app))) runCatching { app.startActivity(android.content.Intent(app, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) } }, places, { safeMode.value }, visualWatcher, soundWatcher,
        Triple({ prefs.getBoolean("show_locked", false) }, { v: Boolean -> prefs.edit().putBoolean("show_locked", v).apply() }, { runCatching { app.startActivity(android.content.Intent(app, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) } }),
        macros, { runCapability }, { capabilitiesRef() }, onFaceSeen = { scope.launch { engine.onEvent(DeviceEvent.FaceSeen()) } }, avatar = avatar, pip = { pipRequest }, avatarCatalog = avatarCatalog, avatarInstaller = avatarInstaller,
        avatarLibraryTool = com.rishitgoklani.hermes_front_mobile.domain.avatar.library.avatarLibraryCapability(avatarLibrary, avatarSlot, selectAvatar, cycleAvatar, removeAvatar),
        notificationHistory = notificationHistory, notificationSource = notificationSource, poster = notificationPoster, extra = extras.capabilities, video = camera))
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

    /** #414: the A2UI overlay surface as a real window above other apps; [overlayController].fallback tells the in-app UI to draw it itself when the window cannot be used. */
    val overlayController = com.rishitgoklani.hermes_front_mobile.domain.overlay.A2uiOverlayController(a2ui, A2uiWindowOverlay(app, a2ui, statusProvider) { act -> scope.launch { handleUiAction(act) } }, scope).also { it.start() }

    private val placeTracker = com.rishitgoklani.hermes_front_mobile.domain.sense.PlaceTracker()
    private fun hourNow() = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

    // Persona behaviors (#322 #325 #326 #332-#338): one registry, one hub; quiet context gates speech (#333).
    private val quietContext = com.rishitgoklani.hermes_front_mobile.data.presence.AndroidQuietContext(app, hour = ::hourNow)
    private val escalation = com.rishitgoklani.hermes_front_mobile.domain.presence.Escalation()
    private val taskSupervisor = com.rishitgoklani.hermes_front_mobile.domain.presence.TaskSupervisor(scope)
    private val behaviors = com.rishitgoklani.hermes_front_mobile.domain.presence.BehaviorRegistry(listOf(
        com.rishitgoklani.hermes_front_mobile.domain.presence.StatusDisplay(),
        com.rishitgoklani.hermes_front_mobile.domain.presence.ToneResponder(),
        com.rishitgoklani.hermes_front_mobile.domain.presence.GreetingBehavior(com.rishitgoklani.hermes_front_mobile.domain.face.ArrivalGreeter(hour = ::hourNow), hour = ::hourNow),
        com.rishitgoklani.hermes_front_mobile.domain.presence.AttentionCue(),
        com.rishitgoklani.hermes_front_mobile.domain.presence.ProactiveSuggestions(com.rishitgoklani.hermes_front_mobile.data.presence.AndroidUpcomingEvents(app), hour = ::hourNow),
        escalation, taskSupervisor,
        com.rishitgoklani.hermes_front_mobile.domain.presence.AmbientAwareness(com.rishitgoklani.hermes_front_mobile.data.presence.AndroidAmbientSense(app) {
            runCatching { com.rishitgoklani.hermes_front_mobile.data.capabilities.soundLevelCapability().executor.execute(kotlinx.serialization.json.JsonObject(emptyMap())).data?.get("peak_dbfs")?.let { (it as kotlinx.serialization.json.JsonPrimitive).content.toDouble().toInt() } }.getOrNull()
        }),
        com.rishitgoklani.hermes_front_mobile.domain.presence.RoutineLearning(com.rishitgoklani.hermes_front_mobile.data.presence.FileAuditSource(File(app.filesDir, "audit.log")), hour = ::hourNow, utcOffsetMs = { java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong() }),
    ))
    private val hub = com.rishitgoklani.hermes_front_mobile.domain.presence.BehaviorHub(behaviors, quietContext, com.rishitgoklani.hermes_front_mobile.domain.presence.BodyCueSink(tts, face, runCapability))
    init {
        capabilities.register(com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.quietStatusCapability(quietContext, hub))
        capabilities.register(com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.alertCapability(escalation, hub))
        capabilities.register(com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.taskCapability(taskSupervisor, { capabilities }, { runCapability }))
        capabilities.register(com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.routineSuggestCapability(com.rishitgoklani.hermes_front_mobile.data.presence.FileAuditSource(File(app.filesDir, "audit.log")), utcOffsetMs = { java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong() }))
    }
    private val fusion = com.rishitgoklani.hermes_front_mobile.domain.sense.ArrivalFusion()
    init {
        // A model still PENDING means the app died before it proved itself: mark it BROKEN (the library shows it) and show the eyes.
        kotlinx.coroutines.runBlocking(Dispatchers.IO) { com.rishitgoklani.hermes_front_mobile.domain.avatar.library.ReconcilePending(avatarLibrary, avatarSlot)() }
        // Avatar safety: if the process died twice in a row while the 3D avatar was on (the phone is memory-starved), fall back to the eyes face.
        val d = com.rishitgoklani.hermes_front_mobile.domain.avatar.AvatarSafety.onStart(prefs.getBoolean("avatar_mode", false), prefs.getInt("avatar_unclean", 0))
        prefs.edit().putInt("avatar_unclean", d.uncleanCount).apply()
        if (!d.avatarOn) kotlinx.coroutines.runBlocking(Dispatchers.IO) { avatarSlot.set(com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarSlot.Eyes) }
        scope.launch { kotlinx.coroutines.delay(60_000); prefs.edit().putInt("avatar_unclean", 0).apply() }
        // The old single model.vrm becomes the first library entry (once), then the legacy files go.
        scope.launch(Dispatchers.IO) {
            val legacy = File(avatarDir, "model.vrm")
            if (legacy.isFile) {
                val bytes = runCatching { legacy.readBytes() }.getOrNull()
                val entry = com.rishitgoklani.hermes_front_mobile.domain.avatar.library.MigrateLegacyAvatar(avatarLibrary, importAvatar, markProven, selectAvatar)(bytes, prefs.getBoolean("avatar_mode", false))
                if (entry != null || avatarLibrary.all().isNotEmpty()) { legacy.delete(); File(avatarDir, "model.prev.vrm").delete(); File(avatarDir, "snap.png").delete() }
            }
        }
        // The slot is the truth; AvatarController.enabled mirrors it (and the avatar_mode tool flips the slot back through the controller).
        scope.launch { avatarSlot.slot().collect { slot -> avatar.setEnabled(slot is com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarSlot.Model); prefs.edit().putBoolean("avatar_mode", slot is com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarSlot.Model).apply() } }
        scope.launch {
            avatar.state.map { it.enabled }.distinctUntilChanged().drop(1).collect { want ->
                val cur = avatarSlot.current(); val isModel = cur is com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarSlot.Model
                if (want && !isModel) {
                    val last = avatarLibrary.all().filter { it.status != com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarStatus.BROKEN }.maxByOrNull { it.lastUsedAt }
                    if (last != null) selectAvatar(com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarSlot.Model(last.id)) else avatar.setEnabled(false)
                } else if (!want && isModel) selectAvatar(com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarSlot.Eyes)
            }
        }
        scope.launch { face.state.collect { avatar.setMood(it.mood); avatar.look(it.lookX, it.lookY) } }
        scope.launch { tts.openness.collect { avatar.setMouth(it) } }
    }
    private var sensing = false
    fun startSensing() {
        if (safeMode.value || sensing) return; sensing = true
        // Geofence polling: battery- and distance-aware interval, backs off when location fixes fail (see GeofencePoller).
        val poller = com.rishitgoklani.hermes_front_mobile.domain.sense.GeofencePoller(places, { locationOnce(app) }, com.rishitgoklani.hermes_front_mobile.data.capabilities.AndroidBatteryStatusReader(app), placeTracker, { !kill.isActive() }) { engine.onEvent(it) }
        scope.launch { kotlinx.coroutines.delay(30_000); while (true) kotlinx.coroutines.delay(poller.pollOnce()) }
        engine.observe { e -> extras.onEvent(e) }
        extras.start()
        engine.observe { e ->
            if (!kill.isActive()) hub.dispatch(com.rishitgoklani.hermes_front_mobile.domain.presence.Input.Device(e))
            if (e is DeviceEvent.WifiDisconnected) fusion.wifiLost()
            val sig = when (e) {
                is DeviceEvent.FaceSeen -> com.rishitgoklani.hermes_front_mobile.domain.sense.PresenceSignal.Face
                is DeviceEvent.Sound -> com.rishitgoklani.hermes_front_mobile.domain.sense.PresenceSignal.Sound
                is DeviceEvent.Motion -> com.rishitgoklani.hermes_front_mobile.domain.sense.PresenceSignal.Motion
                is DeviceEvent.VisualChange -> com.rishitgoklani.hermes_front_mobile.domain.sense.PresenceSignal.Visual
                is DeviceEvent.WifiConnected -> com.rishitgoklani.hermes_front_mobile.domain.sense.PresenceSignal.Wifi
                else -> null
            }
            sig?.let { s -> fusion.signal(s)?.let { p -> audit.record("presence", if (p.arrived) "arrived" else "departed", "event"); scope.launch { engine.onEvent(p) } } }
        }
        scope.launch { while (true) { kotlinx.coroutines.delay(60_000); fusion.tick()?.let { p -> audit.record("presence", "departed", "event"); engine.onEvent(p) } } }
        scope.launch { hub.runTicks(15_000) }
        scope.launch { wake.events.collect { v ->
            if (kill.isActive()) return@collect
            when (v) {
                is com.rishitgoklani.hermes_front_mobile.domain.VoiceEvent.State -> hub.dispatch(com.rishitgoklani.hermes_front_mobile.domain.presence.Input.State(v.state))
                is com.rishitgoklani.hermes_front_mobile.domain.VoiceEvent.Heard -> hub.dispatch(com.rishitgoklani.hermes_front_mobile.domain.presence.Input.Heard(v.text))
                else -> {}
            }
        } }
        motion.start()
        DeskbotAccessibilityService.onKillCombo = { kill.trigger("volume-down x3") }
        DeviceEventSource(app, scope) { e ->
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
    fun liveSettingsViewModel(owner: ViewModelStoreOwner): com.rishitgoklani.hermes_front_mobile.ui.settings.LiveSettingsViewModel = vm(owner) { com.rishitgoklani.hermes_front_mobile.ui.settings.LiveSettingsViewModel(liveSettings) }
    fun settingsViewModel(owner: ViewModelStoreOwner): SettingsViewModel = vm(owner) { SettingsViewModel(settings, com.rishitgoklani.hermes_front_mobile.ui.settings.AboutInfo(BuildConfig.VERSION_NAME, BuildConfig.BUILD_TYPE)) }
    fun onboardingViewModel(owner: ViewModelStoreOwner): com.rishitgoklani.hermes_front_mobile.ui.onboarding.OnboardingViewModel = vm(owner) { com.rishitgoklani.hermes_front_mobile.ui.onboarding.OnboardingViewModel(settings, debugSource) }
    fun debugViewModel(owner: ViewModelStoreOwner): DebugViewModel = vm(owner) { DebugViewModel(debugSource, crashLog) }
    fun avatarLibraryViewModel(owner: ViewModelStoreOwner): com.rishitgoklani.hermes_front_mobile.ui.avatar.AvatarLibraryViewModel = vm(owner) { com.rishitgoklani.hermes_front_mobile.ui.avatar.AvatarLibraryViewModel(avatarCatalog, avatarInstaller, avatarLibrary, avatarSlot, selectAvatar, removeAvatar) }
    fun avatarCarouselViewModel(owner: ViewModelStoreOwner): com.rishitgoklani.hermes_front_mobile.ui.avatar.AvatarCarouselViewModel = vm(owner) {
        val am = app.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        com.rishitgoklani.hermes_front_mobile.ui.avatar.AvatarCarouselViewModel(avatarLibrary, avatarSlot, avatarFiles, cycleAvatar, ::proveLater, avatarSnapshots, { android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.lowMemory }, log = { android.util.Log.d("DeskbotAvatar", it) }, nativeGl = com.rishitgoklani.hermes_front_mobile.data.avatar.DataStoreNativeGlSwitch.create(app))
    }

    private inline fun <reified T : ViewModel> vm(owner: ViewModelStoreOwner, crossinline create: () -> T): T =
        ViewModelProvider(owner, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
        })[T::class.java]
}

class DeskbotApp : Application() {
    lateinit var container: AppContainer
    /** Created before anything else so even a crash while the container is built is recorded. */
    val crashLog by lazy { com.rishitgoklani.hermes_front_mobile.domain.diagnostics.CrashLog(com.rishitgoklani.hermes_front_mobile.data.FileCrashStore(File(filesDir, "last_crash.txt"))) }

    /** Records the crash (time, thread, stack, version; credentials redacted) to filesDir, then lets the previous handler finish the job. */
    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            crashLog.record(thread.name, error, "${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})", System.currentTimeMillis())
            previous?.uncaughtException(thread, error)
        }
    }

    override fun onCreate() { super.onCreate(); installCrashHandler(); container = AppContainer(this, crashLog); container.startBridge(); container.restorePersona(); container.startSensing() }
}
