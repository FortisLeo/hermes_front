package com.rishitgoklani.hermes_front_mobile.data.events

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.service.notification.StatusBarNotification
import com.rishitgoklani.hermes_front_mobile.data.notifications.DeskbotNotificationListener
import com.rishitgoklani.hermes_front_mobile.data.ui.DeskbotAccessibilityService
import com.rishitgoklani.hermes_front_mobile.domain.routines.DeviceEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Turns Android broadcasts/callbacks into [DeviceEvent]s for the routine engine. Call [start] once. */
class DeviceEventSource(private val context: Context, private val scope: CoroutineScope, private val emit: suspend (DeviceEvent) -> Unit) {
    private var started = false

    @Suppress("DEPRECATION")
    fun start() {
        if (started) return; started = true
        fun send(e: DeviceEvent) { scope.launch { emit(e) } }
        fun level(): Int = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { i -> i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / i.getIntExtra(BatteryManager.EXTRA_SCALE, 100) } ?: -1
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = when (i.action) {
                Intent.ACTION_POWER_CONNECTED -> send(DeviceEvent.ChargerConnected(level()))
                Intent.ACTION_POWER_DISCONNECTED -> send(DeviceEvent.ChargerDisconnected(level()))
                Intent.ACTION_BATTERY_LOW -> send(DeviceEvent.BatteryLow(level()))
                Intent.ACTION_SCREEN_ON -> send(DeviceEvent.ScreenOn())
                Intent.ACTION_SCREEN_OFF -> send(DeviceEvent.ScreenOff())
                else -> Unit
            }
        }, IntentFilter().apply { listOf(Intent.ACTION_POWER_CONNECTED, Intent.ACTION_POWER_DISCONNECTED, Intent.ACTION_BATTERY_LOW, Intent.ACTION_SCREEN_ON, Intent.ACTION_SCREEN_OFF).forEach(::addAction) })

        val cm = context.getSystemService(ConnectivityManager::class.java)
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        cm.registerNetworkCallback(android.net.NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { send(DeviceEvent.WifiConnected(wifi.connectionInfo?.ssid?.trim('"').orEmpty())) }
            override fun onLost(network: Network) { send(DeviceEvent.WifiDisconnected()) }
        })

        DeskbotNotificationListener.listeners += { sbn: StatusBarNotification ->
            if (!sbn.isOngoing && sbn.packageName != context.packageName) {
                val e = sbn.notification.extras
                val label = runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrDefault(sbn.packageName)
                send(DeviceEvent.NotificationPosted(label, e.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty(), e.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()))
            }
        }

        context.getSystemService(android.content.ClipboardManager::class.java).addPrimaryClipChangedListener {
            val t = runCatching { context.getSystemService(android.content.ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() }.getOrNull()
            if (!t.isNullOrBlank() && t.length < 2000) send(DeviceEvent.Clipboard(t))
        }

        scope.launch { // foreground app changes (accessibility window events), polled cheaply
            var last: String? = null
            while (true) { delay(2000); val p = DeskbotAccessibilityService.lastPackage; if (p != null && p != last) { last = p; emit(DeviceEvent.AppForeground(p)) } }
        }
    }
}
