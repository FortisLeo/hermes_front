package com.rishitgoklani.hermes_front_mobile.ui.face

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Pure formatting for the on-face clock so it can be unit tested. */
object ClockFormat {
    fun time(now: ZonedDateTime, use24h: Boolean = true): String = now.format(DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a", Locale.ENGLISH))
    fun date(now: ZonedDateTime): String = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH))
    fun line(now: ZonedDateTime, extra: String = "", use24h: Boolean = true): String = listOf(date(now), extra).filter { it.isNotBlank() }.joinToString("  ·  ")
}

/** Quiet clock for the face: time, date and an optional status line (battery, Wi-Fi). Ticks itself every second. */
@Composable
fun FaceClock(extra: String, modifier: Modifier = Modifier, color: Color = Color(0x99FFFFFF), use24h: Boolean = true) {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = ZonedDateTime.now(); delay(1000L - System.currentTimeMillis() % 1000L) } }
    Column(modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(ClockFormat.time(now, use24h), color = color, fontSize = 40.sp, fontWeight = FontWeight.Light)
        Text(ClockFormat.line(now, extra), color = color.copy(alpha = color.alpha * 0.8f), fontSize = 14.sp)
    }
}
