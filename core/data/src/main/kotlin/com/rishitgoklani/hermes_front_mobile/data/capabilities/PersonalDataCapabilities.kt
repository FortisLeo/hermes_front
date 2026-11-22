package com.rishitgoklani.hermes_front_mobile.data.capabilities

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import android.provider.ContactsContract
import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun calendarCapabilities(context: Context): List<Capability> = listOf(
    Capability("calendar_events", "List calendar events from now for `days` days (default 1).",
        """{"type":"object","properties":{"days":{"type":"integer"},"from_ms":{"type":"integer"}}}""") { a ->
        val start = a.long("from_ms") ?: System.currentTimeMillis(); val end = start + (a.int("days") ?: 1) * 86_400_000L
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it, start); ContentUris.appendId(it, end) }.build()
        val fmt = SimpleDateFormat("EEE d MMM HH:mm", Locale.getDefault())
        val rows = mutableListOf<String>(); val arr = buildJsonArray { }
        runCatching {
            context.contentResolver.query(uri, arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.EVENT_LOCATION), null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext() && rows.size < 50) rows += "${fmt.format(Date(c.getLong(1)))} - ${c.getString(0).orEmpty()}" + (c.getString(3)?.takeIf { it.isNotBlank() }?.let { " @ $it" } ?: "")
            }
        }.onFailure { return@Capability ActionResult.failed("Calendar read failed: ${it.message}") }
        ActionResult.completed(if (rows.isEmpty()) "No events" else rows.joinToString("\n"), buildJsonObject { put("count", rows.size) })
    },
    Capability("calendar_add_event", "Create an event in the first writable calendar.",
        """{"type":"object","properties":{"title":{"type":"string"},"start_ms":{"type":"integer"},"duration_min":{"type":"integer"},"location":{"type":"string"}},"required":["title","start_ms"]}""") { a ->
        val calId = context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, arrayOf(CalendarContract.Calendars._ID), "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL}>=?", arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()), null)?.use { if (it.moveToFirst()) it.getLong(0) else null }
            ?: return@Capability ActionResult.failed("No writable calendar on this phone")
        val start = a.long("start_ms") ?: return@Capability ActionResult.failed("start_ms required")
        val v = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calId); put(CalendarContract.Events.TITLE, a.str("title")); put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, start + (a.int("duration_min") ?: 30) * 60_000L); put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id); put(CalendarContract.Events.EVENT_LOCATION, a.str("location"))
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, v)
        if (uri != null) ActionResult.completed("Event added") else ActionResult.failed("Insert failed")
    },
)

fun contactCapabilities(context: Context): List<Capability> = listOf(
    Capability("contacts_search", "Search contacts by name or number.", """{"type":"object","properties":{"query":{"type":"string"},"limit":{"type":"integer"}},"required":["query"]}""") { a ->
        val q = a.str("query").orEmpty(); val rows = mutableListOf<String>()
        runCatching {
            context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?", arrayOf("%$q%", "%$q%"), null)?.use { c ->
                while (c.moveToNext() && rows.size < (a.int("limit") ?: 20)) rows += "${c.getString(0)}: ${c.getString(1)}"
            }
        }.onFailure { return@Capability ActionResult.failed("Contacts read failed: ${it.message}") }
        ActionResult.completed(if (rows.isEmpty()) "No contacts" else rows.distinct().joinToString("\n"))
    },
    Capability("contacts_add", "Add a contact with name and optional phone/email.", """{"type":"object","properties":{"name":{"type":"string"},"phone":{"type":"string"},"email":{"type":"string"}},"required":["name"]}""") { a ->
        val ops = arrayListOf(android.content.ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI).withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null).withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build(),
            android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0).withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE).withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, a.str("name")).build())
        a.str("phone")?.let { ops += android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0).withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE).withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, it).build() }
        a.str("email")?.let { ops += android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI).withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0).withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE).withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, it).build() }
        runCatching { context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops); ActionResult.completed("Contact added") }.getOrElse { ActionResult.failed(it.message ?: "failed") }
    },
)
