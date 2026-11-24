package com.rishitgoklani.hermes_front_mobile.data.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.rishitgoklani.hermes_front_mobile.domain.notifications.NotificationPoster
import java.util.concurrent.atomic.AtomicInteger

class AndroidNotificationPoster(private val context: Context, private val iconRes: Int, firstId: Int = 1000) : NotificationPoster {
    private val ids = AtomicInteger(firstId)
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    override fun post(title: String, text: String, actions: List<String>): Int {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Deskbot alerts", NotificationManager.IMPORTANCE_HIGH))
        val id = ids.incrementAndGet()
        val b = Notification.Builder(context, CHANNEL).setSmallIcon(iconRes).setContentTitle(title).setContentText(text).setAutoCancel(true)
            .setStyle(Notification.BigTextStyle().bigText(text))
        manager.notify(id, b.build())
        return id
    }

    private companion object { const val CHANNEL = "deskbot_alerts" }
}
