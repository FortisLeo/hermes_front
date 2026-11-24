package com.rishitgoklani.hermes_front_mobile.data.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.LockScreenNotifier

/** One replaceable notification with PUBLIC lock-screen visibility (its text shows without unlocking, subject to the user's global lock-screen notification setting). */
class AndroidLockScreenNotifier(private val context: Context, private val iconRes: Int) : LockScreenNotifier {
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    override fun show(title: String, text: String): Int {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Deskbot lock screen", NotificationManager.IMPORTANCE_DEFAULT).apply { lockscreenVisibility = Notification.VISIBILITY_PUBLIC; setShowBadge(false) })
        val n = Notification.Builder(context, CHANNEL).setSmallIcon(iconRes).setContentTitle(title).setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
            .setVisibility(Notification.VISIBILITY_PUBLIC).setOnlyAlertOnce(true).setAutoCancel(true).build()
        manager.notify(ID, n); return ID
    }
    override fun clear() = manager.cancel(ID)

    private companion object { const val CHANNEL = "deskbot_lockscreen"; const val ID = 4100 }
}
