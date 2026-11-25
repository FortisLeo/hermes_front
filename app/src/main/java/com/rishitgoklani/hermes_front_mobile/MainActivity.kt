package com.rishitgoklani.hermes_front_mobile

import android.os.Bundle
import android.view.WindowManager
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarSlot
import androidx.compose.foundation.verticalScroll
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.size
import com.rishitgoklani.hermes_front_mobile.ui.onboarding.OnboardingScreen
import com.rishitgoklani.hermes_front_mobile.ui.onboarding.PermissionGroup
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.rishitgoklani.hermes_front_mobile.domain.BotState
import com.rishitgoklani.hermes_front_mobile.domain.a2ui.Surface as UiSurface
import com.rishitgoklani.hermes_front_mobile.domain.behavior.IdleBehavior
import com.rishitgoklani.hermes_front_mobile.domain.behavior.SleepSchedule
import com.rishitgoklani.hermes_front_mobile.domain.face.Greetings
import com.rishitgoklani.hermes_front_mobile.domain.face.Mood
import com.rishitgoklani.hermes_front_mobile.domain.face.Reaction
import com.rishitgoklani.hermes_front_mobile.ui.a2ui.A2uiHost
import com.rishitgoklani.hermes_front_mobile.ui.chat.ChatScreen
import com.rishitgoklani.hermes_front_mobile.ui.face.FaceClock
import com.rishitgoklani.hermes_front_mobile.ui.face.FaceScreen
import com.rishitgoklani.hermes_front_mobile.ui.settings.DebugScreen
import com.rishitgoklani.hermes_front_mobile.ui.settings.SettingsScreen
import com.rishitgoklani.hermes_front_mobile.ui.theme.Hermes_front_mobileTheme
import com.rishitgoklani.hermes_front_mobile.ui.voice.VoiceScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

