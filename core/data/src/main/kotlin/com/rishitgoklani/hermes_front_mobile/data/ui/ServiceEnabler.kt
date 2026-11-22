package com.rishitgoklani.hermes_front_mobile.data.ui

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/** Enables our accessibility / notification-listener services through secure settings (needs WRITE_SECURE_SETTINGS). */
object ServiceEnabler {
    fun enableAccessibility(context: Context): Boolean = runCatching {
        val component = ComponentName(context, DeskbotAccessibilityService::class.java).flattenToString()
        val resolver = context.contentResolver
        val current = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        if (!ComponentList.contains(current, component)) {
            Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, ComponentList.add(current, component))
        }
        Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        true
    }.getOrDefault(false)

    fun enableNotificationListener(context: Context): Boolean = runCatching {
        val component = ComponentName(context, com.rishitgoklani.hermes_front_mobile.data.notifications.DeskbotNotificationListener::class.java).flattenToString()
        val resolver = context.contentResolver
        val current = Settings.Secure.getString(resolver, "enabled_notification_listeners")
        if (!ComponentList.contains(current, component)) Settings.Secure.putString(resolver, "enabled_notification_listeners", ComponentList.add(current, component))
        true
    }.getOrDefault(false)
}
