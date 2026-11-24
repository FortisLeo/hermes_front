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
import androidx.compose.foundation.border
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
private val UserColors = listOf(Color(0xFF4285F4), Color(0xFF8AB4F8))        // blue: you are speaking
private val QuietColors = listOf(Color(0xFF8AB4F8), Color(0xFF8AB4F8)).map { it.copy(alpha = 0.55f) }

/**
 * Bottom-corner voice indicator in the style of Gemini Live: a dark pill with a scrolling bar histogram of how loud the USER is (blue); almost flat when nothing is heard.
 * It draws the microphone only, never the speaker. Appears only while a live conversation is running.
 * The level is collected here so only this small pill recomposes at microphone rate.
 */
@Composable
fun LiveWave(active: StateFlow<Boolean>, mic: StateFlow<Float>, modifier: Modifier = Modifier) {
    val on by active.collectAsState()
    AnimatedVisibility(
        visible = on, modifier = modifier,
        enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 2 },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(180)) { it / 2 },
    ) { WavePill(mic) }
}

@Composable
private fun WavePill(mic: StateFlow<Float>) {
    val micLevel by mic.collectAsState()
    val mode = WaveModel.mode(active = true, mic = micLevel)
    val level = if (mode == WaveMode.User) micLevel else 0f

    var history by remember { mutableStateOf(List(BARS) { 0f }) }
    val latest by rememberUpdatedState(level)
    LaunchedEffect(Unit) { while (true) { history = WaveModel.push(history, latest, BARS); delay(55) } }

    val phase by rememberInfiniteTransition(label = "wave").animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    val colors = if (mode == WaveMode.User) UserColors else QuietColors
    val description = if (mode == WaveMode.User) "You are speaking" else "Listening"

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
            .semantics { contentDescription = description },
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
    init {
        scope.launch {
            var t = 0
            while (true) {
                val speaking = (t / 20) % 4 < 2 // 1 s ticks of 20 x 50 ms: two seconds of speech, two quiet
                val env = (0.5 + 0.5 * sin(t * 0.55)).toFloat() * (0.5f + 0.5f * sin(t * 0.23 + 1).toFloat().coerceAtLeast(0f))
                mic.value = if (speaking) (0.15f + 0.6f * env).coerceIn(0f, 1f) else 0f
                t++; delay(50)
            }
        }
    }
}
