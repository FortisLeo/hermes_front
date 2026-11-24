package com.rishitgoklani.hermes_front_mobile.ui.voice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rishitgoklani.hermes_front_mobile.domain.live.LiveNotice
import com.rishitgoklani.hermes_front_mobile.domain.live.WaveMode
import com.rishitgoklani.hermes_front_mobile.domain.live.WaveModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

private const val BARS = 11
private const val NOTICE_MS = 6_000L
private val UserColors = listOf(Color(0xFF4285F4), Color(0xFF8AB4F8))        // blue: you are speaking
private val QuietColors = listOf(Color(0xFF8AB4F8), Color(0xFF8AB4F8)).map { it.copy(alpha = 0.55f) }

/**
 * Bottom-corner voice button in the style of Gemini Live: a dark pill. Tap it to start listening (no wake phrase) and tap again to stop.
 * While listening it shows a scrolling bar histogram of how loud the USER is (blue); almost flat when nothing is heard. It draws the microphone only, never the speaker.
 * When Gemini Live is not available it is hidden. A short notice above it says why a conversation stopped or that it is reconnecting.
 * The level is collected here so only this small pill recomposes at microphone rate.
 */
@Composable
fun LiveWave(
    available: StateFlow<Boolean>, active: StateFlow<Boolean>, mic: StateFlow<Float>, notice: StateFlow<LiveNotice?>,
    onToggle: () -> Unit, modifier: Modifier = Modifier,
) {
    val on by active.collectAsState()
    val ready by available.collectAsState()
    Column(modifier, horizontalAlignment = Alignment.End) {
        NoticeChip(notice)
        AnimatedVisibility(
            visible = ready || on,
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 2 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(180)) { it / 2 },
        ) { if (on) WavePill(mic, onToggle) else TapPill(onToggle) }
    }
}

/** A message that fades after a few seconds; announced to screen readers. */
@Composable
private fun NoticeChip(notice: StateFlow<LiveNotice?>) {
    val latest by notice.collectAsState()
    var shown by remember { mutableStateOf<LiveNotice?>(null) }
    LaunchedEffect(latest?.id) {
        val n = latest; shown = n
        if (n != null) { delay(NOTICE_MS); if (shown?.id == n.id) shown = null }
    }
    AnimatedVisibility(visible = shown != null, enter = fadeIn(tween(150)), exit = fadeOut(tween(300))) {
        Text(
            shown?.text.orEmpty(), Modifier.padding(horizontal = 16.dp).widthIn(max = 260.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xE6101218))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
            color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodySmall, maxLines = 3,
        )
    }
}

/** The resting state: a small button saying what a tap does. */
@Composable
private fun TapPill(onToggle: () -> Unit) {
    Box(
        Modifier.padding(16.dp).width(128.dp).height(48.dp).clip(RoundedCornerShape(50)).background(Color(0xE6101218))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
            .clickable(onClickLabel = "Start listening", role = Role.Button, onClick = onToggle).semantics { contentDescription = "Tap to talk" },
        contentAlignment = Alignment.Center,
    ) { Text("Tap to talk", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge) }
}

@Composable
private fun WavePill(mic: StateFlow<Float>, onToggle: () -> Unit) {
    val micLevel by mic.collectAsState()
    val mode = WaveModel.mode(active = true, mic = micLevel)
    val level = if (mode == WaveMode.User) micLevel else 0f

    var history by remember { mutableStateOf(List(BARS) { 0f }) }
    val latest by rememberUpdatedState(level)
    LaunchedEffect(Unit) { while (true) { history = WaveModel.push(history, latest, BARS); delay(55) } }

    val phase by rememberInfiniteTransition(label = "wave").animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    val colors = if (mode == WaveMode.User) UserColors else QuietColors
    val description = if (mode == WaveMode.User) "Listening, you are speaking. Tap to stop." else "Listening. Tap to stop."

    val heights = List(BARS) { i ->
        val target = when (mode) {
            WaveMode.User -> WaveModel.barHeight(history[i])
            else -> 0.12f + 0.035f * sin(2 * PI * (phase + i / BARS.toFloat())).toFloat()
        }
        animateFloatAsState(target, tween(90), label = "bar$i").value
    }

    Box(
        Modifier.padding(16.dp).width(128.dp).height(48.dp)
            .clip(RoundedCornerShape(50)).border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
            .clickable(onClickLabel = "Stop listening", role = Role.Button, onClick = onToggle).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) { drawRect(Color(0xE6101218)) }
        Canvas(Modifier.width(88.dp).height(32.dp)) {
            val slot = size.width / BARS
            val barW = slot * 0.62f
            val brush = Brush.horizontalGradient(colors)
            heights.forEachIndexed { i, h ->
                val barH = (size.height * h.coerceIn(0.06f, 1f))
                drawRoundRect(brush, topLeft = Offset(i * slot + (slot - barW) / 2, (size.height - barH) / 2), size = Size(barW, barH), cornerRadius = CornerRadius(barW / 2))
            }
        }
    }
}

/**
 * Debug-only driver that makes the wave move without a microphone or a session: bursts of speech with quiet gaps, on repeat.
 * Used to check the look of the indicator on a device.
 */
class WaveDemo(scope: CoroutineScope) {
    val active = MutableStateFlow(true)
    val mic = MutableStateFlow(0f)
    val notice = MutableStateFlow<LiveNotice?>(null)
    init {
        scope.launch {
            var t = 0
            while (true) {
                if (t == 60) notice.value = LiveNotice(1, "Connection dropped, reconnecting…")
                val speaking = (t / 20) % 4 < 2 // 1 s ticks of 20 x 50 ms: two seconds of speech, two quiet
                val env = (0.5 + 0.5 * sin(t * 0.55)).toFloat() * (0.5f + 0.5f * sin(t * 0.23 + 1).toFloat().coerceAtLeast(0f))
                mic.value = if (speaking) (0.15f + 0.6f * env).coerceIn(0f, 1f) else 0f
                t++; delay(50)
            }
        }
    }
}
