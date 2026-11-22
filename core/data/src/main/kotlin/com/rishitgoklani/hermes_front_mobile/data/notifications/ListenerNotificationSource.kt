package com.rishitgoklani.hermes_front_mobile.data.notifications

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.rishitgoklani.hermes_front_mobile.domain.notifications.NotificationHistory
import com.rishitgoklani.hermes_front_mobile.domain.notifications.NotificationRecord
import com.rishitgoklani.hermes_front_mobile.domain.notifications.NotificationSource

class ListenerNotificationSource(private val context: Context, private val history: NotificationHistory) : NotificationSource {
    init { DeskbotNotificationListener.listeners += { history.add(map(it)) } }

    private fun listener() = DeskbotNotificationListener.instance ?: error("Notification access not enabled")

    fun map(sbn: StatusBarNotification): NotificationRecord {
        val n = sbn.notification
        val extras = n.extras
        val label = runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrDefault(sbn.packageName)
        val actions = n.actions.orEmpty()
        return NotificationRecord(
            key = sbn.key, pkg = sbn.packageName, app = label,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty(),
            time = sbn.postTime, actions = actions.mapNotNull { it.title?.toString() },
            ongoing = sbn.isOngoing, canReply = actions.any { it.remoteInputs?.isNotEmpty() == true },
        )
    }

    override suspend fun active() = listener().activeNotifications.map(::map)

    override suspend fun dismiss(key: String): Boolean = runCatching { listener().cancelNotification(key); true }.getOrDefault(false)

    override suspend fun dismissAll(): Boolean = runCatching { listener().cancelAllNotifications(); true }.getOrDefault(false)

    override suspend fun act(key: String, actionTitle: String, reply: String?): Boolean {
        val sbn = listener().activeNotifications.firstOrNull { it.key == key } ?: return false
        val action = sbn.notification.actions.orEmpty().firstOrNull { it.title?.toString().equals(actionTitle, true) } ?: return false
        return runCatching {
            val inputs = action.remoteInputs
            if (reply != null && inputs?.isNotEmpty() == true) {
                val intent = Intent()
                val bundle = Bundle().also { b -> inputs.forEach { b.putCharSequence(it.resultKey, reply) } }
                RemoteInput.addResultsToIntent(inputs, intent, bundle)
                action.actionIntent.send(context, 0, intent)
            } else action.actionIntent.send()
            true
        }.getOrDefault(false)
    }
}
