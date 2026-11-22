package com.rishitgoklani.hermes_front_mobile.testing

import com.rishitgoklani.hermes_front_mobile.domain.notifications.NotificationPoster
import com.rishitgoklani.hermes_front_mobile.domain.notifications.NotificationRecord
import com.rishitgoklani.hermes_front_mobile.domain.notifications.NotificationSource

class FakeNotificationSource(var records: List<NotificationRecord> = emptyList()) : NotificationSource {
    val dismissed = mutableListOf<String>(); var dismissedAll = false; val acted = mutableListOf<Triple<String, String, String?>>()
    override suspend fun active() = records
    override suspend fun dismiss(key: String): Boolean { dismissed += key; return true }
    override suspend fun dismissAll(): Boolean { dismissedAll = true; return true }
    override suspend fun act(key: String, actionTitle: String, reply: String?): Boolean { acted += Triple(key, actionTitle, reply); return records.any { it.key == key } }
}
class FakeNotificationPoster : NotificationPoster {
    val posted = mutableListOf<Triple<String, String, List<String>>>()
    override fun post(title: String, text: String, actions: List<String>): Int { posted += Triple(title, text, actions); return posted.size }
}