/** Everything lives on the face. Chat, voice, debug and settings sit in a hidden drawer (swipe in from the left edge). */
private enum class Section(val label: String) { Chat("Chat"), Voice("Voice"), Debug("Debug"), Settings("Settings"), Avatar("Avatars") }

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as DeskbotApp).container }
    private val library by lazy { container.avatarLibraryViewModel(this) }
    /** System file picker for a .vrm (no MIME type exists for VRM, so any file is offered and the model check decides). */
    private val pickVrm = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lifecycleScope.launch {
            val name = runCatching { contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } }.getOrNull() ?: "file"
            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { contentResolver.openInputStream(uri)?.use { it.readNBytes(com.rishitgoklani.hermes_front_mobile.domain.avatar.VrmLicense.MAX_BYTES + 1) } }.getOrNull() }
            library.importFile(name, bytes)
        }
    }

    private val onboarding by lazy { container.onboardingViewModel(this) }
    private val permissionsAsked = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { refreshPermissions() }

    private fun androidPermission(g: PermissionGroup): String? = when (g) {
        PermissionGroup.Microphone -> android.Manifest.permission.RECORD_AUDIO
        PermissionGroup.Camera -> android.Manifest.permission.CAMERA
        PermissionGroup.Notifications -> if (android.os.Build.VERSION.SDK_INT >= 33) android.Manifest.permission.POST_NOTIFICATIONS else null
    }
    private fun isGranted(g: PermissionGroup) = androidPermission(g)?.let { checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED } ?: true
    private fun refreshPermissions() = onboarding.setPermissions(PermissionGroup.entries.associateWith(::isGranted))
    /** Asks only for what is still missing, and only the groups the intro explained. */
    private fun requestMissingPermissions() { val missing = PermissionGroup.entries.filter { !isGranted(it) }.mapNotNull(::androidPermission); if (missing.isNotEmpty()) permissionsAsked.launch(missing.toTypedArray()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); StartupTrace.mark("activityOnCreate")
        enableEdgeToEdge()
        enterImmersive()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        refreshPermissions()
        seedFromDebugIntent()
        BotService.start(this)
        StartupTrace.mark("setContent")
        setContent {
            val theme by container.theme.collectAsStateWithLifecycle()
            val primary = runCatching { Color(android.graphics.Color.parseColor(theme.primaryHex)) }.getOrDefault(Color.Cyan)
            val background = runCatching { Color(android.graphics.Color.parseColor(theme.backgroundHex)) }.getOrDefault(Color(0xFF05070A))
            Hermes_front_mobileTheme {
                MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = primary)) { Root(background) }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Root(background: Color) {
        val scope = rememberCoroutineScope()
        val drawer = rememberDrawerState(DrawerValue.Closed)
        var section by remember { mutableStateOf<Section?>(Section.entries.firstOrNull { it.name.equals(intent?.getStringExtra("section"), true) }) }
        val chat = container.chatViewModel(this); val settings = container.settingsViewModel(this); val voice = container.voiceViewModel(this); val debug = container.debugViewModel(this)
        val carousel = container.avatarCarouselViewModel(this)
        androidx.compose.runtime.LaunchedEffect(Unit) { StartupTrace.mark("firstComposition") }
        val head = remember { mutableStateOf(Triple(0.5f, 0.3f, 0.15f)) }
        val engineFaceEffects = remember { mutableStateOf(true) }   // false once the engine reports it cannot draw them
        val viewerEvents = remember(carousel) { object : com.rishitgoklani.hermes_front_mobile.ui.avatar.ViewerEvents {
            override fun onShown(key: String, ms: Long, wasStaged: Boolean) = carousel.onShown(key, ms, wasStaged)
            override fun onStaged(key: String, ms: Long) = carousel.onStaged(key, ms)
            override fun onError(key: String, message: String) = carousel.onError(key, message)
            override fun onFatal(message: String) = carousel.onFatal(message)
            override fun onNodes(names: List<String>) = container.avatar.setNodes(names)
            override fun onHead(x: Float, y: Float, size: Float) { head.value = Triple(x, y, size) }
            override fun onFaceEffectsUnavailable(message: String) { engineFaceEffects.value = false }
        } }
        val face by container.face.state.collectAsStateWithLifecycle()
        val chatState by chat.state.collectAsStateWithLifecycle(); val voiceState by voice.state.collectAsStateWithLifecycle()
        val wakeOn by container.wake.enabled.collectAsStateWithLifecycle(); val wakeBot by container.wake.state.collectAsStateWithLifecycle()
        val bot = listOf(voiceState.bot, wakeBot, chatState.bot).firstOrNull { it != BotState.Idle } ?: BotState.Idle
        val screens by container.a2ui.screens.collectAsStateWithLifecycle(); val uiValues by container.a2ui.values.collectAsStateWithLifecycle()
        var actions by remember { mutableStateOf(emptyList<com.rishitgoklani.hermes_front_mobile.domain.behavior.QuickAction>()) }
        val lastInteraction = remember { mutableLongStateOf(System.currentTimeMillis()) }
        var statusLine by remember { mutableStateOf("") }

        LaunchedEffect(section) { actions = container.quickActions.all() }
        LaunchedEffect(Unit) { while (true) { delay(1000); container.a2ui.sweep() } }                      // expire generative screens
        LaunchedEffect(Unit) {                                                                              // battery on the face
            while (true) { statusLine = container.statusProvider.read("battery")?.second.orEmpty().let { if (it.isNotEmpty()) "battery $it" else "" }; delay(30_000) }
        }
        LaunchedEffect(bot) {                                                                               // idle micro-behaviours + night sleep
            val schedule = SleepSchedule()
            while (bot == BotState.Idle) {
                delay(8_000L + (Math.random() * 12_000).toLong())
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY); val idleFor = System.currentTimeMillis() - lastInteraction.longValue
                if (schedule.isSleepTime(hour) && idleFor > 60_000) { if (container.face.state.value.mood != Mood.Sleepy) container.face.setMood(Mood.Sleepy) }
                else IdleBehavior.next(hour, schedule, Math.random().toFloat())?.let { container.face.react(it) }
            }
        }
        LaunchedEffect(bot) { if (bot != BotState.Idle) { lastInteraction.longValue = System.currentTimeMillis(); wake() } }

        fun interact() {
            val away = System.currentTimeMillis() - lastInteraction.longValue
            lastInteraction.longValue = System.currentTimeMillis(); wake(); container.face.react(Reaction.Giggle)
            Greetings.greeting(Calendar.getInstance().get(Calendar.HOUR_OF_DAY), away)?.let { container.speaker.enqueue(it) }
        }

        val mouth by container.mouth.openness.collectAsState()
        val hermesLine = debug.state.collectAsStateWithLifecycle().value.snapshot?.let { if (it.hermesHealthy) "Hermes online" else "Hermes unreachable" }.orEmpty()
        // The overlay surface lives in its own window above other apps; it only draws here when that window cannot be used (no permission or the window failed).
        val overlayFallback by container.overlayController.fallback.collectAsStateWithLifecycle()
        val liveScreen = screens[UiSurface.Full] ?: (if (overlayFallback) screens[UiSurface.Overlay] else null) ?: screens[UiSurface.Desk]
        BackHandler(enabled = section != null) { section = null }

        ModalNavigationDrawer(
            drawerState = drawer, gesturesEnabled = section == null,
            drawerContent = {
                ModalDrawerSheet {
                  Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    Text("Deskbot", Modifier.padding(20.dp).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                    Section.entries.forEach { s -> NavigationDrawerItem(label = { Text(s.label) }, selected = false, onClick = { section = s; scope.launch { drawer.close() } }, modifier = Modifier.padding(horizontal = 12.dp)) }
                    NavigationDrawerItem(label = { Text(if (wakeOn) "Stop listening" else "Always listen") }, selected = false, onClick = { container.wake.toggle(); scope.launch { drawer.close() } }, modifier = Modifier.padding(horizontal = 12.dp))
                    Text("Swipe left or press back to hide", Modifier.padding(20.dp), style = MaterialTheme.typography.bodySmall)
                  }
                }
            },
        ) {
            Box(Modifier.fillMaxSize().background(background)) {
                // Screen readers: announces mood / bot state / Hermes connection changes without drawing anything.
                Box(Modifier.size(1.dp).semantics { liveRegion = LiveRegionMode.Polite; contentDescription = "Deskbot is ${bot.name.lowercase()}, mood ${face.mood.name.lowercase()}. $hermesLine".trim() })
                val av by container.avatar.state.collectAsStateWithLifecycle(); val carouselUi by carousel.ui.collectAsStateWithLifecycle()
                val settingsUi by settings.state.collectAsStateWithLifecycle()
                val renderQuality = settingsUi.displayQuality
                androidx.compose.runtime.LaunchedEffect(carouselUi.loaded) { if (carouselUi.loaded) StartupTrace.mark("carouselLoaded") }
                androidx.compose.runtime.LaunchedEffect(carouselUi.active != null) { if (carouselUi.active != null) StartupTrace.mark("avatarRequested") }
                // Blush, tears, sweat and anger marks are drawn by the engine on the 3D face; the 2D overlay keeps hearts, sparkles, Zzz and the like.
                val faceEffects = com.rishitgoklani.hermes_front_mobile.ui.face.EyeStyles.compute(bot, face).effects
                FaceScreen(bot, face, actions, mouth = mouth, faceContent = if (!carouselUi.loaded) ({ Box(Modifier.fillMaxSize().background(background)) })   // saved slot not read yet: draw nothing, so the default eyes never flash
                    else if (carouselUi.active != null) ({
                    Box(Modifier.fillMaxSize()) {
                        // A new quality level rebuilds the GL view so the new switches (outlines, physics, texture format) take effect.
                        androidx.compose.runtime.key(renderQuality) { com.rishitgoklani.hermes_front_mobile.ui.avatar.AvatarView(av, container.avatarDir, container.avatarLibraryDir, carouselUi.active, carouselUi.staged, viewerEvents, snapshots = container.avatarSnapshots, displayQuality = renderQuality, effects = faceEffects, activity = bot.name) }
                        // While the viewer is still loading the model it stays behind a plain cover that fades away: the eyes must not flash first.
                        androidx.compose.animation.AnimatedVisibility(visible = !carouselUi.ready, enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(300))) {
                            Box(Modifier.fillMaxSize().background(background))
                        }
                        com.rishitgoklani.hermes_front_mobile.ui.face.FaceEffectsOverlay(if (engineFaceEffects.value) faceEffects - com.rishitgoklani.hermes_front_mobile.ui.avatar.EngineInputMapper.engineEffects else faceEffects, head.value.first, head.value.second, head.value.third)
                    } }) else null, onAction = { a -> scope.launch { container.runCapability(a.tool, a.args) } }, onTap = { interact() }, backgroundColor = background,
                    onSwipeNext = if (section == null && liveScreen == null && carouselUi.slotCount > 1) (suspend { carousel.swipeNextAndAwait() }) else null,
                    overlay = {
                        if (liveScreen == null) { if (!inPip.value && carouselUi.loaded) { if (carouselUi.slot is AvatarSlot.Model) FaceClock(statusLine, Modifier.align(Alignment.TopStart), compact = true) else FaceClock(statusLine, Modifier.align(Alignment.BottomCenter)) } }
                        else Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 420.dp).padding(12.dp)) {
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))) {
                                Column {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = { container.a2ui.clear() }, Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Hide this card" }) { Text("Hide") } }
                                    A2uiHost(liveScreen.root, uiValues, container.a2ui::setValue, { act -> scope.launch { container.handleUiAction(act) } }, container.statusProvider)
                                }
                            }
                        }
                    })
                if (carouselUi.nativeDisabled) Surface(Modifier.align(Alignment.TopCenter).padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite }, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("The 3D avatar was turned off after a graphics error.", color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = carousel::retryNative, Modifier.heightIn(min = 48.dp)) { Text("Retry") }
                    }
                }
                section?.let { s ->
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { section = null }, Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Close ${s.label}" }) { Text("✕  Close") }; Text(s.label, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                            }
                            Box(Modifier.weight(1f)) {
                                when (s) {
                                    Section.Chat -> { val st by chat.state.collectAsStateWithLifecycle(); ChatScreen(st, chat::send) }
                                    Section.Voice -> VoiceScreen(voiceState, voice::talk, voice::cancel, Modifier, wakeOn,
                                        if (wakeOn && !container.modelStore.isReady()) "Downloading speech model (40 MB) on first use…" else if (wakeOn) "Listening: $wakeBot" else "", onWakeToggle = { container.wake.toggle() })
                                    Section.Debug -> { val st by debug.state.collectAsStateWithLifecycle(); val killed by container.kill.active.collectAsStateWithLifecycle(); val safe by container.safeMode.collectAsStateWithLifecycle()
                                        DebugScreen(st, debug::refresh, Modifier, killed, container::resetKill, safe, container::exitSafeMode,
                                            onCopyCrash = { text -> (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Deskbot crash report", text)) }, onClearCrash = debug::clearCrash) }
                                    Section.Avatar -> { val lib by library.state.collectAsStateWithLifecycle()
                                        LaunchedEffect(lib.message) { if (lib.message != null && !lib.messageIsError) { kotlinx.coroutines.delay(6000); library.consumeMessage() } }
                                        com.rishitgoklani.hermes_front_mobile.ui.avatar.AvatarLibraryScreen(lib, onUse = { slot -> library.use(slot); section = null }, onRetry = library::retry, onRemove = library::removeAvatar, onUpload = { pickVrm.launch(arrayOf("*/*")) },
                                            onLoad = library::load, onSelect = library::select, onQuery = library::onQuery, onInstall = library::install, thumbnail = library::thumbnail, onDismissMessage = library::consumeMessage) }
                                    Section.Settings -> { val st by settings.state.collectAsStateWithLifecycle(); SettingsScreen(st, settings::onBaseUrl, settings::onApiKey, settings::save, onDisplayQuality = settings::onDisplayQuality, onShowIntro = { section = null; onboarding.replay() }) }
                                }
                            }
                        }
                    }
                }
            }
        }
        val intro by onboarding.state.collectAsStateWithLifecycle()
        if (intro.visible) OnboardingScreen(intro, onNext = onboarding::next, onBack = onboarding::back, onSkip = onboarding::skip, onRequestPermissions = ::requestMissingPermissions, onTestConnection = onboarding::testConnection)
    }

    /** Hide status and navigation bars; they reappear transiently on an edge swipe and hide again. */
    private fun enterImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply { systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE; hide(WindowInsetsCompat.Type.systemBars()) }
        if (android.os.Build.VERSION.SDK_INT >= 28) window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
    }

    private val inPip = androidx.compose.runtime.mutableStateOf(false)
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) { super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig); inPip.value = isInPictureInPictureMode }

    override fun onDestroy() { container.pipRequest = null; super.onDestroy() }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        container.pipRequest = { runCatching { enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().setAspectRatio(android.util.Rational(1, 1)).build()) }.getOrDefault(false) }
        val locked = container.showWhenLocked()
        setShowWhenLocked(locked); setTurnScreenOn(locked)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) enterImmersive() }

    /** Leave sleepy mood when the user interacts (restores the active persona's mood). */
    private fun wake() { if (container.face.state.value.mood == Mood.Sleepy) lifecycleScope.launch { container.face.setMood(container.personas.active().mood()) } }

    /** Debug builds only: `--es api_key K` / `--es bridge_token T` seed settings, `--es say TEXT` speaks, for adb-driven testing. */
    private fun seedFromDebugIntent() {
        if (!BuildConfig.DEBUG) return
        intent?.getStringExtra("say")?.let { container.speaker.enqueue(it) }
        intent?.getStringExtra("bridge_token")?.let { t -> lifecycleScope.launch { container.settings.saveBridgeToken(t) } }
        val key = intent?.getStringExtra("api_key") ?: return
        lifecycleScope.launch { container.settings.save(intent.getStringExtra("base_url") ?: "http://127.0.0.1:8642", key) }
    }
}
