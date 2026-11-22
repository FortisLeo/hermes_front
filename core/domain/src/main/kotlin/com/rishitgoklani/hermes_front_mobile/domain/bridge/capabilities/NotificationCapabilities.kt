package com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.notifications.NotificationHistory
import com.rishitgoklani.hermes_front_mobile.domain.notifications.NotificationPoster
import com.rishitgoklani.hermes_front_mobile.domain.notifications.NotificationRecord
import com.rishitgoklani.hermes_front_mobile.domain.notifications.NotificationSource
import com.rishitgoklani.hermes_front_mobile.domain.notifications.OtpExtractor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private fun JsonObject.s(k: String) = this[k]?.jsonPrimitive?.contentOrNull
private fun NotificationRecord.json() = buildJsonObject {
    put("key", key); put("app", app); put("package", pkg); put("title", title); put("text", text); put("time", time)
    put("actions", buildJsonArray { actions.forEach { add(JsonPrimitive(it)) } }); put("can_reply", canReply)
}
private fun line(r: NotificationRecord) = "${r.app}: ${r.title} - ${r.text}".take(160)

fun notificationCapabilities(source: NotificationSource, history: NotificationHistory, poster: NotificationPoster, clock: () -> Long = System::currentTimeMillis): List<Capability> = listOf(
    Capability("notifications_list", "List active notifications (optionally filter by app or text). Each has a key for dismiss/act.",
        """{"type":"object","properties":{"app":{"type":"string"},"query":{"type":"string"},"limit":{"type":"integer"}}}""") { a ->
        val list = source.active().filter { !it.ongoing }
            .filter { a.s("app")?.let { f -> it.app.contains(f, true) || it.pkg.contains(f, true) } ?: true }
            .filter { a.s("query")?.let { q -> it.title.contains(q, true) || it.text.contains(q, true) } ?: true }
            .take(a["limit"]?.jsonPrimitive?.intOrNull ?: 20)
        ActionResult.completed(if (list.isEmpty()) "No notifications" else list.joinToString("\n") { line(it) },
            buildJsonObject { put("notifications", buildJsonArray { list.forEach { add(it.json()) } }) })
    },
    Capability("notification_dismiss", "Dismiss one notification by key, all from an app, ones containing a keyword, or everything (all=true).",
        """{"type":"object","properties":{"key":{"type":"string"},"app":{"type":"string"},"keyword":{"type":"string"},"all":{"type":"boolean"}}}""") { a ->
        if (a["all"]?.jsonPrimitive?.contentOrNull == "true") return@Capability if (source.dismissAll()) ActionResult.completed("Dismissed all") else ActionResult.failed("Listener unavailable")
        val targets = source.active().filter { !it.ongoing }.filter {
            (a.s("key")?.let { k -> it.key == k } ?: true) && (a.s("app")?.let { f -> it.app.contains(f, true) || it.pkg.contains(f, true) } ?: true) &&
                (a.s("keyword")?.let { k -> it.title.contains(k, true) || it.text.contains(k, true) } ?: true)
        }
        if (a.s("key") == null && a.s("app") == null && a.s("keyword") == null) return@Capability ActionResult.failed("Give key, app, keyword or all=true")
        val n = targets.count { source.dismiss(it.key) }
        ActionResult.completed("Dismissed $n")
    },
    Capability("notification_act", "Press a notification action button by title (or inline-reply with reply text).",
        """{"type":"object","properties":{"key":{"type":"string"},"action":{"type":"string"},"reply":{"type":"string"}},"required":["key","action"]}""") { a ->
        val ok = source.act(a.s("key") ?: "", a.s("action") ?: "", a.s("reply"))
        if (ok) ActionResult.completed("Action sent") else ActionResult.failed("No such notification/action")
    },
    Capability("notification_post", "Post a notification on this phone (reminder, alert, status).",
        """{"type":"object","properties":{"title":{"type":"string"},"text":{"type":"string"},"actions":{"type":"array","items":{"type":"string"}}},"required":["title"]}""") { a ->
        val id = poster.post(a.s("title") ?: "", a.s("text").orEmpty(), (a["actions"] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList())
        ActionResult.completed("Posted #$id")
    },
    Capability("notification_history", "Search past notifications. since_minutes limits age; digest=true returns per-app counts.",
        """{"type":"object","properties":{"query":{"type":"string"},"app":{"type":"string"},"since_minutes":{"type":"integer"},"limit":{"type":"integer"},"digest":{"type":"boolean"}}}""") { a ->
        val since = clock() - (a["since_minutes"]?.jsonPrimitive?.intOrNull ?: Int.MAX_VALUE / 120).toLong() * 60_000
        if (a["digest"]?.jsonPrimitive?.contentOrNull == "true") {
            val d = history.digest(since)
            return@Capability ActionResult.completed(if (d.isEmpty()) "Nothing" else d.entries.joinToString(", ") { "${it.key}: ${it.value}" })
        }
        val list = history.search(a.s("query"), a.s("app"), since, a["limit"]?.jsonPrimitive?.intOrNull ?: 30)
        ActionResult.completed(if (list.isEmpty()) "No matches" else list.joinToString("\n") { line(it) }, buildJsonObject { put("notifications", buildJsonArray { list.forEach { add(it.json()) } }) })
    },
    Capability("otp_latest", "Return the most recent verification code seen in a notification within max_age_minutes (default 10).",
        """{"type":"object","properties":{"max_age_minutes":{"type":"integer"}}}""") { a ->
        val since = clock() - (a["max_age_minutes"]?.jsonPrimitive?.intOrNull ?: 10) * 60_000L
        val hit = history.search(null, null, since, 50).firstNotNullOfOrNull { r -> OtpExtractor.extract("${r.title} ${r.text}")?.let { r to it } }
        if (hit == null) ActionResult.failed("No recent code") else ActionResult.completed("Code from ${hit.first.app}", buildJsonObject { put("code", hit.second); put("app", hit.first.app) })
    },
)
