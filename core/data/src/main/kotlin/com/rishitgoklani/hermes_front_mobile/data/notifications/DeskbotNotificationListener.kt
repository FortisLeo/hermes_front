package com.rishitgoklani.hermes_front_mobile.data.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Keeps a live reference for [NotificationStore]; the store itself is pure and fed from here. */
class DeskbotNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() { instance = this; super.onListenerConnected() }
    override fun onListenerDisconnected() { if (instance === this) instance = null; super.onListenerDisconnected() }
    override fun onNotificationPosted(sbn: StatusBarNotification) { listeners.forEach { it(sbn) } }
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }

    companion object {
        @Volatile var instance: DeskbotNotificationListener? = null
        val listeners = java.util.concurrent.CopyOnWriteArrayList<(StatusBarNotification) -> Unit>()
    }
}
