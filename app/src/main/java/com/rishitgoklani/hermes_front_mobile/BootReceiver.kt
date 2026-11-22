package com.rishitgoklani.hermes_front_mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (shouldStart(intent.action)) BotService.start(context)
    }

    companion object {
        fun shouldStart(action: String?) = action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED
    }
}
