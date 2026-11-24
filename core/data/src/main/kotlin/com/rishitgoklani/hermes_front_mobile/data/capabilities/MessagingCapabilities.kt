package com.rishitgoklani.hermes_front_mobile.data.capabilities

import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.ContentUris
import android.content.Context
import android.provider.CallLog
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.messaging.SmsCheck
import com.rishitgoklani.hermes_front_mobile.domain.messaging.SmsRules
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun JsonObject.flag(k: String): Boolean = this[k]?.jsonPrimitive?.booleanOrNull ?: (str(k) == "true")
private val stamp get() = SimpleDateFormat("d MMM HH:mm", Locale.getDefault())

/** SMS and call history. Sending needs `confirm=true` (see [SmsRules]). */
fun messagingCapabilities(context: Context): List<Capability> = listOf(
    Capability("sms_read", "Read recent text messages (inbox by default, box=sent for sent). Optional `from` filter and `limit` (default 10).",
        """{"type":"object","properties":{"box":{"type":"string","enum":["inbox","sent"]},"from":{"type":"string"},"limit":{"type":"integer"}}}""") { a ->
        val uri = if (a.str("box") == "sent") Telephony.Sms.Sent.CONTENT_URI else Telephony.Sms.Inbox.CONTENT_URI
        val from = a.str("from"); val limit = (a.int("limit") ?: 10).coerceIn(1, 50); val rows = mutableListOf<String>()
        runCatching {
            context.contentResolver.query(uri, arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                from?.let { "${Telephony.Sms.ADDRESS} LIKE ?" }, from?.let { arrayOf("%$it%") }, "${Telephony.Sms.DATE} DESC")?.use { c ->
                while (c.moveToNext() && rows.size < limit) rows += "${stamp.format(Date(c.getLong(2)))} ${c.getString(0)}: ${c.getString(1).orEmpty().take(300)}"
            }
        }.onFailure { return@Capability ActionResult.failed("SMS read failed: ${it.message}") }
        ActionResult.completed(if (rows.isEmpty()) "No messages" else rows.joinToString("\n"))
    },
    Capability("sms_send", "Send a text message. Costs money and cannot be undone: ask the user first, then pass confirm=true.",
        """{"type":"object","properties":{"to":{"type":"string"},"text":{"type":"string"},"confirm":{"type":"boolean"}},"required":["to","text","confirm"]}""") { a ->
        when (val c = SmsRules.check(a.str("to"), a.str("text"), a.flag("confirm"))) {
            is SmsCheck.Refused -> ActionResult.failed(c.reason)
            is SmsCheck.Ok -> runCatching {
                val sms = context.getSystemService(SmsManager::class.java)
                sms.sendMultipartTextMessage(c.number, null, sms.divideMessage(c.text), null, null)
                ActionResult.completed("Message sent to ${c.number}")
            }.getOrElse { ActionResult.failed("SMS send failed: ${it.message}") }
        }
    },
    Capability("call_log", "Recent calls (incoming, outgoing, missed) with `limit` (default 10) and optional `type` filter.",
        """{"type":"object","properties":{"limit":{"type":"integer"},"type":{"type":"string","enum":["incoming","outgoing","missed"]}}}""") { a ->
        val want = when (a.str("type")) { "incoming" -> CallLog.Calls.INCOMING_TYPE; "outgoing" -> CallLog.Calls.OUTGOING_TYPE; "missed" -> CallLog.Calls.MISSED_TYPE; else -> null }
        val limit = (a.int("limit") ?: 10).coerceIn(1, 50); val rows = mutableListOf<String>()
        runCatching {
            context.contentResolver.query(CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION),
                want?.let { "${CallLog.Calls.TYPE}=?" }, want?.let { arrayOf(it.toString()) }, "${CallLog.Calls.DATE} DESC")?.use { c ->
                while (c.moveToNext() && rows.size < limit) {
                    val kind = when (c.getInt(2)) { CallLog.Calls.INCOMING_TYPE -> "in"; CallLog.Calls.OUTGOING_TYPE -> "out"; CallLog.Calls.MISSED_TYPE -> "missed"; else -> "other" }
                    rows += "${stamp.format(Date(c.getLong(3)))} $kind ${c.getString(1) ?: c.getString(0)} (${c.getLong(4)}s)"
                }
            }
        }.onFailure { return@Capability ActionResult.failed("Call log read failed: ${it.message}") }
        ActionResult.completed(if (rows.isEmpty()) "No calls" else rows.joinToString("\n"))
    },
    Capability("contacts_delete", "Delete contacts whose display name equals `name` exactly. Needs confirm=true.",
        """{"type":"object","properties":{"name":{"type":"string"},"confirm":{"type":"boolean"}},"required":["name","confirm"]}""") { a ->
        if (!a.flag("confirm")) return@Capability ActionResult.failed("Ask the user first, then call again with confirm=true")
        val name = a.str("name").orEmpty().ifBlank { return@Capability ActionResult.failed("name required") }
        runCatching {
            val n = context.contentResolver.delete(ContactsContract.RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build(),
                "${ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY}=?", arrayOf(name))
            if (n > 0) ActionResult.completed("Deleted $n contact(s)") else ActionResult.failed("No contact named $name")
        }.getOrElse { ActionResult.failed(it.message ?: "failed") }
    },
    Capability("calendar_delete_event", "Delete the calendar event with id `event_id` (ids come from calendar_events). Needs confirm=true.",
        """{"type":"object","properties":{"event_id":{"type":"integer"},"confirm":{"type":"boolean"}},"required":["event_id","confirm"]}""") { a ->
        if (!a.flag("confirm")) return@Capability ActionResult.failed("Ask the user first, then call again with confirm=true")
        val id = a.long("event_id") ?: return@Capability ActionResult.failed("event_id required")
        runCatching {
            val n = context.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), null, null)
            if (n > 0) ActionResult.completed("Event deleted") else ActionResult.failed("No event with id $id")
        }.getOrElse { ActionResult.failed(it.message ?: "failed") }
    },
    Capability("dnd", "Get or set Do Not Disturb. mode: get | off | priority | alarms | silence.",
        """{"type":"object","properties":{"mode":{"type":"string","enum":["get","off","priority","alarms","silence"]}},"required":["mode"]}""") { a ->
        val nm = context.getSystemService(NotificationManager::class.java)
        fun name(f: Int) = when (f) { NotificationManager.INTERRUPTION_FILTER_ALL -> "off"; NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "priority"; NotificationManager.INTERRUPTION_FILTER_ALARMS -> "alarms"; NotificationManager.INTERRUPTION_FILTER_NONE -> "silence"; else -> "unknown" }
        val target = when (a.str("mode")) { "off" -> NotificationManager.INTERRUPTION_FILTER_ALL; "priority" -> NotificationManager.INTERRUPTION_FILTER_PRIORITY; "alarms" -> NotificationManager.INTERRUPTION_FILTER_ALARMS; "silence" -> NotificationManager.INTERRUPTION_FILTER_NONE; else -> null }
        if (target == null) return@Capability ActionResult.completed("Do Not Disturb: ${name(nm.currentInterruptionFilter)}")
        if (!nm.isNotificationPolicyAccessGranted) return@Capability ActionResult.failed("Do Not Disturb access not granted (run grant_all.sh)")
        nm.setInterruptionFilter(target); ActionResult.completed("Do Not Disturb: ${name(nm.currentInterruptionFilter)}")
    },
    Capability("app_usage", "Screen time per app for the last `hours` (default 24), top `limit` (default 10).",
        """{"type":"object","properties":{"hours":{"type":"integer"},"limit":{"type":"integer"}}}""") { a ->
        val end = System.currentTimeMillis(); val start = end - (a.int("hours") ?: 24).coerceIn(1, 168) * 3_600_000L
        val stats = runCatching { context.getSystemService(UsageStatsManager::class.java).queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, end) }.getOrNull().orEmpty()
            .filter { it.totalTimeInForeground > 0 }.sortedByDescending { it.totalTimeInForeground }.take((a.int("limit") ?: 10).coerceIn(1, 30))
        if (stats.isEmpty()) ActionResult.failed("No usage data (grant usage access: appops set PACKAGE_USAGE_STATS allow)")
        else ActionResult.completed(stats.joinToString("\n") { "${it.packageName}: ${it.totalTimeInForeground / 60_000} min" })
    },
)
