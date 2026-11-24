package com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.notifications.RankNotifications
import com.rishitgoklani.hermes_front_mobile.domain.notifications.SilenceRule
import com.rishitgoklani.hermes_front_mobile.domain.notifications.SilenceRules
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private fun JsonObject.s(k: String) = this[k]?.jsonPrimitive?.contentOrNull

/** #104: deterministic notification ranking plus per-app silence rules. */
fun notificationPriorityCapabilities(rank: RankNotifications, rules: SilenceRules, clock: () -> Long = System::currentTimeMillis): List<Capability> = listOf(
    Capability("notifications_rank", "Rank active notifications by importance (high/normal/low) with reasons, skipping silenced apps. dismiss_silenced=true also clears the silenced ones.",
        """{"type":"object","properties":{"limit":{"type":"integer"},"dismiss_silenced":{"type":"boolean"}}}""") { a ->
        val r = rank(a["limit"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 10, a.s("dismiss_silenced") == "true")
        val speak = if (r.ranked.isEmpty()) "Nothing important" else r.ranked.take(3).joinToString(". ") { "${it.record.app}: ${it.record.title}".take(80) }
        ActionResult.completed(speak, buildJsonObject {
            put("ranked", buildJsonArray { r.ranked.forEach { x -> add(buildJsonObject { put("key", x.record.key); put("app", x.record.app); put("title", x.record.title); put("score", x.score); put("importance", x.importance.name.lowercase()); put("reason", x.reason) }) } })
            put("silenced_count", r.silenced.size); put("dismissed", r.dismissed)
        })
    },
    Capability("notification_silence", "Manage silence rules: action=add|remove|list. add needs app (label or package part), optional contains and minutes (0/omitted = until removed).",
        """{"type":"object","properties":{"action":{"type":"string","enum":["add","remove","list"]},"app":{"type":"string"},"contains":{"type":"string"},"minutes":{"type":"integer"}},"required":["action"]}""") { a ->
        when (a.s("action")) {
            "add" -> {
                val app = a.s("app")?.trim().orEmpty(); if (app.length < 2) return@Capability ActionResult.failed("app required (at least 2 characters)")
                val minutes = a.s("minutes")?.toLongOrNull() ?: 0L; if (minutes < 0) return@Capability ActionResult.failed("minutes must be 0 or more")
                rules.add(SilenceRule(app, a.s("contains")?.ifBlank { null }, if (minutes == 0L) 0 else clock() + minutes * 60_000))
                ActionResult.completed("Silencing $app" + if (minutes > 0) " for $minutes minutes" else "")
            }
            "remove" -> if (rules.remove(a.s("app").orEmpty(), a.s("contains"))) ActionResult.completed("Removed") else ActionResult.failed("No such rule")
            "list" -> rules.list().let { l -> ActionResult.completed(if (l.isEmpty()) "No silence rules" else l.joinToString("\n") { "${it.app}${it.contains?.let { c -> " containing '$c'" } ?: ""}" }) }
            else -> ActionResult.failed("action must be add, remove or list")
        }
    },
)
