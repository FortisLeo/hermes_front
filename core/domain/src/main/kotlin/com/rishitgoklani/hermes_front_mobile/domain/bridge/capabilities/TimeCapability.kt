package com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object LocalTime {
    /** One-line context for the model: "Monday 23 November 2026, 00:42 (Asia/Kolkata, UTC+05:30)". */
    fun describe(now: ZonedDateTime): String =
        "${now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${now.dayOfMonth} ${now.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${now.year}, " +
            "${now.format(DateTimeFormatter.ofPattern("HH:mm"))} (${now.zone.id}, UTC${now.offset.id.replace("Z", "+00:00")})"
    fun partOfDay(hour: Int) = when (hour) { in 5..11 -> "morning"; in 12..16 -> "afternoon"; in 17..20 -> "evening"; else -> "night" }
}

/** Gives Hermes the phone's local date, time, zone and part of day. [now] is injectable for tests. */
fun timeNowCapability(now: () -> ZonedDateTime = { ZonedDateTime.now() }) = Capability("time_now", "The phone's current local date, time, weekday, time zone and part of day. Use for anything relative to now (reminders, 'tonight', schedules).") {
    val t = now()
    ActionResult.completed(LocalTime.describe(t), buildJsonObject {
        put("iso", t.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)); put("date", t.toLocalDate().toString()); put("time", t.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
        put("weekday", t.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)); put("timezone", t.zone.id); put("epoch_ms", t.toInstant().toEpochMilli()); put("part_of_day", LocalTime.partOfDay(t.hour))
    })
}
