package com.rishitgoklani.hermes_front_mobile

import android.os.Bundle
import android.view.WindowManager
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
private enum class Section(val label: String) { Chat("Chat"), Voice("Voice"), Debug("Debug"), Settings("Settings") }

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as DeskbotApp).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        enterImmersive()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        seedFromDebugIntent()
        BotService.start(this)
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
        var section by remember { mutableStateOf<Section?>(null) }
        val chat = container.chatViewModel(this); val settings = container.settingsViewModel(this); val voice = container.voiceViewModel(this); val debug = container.debugViewModel(this)
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

        val liveScreen = screens[UiSurface.Full] ?: screens[UiSurface.Overlay] ?: screens[UiSurface.Desk]
        BackHandler(enabled = section != null) { section = null }

        ModalNavigationDrawer(
            drawerState = drawer, gesturesEnabled = section == null,
            drawerContent = {
                ModalDrawerSheet {
                    Text("Deskbot", Modifier.padding(20.dp), style = MaterialTheme.typography.titleLarge)
                    Section.entries.forEach { s -> NavigationDrawerItem(label = { Text(s.label) }, selected = false, onClick = { section = s; scope.launch { drawer.close() } }, modifier = Modifier.padding(horizontal = 12.dp)) }
                    NavigationDrawerItem(label = { Text(if (wakeOn) "Stop listening" else "Always listen") }, selected = false, onClick = { container.wake.toggle(); scope.launch { drawer.close() } }, modifier = Modifier.padding(horizontal = 12.dp))
                    Text("Swipe left or press back to hide", Modifier.padding(20.dp), style = MaterialTheme.typography.bodySmall)
                }
            },
        ) {
            Box(Modifier.fillMaxSize().background(background)) {
                FaceScreen(bot, face, actions, onAction = { a -> scope.launch { container.runCapability(a.tool, a.args) } }, onTap = { interact() }, backgroundColor = background,
                    overlay = {
                        if (liveScreen == null) FaceClock(statusLine, Modifier.align(Alignment.BottomCenter))
                        else Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = 420.dp).padding(12.dp)) {
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))) {
                                Column {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = { container.a2ui.clear() }) { Text("Hide") } }
                                    A2uiHost(liveScreen.root, uiValues, container.a2ui::setValue, { act -> scope.launch { container.handleUiAction(act) } }, container.statusProvider)
                                }
                            }
                        }
                    })
                section?.let { s ->
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { section = null }) { Text("✕  Close") }; Text(s.label, style = MaterialTheme.typography.titleMedium)
                            }
                            Box(Modifier.weight(1f)) {
                                when (s) {
                                    Section.Chat -> { val st by chat.state.collectAsStateWithLifecycle(); ChatScreen(st, chat::send) }
                                    Section.Voice -> VoiceScreen(voiceState, voice::talk, voice::cancel, Modifier, wakeOn,
                                        if (wakeOn && !container.modelStore.isReady()) "Downloading speech model (40 MB) on first use…" else if (wakeOn) "Listening: $wakeBot" else "", onWakeToggle = { container.wake.toggle() })
                                    Section.Debug -> { val st by debug.state.collectAsStateWithLifecycle(); val killed by container.kill.active.collectAsStateWithLifecycle(); val safe by container.safeMode.collectAsStateWithLifecycle()
                                        DebugScreen(st, debug::refresh, Modifier, killed, container::resetKill, safe, container::exitSafeMode) }
                                    Section.Settings -> { val st by settings.state.collectAsStateWithLifecycle(); SettingsScreen(st, settings::onBaseUrl, settings::onApiKey, settings::save) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Hide status and navigation bars; they reappear transiently on an edge swipe and hide again. */
    private fun enterImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply { systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE; hide(WindowInsetsCompat.Type.systemBars()) }
        if (android.os.Build.VERSION.SDK_INT >= 28) window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
    }

    override fun onResume() {
        super.onResume()
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
