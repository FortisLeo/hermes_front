package com.rishitgoklani.hermes_front_mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** Keeps the process (bridge server, audio pipeline) alive. specialUse so it may also start at boot. */
class BotService : Service() {
    override fun onCreate() {
        super.onCreate()
        (application as DeskbotApp).container.startBridge()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Deskbot", NotificationManager.IMPORTANCE_LOW))
        val micGranted = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        try {
            // Microphone type is refused when the service is started from the background (e.g. boot); fall back to specialUse only.
            startForeground(NOTIFICATION_ID, notification(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or if (micGranted) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
        } catch (e: SecurityException) {
            startForeground(NOTIFICATION_ID, notification(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        }
        (application as DeskbotApp).container.resumeWakeIfEnabled()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL = "deskbot"
        const val NOTIFICATION_ID = 1

        fun notification(context: Context): Notification =
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Deskbot is running")
                .setOngoing(true)
                .build()

        fun start(context: Context) = ContextCompat.startForegroundService(context, Intent(context, BotService::class.java))
    }
}
