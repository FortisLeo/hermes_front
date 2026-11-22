package com.rishitgoklani.hermes_front_mobile.ui.face

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ClockFormatTest {
    private val t = ZonedDateTime.of(2026, 11, 23, 0, 42, 5, 0, ZoneId.of("Asia/Kolkata"))
    @Test fun formats24And12Hour() { assertEquals("00:42", ClockFormat.time(t)); assertEquals("12:42 AM", ClockFormat.time(t, use24h = false)) }
    @Test fun dateAndStatusLine() { assertEquals("Monday, 23 November", ClockFormat.date(t)); assertEquals("Monday, 23 November  ·  87%", ClockFormat.line(t, "87%")); assertEquals("Monday, 23 November", ClockFormat.line(t, "")) }
}
