package com.rishitgoklani.hermes_front_mobile

import android.app.Application
import com.rishitgoklani.hermes_front_mobile.data.capabilities.AndroidBatteryStatusReader
import com.rishitgoklani.hermes_front_mobile.data.media.AndroidMediaSessionPort
import com.rishitgoklani.hermes_front_mobile.data.notifications.AndroidLockScreenNotifier
import com.rishitgoklani.hermes_front_mobile.data.state.FileStateStore
import com.rishitgoklani.hermes_front_mobile.domain.SpeechSynthesizer
import com.rishitgoklani.hermes_front_mobile.domain.behavior.RunCapability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.lockScreenAnswerCapability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.notificationPriorityCapabilities
import com.rishitgoklani.hermes_front_mobile.domain.media.IdentifyNowPlaying
import com.rishitgoklani.hermes_front_mobile.domain.media.nowPlayingCapability
import com.rishitgoklani.hermes_front_mobile.domain.notifications.NotificationPoster
import com.rishitgoklani.hermes_front_mobile.domain.notifications.NotificationSource
import com.rishitgoklani.hermes_front_mobile.domain.notifications.RankNotifications
import com.rishitgoklani.hermes_front_mobile.domain.notifications.SilenceRules
import com.rishitgoklani.hermes_front_mobile.domain.power.ChargeCapGuard
import com.rishitgoklani.hermes_front_mobile.domain.power.ChargeCapTask
import com.rishitgoklani.hermes_front_mobile.domain.power.chargeLimitCapability
import com.rishitgoklani.hermes_front_mobile.domain.reminders.ContextReminderBook
import com.rishitgoklani.hermes_front_mobile.domain.reminders.ContextReminderEngine
import com.rishitgoklani.hermes_front_mobile.domain.reminders.contextReminderCapability
import com.rishitgoklani.hermes_front_mobile.domain.routines.BedtimeTemplate
import com.rishitgoklani.hermes_front_mobile.domain.routines.DeviceEvent
import com.rishitgoklani.hermes_front_mobile.domain.routines.FocusFilter
import com.rishitgoklani.hermes_front_mobile.domain.routines.FocusNotificationGuard
import com.rishitgoklani.hermes_front_mobile.domain.routines.FocusTemplate
import com.rishitgoklani.hermes_front_mobile.domain.routines.RoutineTemplateRegistry
import com.rishitgoklani.hermes_front_mobile.domain.routines.TemplateRunner
import com.rishitgoklani.hermes_front_mobile.domain.routines.WakeUpTemplate
import com.rishitgoklani.hermes_front_mobile.domain.routines.focusFilterCapability
import com.rishitgoklani.hermes_front_mobile.domain.routines.routineTemplateCapabilities
import com.rishitgoklani.hermes_front_mobile.domain.safety.KillSwitch
import com.rishitgoklani.hermes_front_mobile.domain.state.PeriodicRunner
import com.rishitgoklani.hermes_front_mobile.domain.system.ScheduledHealthCheck
import com.rishitgoklani.hermes_front_mobile.domain.system.healthCheckCapability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Wiring for the self-contained features (notification ranking, now playing, lock-screen answer, charge cap, scheduled health checks,
 * context reminders, routine templates, focus filter). One place builds the instances so the tools and the background tick share state.
 * [runCapability] is a provider because the registry that contains these tools is built after this class.
 */
class ExtraFeatures(
    app: Application, private val scope: CoroutineScope, tts: SpeechSynthesizer, private val kill: KillSwitch, runCapability: () -> RunCapability,
    notifications: NotificationSource, poster: NotificationPoster, audit: File,
) {
    private val store = FileStateStore(File(app.filesDir, "state"))
    private val battery = AndroidBatteryStatusReader(app)
    private val silence = SilenceRules(store)
    private val chargeGuard = ChargeCapGuard(store)
    private val chargeTask = ChargeCapTask(chargeGuard, battery, poster, tts)
    private val healthCheck = ScheduledHealthCheck({ healthInput(app, kill, audit) }, store, poster, tts)
    private val reminderBook = ContextReminderBook(store)
    private val reminders = ContextReminderEngine(reminderBook, poster, tts)
    private val focus = FocusFilter(store)
    private val focusGuard = FocusNotificationGuard(focus, notifications, app.packageName)
    private val templates = RoutineTemplateRegistry(listOf(WakeUpTemplate(), BedtimeTemplate(), FocusTemplate()))
    private val templateRunner = TemplateRunner(templates, runCapability, scope)
    private val periodic = PeriodicRunner(listOf(chargeTask, healthCheck, reminders)) { name, e -> android.util.Log.w("DeskbotTask", "$name failed: ${e.message}") }

    val capabilities: List<Capability> = buildList {
        addAll(notificationPriorityCapabilities(RankNotifications(notifications, silence), silence))
        add(nowPlayingCapability(IdentifyNowPlaying(AndroidMediaSessionPort(app))))
        add(lockScreenAnswerCapability(AndroidLockScreenNotifier(app, R.mipmap.ic_launcher)))
        add(chargeLimitCapability(chargeGuard, battery, chargeTask))
        add(healthCheckCapability(healthCheck))
        add(contextReminderCapability(reminderBook))
        addAll(routineTemplateCapabilities(templates, templateRunner)); add(focusFilterCapability(focus))
    }

    /** Observer for the routine engine: place/charger events arm reminders, notifications are filtered during focus, plugging in checks the cap. */
    suspend fun onEvent(e: DeviceEvent) {
        if (kill.isActive()) return
        reminders.onEvent(e); focusGuard.onEvent(e)
        if (e is DeviceEvent.ChargerConnected) chargeTask.tick(System.currentTimeMillis())
    }

    /** One coarse loop for all periodic tasks (each task gates itself on its own interval). */
    fun start() {
        scope.launch { while (true) { delay(60_000); if (!kill.isActive()) periodic.tickAll(System.currentTimeMillis()) } }
    }
}
