package com.rishitgoklani.hermes_front_mobile.data.ui

/** Pure helpers for the colon-separated `enabled_accessibility_services` / `enabled_notification_listeners` settings. */
object ComponentList {
    fun contains(existing: String?, component: String) = split(existing).any { it.equals(component, true) }

    fun add(existing: String?, component: String): String =
        if (contains(existing, component)) existing.orEmpty() else (split(existing) + component).joinToString(":")

    fun remove(existing: String?, component: String): String =
        split(existing).filterNot { it.equals(component, true) }.joinToString(":")

    private fun split(s: String?) = s.orEmpty().split(':').map { it.trim() }.filter { it.isNotEmpty() }
}
