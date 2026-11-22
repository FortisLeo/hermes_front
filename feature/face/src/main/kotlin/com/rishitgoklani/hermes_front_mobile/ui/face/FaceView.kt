package com.rishitgoklani.hermes_front_mobile.ui.face

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.rishitgoklani.hermes_front_mobile.domain.BotState
import com.rishitgoklani.hermes_front_mobile.domain.face.Effect
import com.rishitgoklani.hermes_front_mobile.domain.face.FaceState
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val Bg = Color(0xFF05070A)

@Composable
fun FaceView(
    bot: BotState, face: FaceState = FaceState(), modifier: Modifier = Modifier,
    onTap: () -> Unit = {}, onLongPress: () -> Unit = {}, backgroundColor: Color = Bg, mouth: Float = 0f,
) {
    val mouthAnim = remember { Animatable(0f) }
    LaunchedEffect(mouth, bot) { mouthAnim.animateTo(mouth, tween(90)) }
    val style = EyeStyles.compute(bot, face)
    val open = remember { Animatable(style.openness) }
    val lookX = remember { Animatable(style.lookX) }
    val lookY = remember { Animatable(style.lookY) }
    LaunchedEffect(style.openness, style.lookX, style.lookY) {
        open.animateTo(style.openness, tween(220)); lookX.animateTo(style.lookX, tween(300)); lookY.animateTo(style.lookY, tween(300))
    }
    val blink = remember { Animatable(1f) }
    LaunchedEffect(style.blinkEnabled, style.blinkRateSec) {
        while (style.blinkEnabled) {
            delay((style.blinkRateSec * 1000 * (0.6 + Math.random() * 0.8)).toLong())
            blink.animateTo(0.05f, tween(70)); blink.animateTo(1f, tween(110))
        }
    }
    val phase by rememberInfiniteTransition(label = "face").animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    val measurer = rememberTextMeasurer()

    Canvas(
        modifier.fillMaxSize().background(backgroundColor).pointerInput(Unit) { detectTapGestures(onTap = { onTap() }, onLongPress = { onLongPress() }) },
    ) {
        val ang = (phase * 2 * PI).toFloat()
        val (mx, my) = when (style.motion) {
            Motion.Nod -> 0f to sin(ang * 3) * size.height * 0.03f
            Motion.Shake -> sin(ang * 4) * size.width * 0.02f to 0f
            Motion.Bounce -> 0f to -kotlin.math.abs(sin(ang * 3)) * size.height * 0.02f
            else -> 0f to 0f
        }
        val lx = when (style.motion) { Motion.EyeRoll -> cos(ang * 2); Motion.LookAround -> sin(ang) * 0.9f; else -> lookX.value }
        val ly = when (style.motion) { Motion.EyeRoll -> sin(ang * 2); else -> lookY.value }
        val eyeW = size.minDimension * 0.42f * style.sizeScale
        val eyeH = eyeW * 1.15f
        val cy = size.height / 2 + my
        val gap = size.width * 0.12f
        val color = Color(style.colorHex)
        listOf(-1, 1).forEach { side ->
            val cx = size.width / 2 + side * (eyeW / 2 + gap / 2) + mx
            val isRight = side == 1
            val scaleY = if (isRight) 1f - style.asymmetry else 1f
            val o = (open.value * blink.value).coerceIn(0.05f, 1.2f)
            val closedWink = isRight && style.winkRight
            when {
                style.crossed -> drawX(color, cx, cy, eyeW)
                closedWink || style.shape == EyeShape.HappyArc || style.shape == EyeShape.ClosedArc -> drawArcEye(color, cx, cy + eyeH * 0.1f, eyeW, if (style.shape == EyeShape.ClosedArc) 0.5f else 1f)
                style.shape == EyeShape.Line || o <= 0.06f -> drawLine(color, Offset(cx - eyeW / 2, cy), Offset(cx + eyeW / 2, cy), eyeW * 0.1f, StrokeCap.Round)
                else -> {
                    val h = eyeH * o * scaleY
                    drawRoundRect(color, Offset(cx - eyeW / 2, cy - h / 2), Size(eyeW, h), CornerRadius(eyeW * style.roundness))
                    drawPupil(style, cx + lx * eyeW * 0.22f, cy + ly * h * 0.22f, eyeW * 0.2f * style.pupilScale, ang)
                    drawLid(style.lidSlant, side, cx, cy, eyeW, h, backgroundColor)
                }
            }
        }
        if (mouthAnim.value > 0.02f) {
            val mw = gap * 1.1f; val mh = eyeW * 0.3f * mouthAnim.value
            drawRoundRect(color, Offset(size.width / 2 + mx - mw / 2, cy + eyeH * 0.2f), Size(mw, mh.coerceAtLeast(4f)), CornerRadius(mw * 0.4f))
        }
        drawEffects(style.effects, measurer, ang, eyeW, size.width / 2 + mx, cy, gap)
    }
}

private fun DrawScope.drawX(color: Color, cx: Float, cy: Float, w: Float) {
    val r = w * 0.35f; val sw = w * 0.12f
    drawLine(color, Offset(cx - r, cy - r), Offset(cx + r, cy + r), sw, StrokeCap.Round)
    drawLine(color, Offset(cx - r, cy + r), Offset(cx + r, cy - r), sw, StrokeCap.Round)
}

private fun DrawScope.drawArcEye(color: Color, cx: Float, cy: Float, w: Float, sweepFraction: Float) {
    val d = w * 0.8f
    drawArc(color, 180f, 180f * sweepFraction.coerceAtLeast(0.5f) , false, Offset(cx - d / 2, cy - d / 2), Size(d, d), style = Stroke(w * 0.16f, cap = StrokeCap.Round))
}

/** Covers part of the top of the eye with the background colour: angry (+) slants inner corner down, sad (-) drops the outer corner. */
private fun DrawScope.drawLid(slant: Float, side: Int, cx: Float, cy: Float, w: Float, h: Float, bg: Color) {
    if (kotlin.math.abs(slant) < 0.05f) return
    val top = cy - h / 2; val left = cx - w / 2 - 2f; val right = cx + w / 2 + 2f
    val inner = if (side < 0) right else left; val outer = if (side < 0) left else right
    val drop = h * 0.5f * kotlin.math.abs(slant)
    val path = Path().apply {
        moveTo(left, top - 2f); lineTo(right, top - 2f)
        if (slant > 0) { lineTo(inner, top + drop); lineTo(outer, top - 2f) } else { lineTo(outer, top + drop); lineTo(inner, top - 2f) }
        close()
    }
    drawPath(path, bg)
}

private fun DrawScope.drawPupil(style: EyeStyle, x: Float, y: Float, r: Float, ang: Float) {
    when (style.pupil) {
        PupilShape.Circle -> drawCircle(Bg, r, Offset(x, y))
        PupilShape.Heart -> drawPath(heart(x, y, r * 1.5f), Color(0xFFFF4D6D))
        PupilShape.Star -> drawPath(star(x, y, r * 1.5f, r * 0.6f), Color(0xFFFFF176))
        PupilShape.Spiral -> {
            val p = Path(); var first = true
            for (i in 0..80) { val t = i / 80f; val a = t * 6 * PI.toFloat() + ang; val rr = r * 1.6f * t; val px = x + rr * cos(a); val py = y + rr * sin(a); if (first) { p.moveTo(px, py); first = false } else p.lineTo(px, py) }
            drawPath(p, Bg, style = Stroke(r * 0.28f, cap = StrokeCap.Round))
        }
    }
}

private fun heart(cx: Float, cy: Float, s: Float) = Path().apply {
    moveTo(cx, cy + s * 0.7f)
    cubicTo(cx - s * 1.1f, cy, cx - s * 0.7f, cy - s * 0.9f, cx, cy - s * 0.35f)
    cubicTo(cx + s * 0.7f, cy - s * 0.9f, cx + s * 1.1f, cy, cx, cy + s * 0.7f); close()
}

private fun star(cx: Float, cy: Float, outer: Float, inner: Float) = Path().apply {
    for (i in 0 until 10) {
        val a = (-PI / 2 + i * PI / 5).toFloat(); val r = if (i % 2 == 0) outer else inner
        val x = cx + r * cos(a); val y = cy + r * sin(a); if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun DrawScope.drawEffects(effects: Set<Effect>, measurer: androidx.compose.ui.text.TextMeasurer, ang: Float, eyeW: Float, midX: Float, cy: Float, gap: Float) {
    val t = ang / (2 * PI.toFloat())
    val half = eyeW / 2 + gap / 2
    effects.forEach { e ->
        when (e) {
            Effect.Tears -> listOf(-1, 1).forEach { s -> val y = cy + eyeW * 0.65f + ((t * 3) % 1f) * eyeW * 0.6f; drawCircle(Color(0xFF64B5F6), eyeW * 0.07f, Offset(midX + s * half + s * eyeW * 0.15f, y)) }
            Effect.Blush -> listOf(-1, 1).forEach { s -> drawOval(Color(0x66FF6B9D), Offset(midX + s * half - eyeW * 0.3f, cy + eyeW * 0.7f), Size(eyeW * 0.6f, eyeW * 0.22f)) }
            Effect.Hearts -> repeat(3) { i -> val p = (t * 2 + i / 3f) % 1f; drawPath(heart(midX + (i - 1) * eyeW * 0.6f, cy - eyeW * 0.8f - p * eyeW * 0.8f, eyeW * 0.1f), Color(0xFFFF4D6D).copy(alpha = 1f - p)) }
            Effect.Sparkles -> repeat(4) { i -> val a = 0.4f + 0.6f * sin(ang * 2 + i * 1.7f).let { kotlin.math.abs(it) }; drawPath(star(midX + (i - 1.5f) * eyeW * 0.9f, cy - eyeW * (0.8f + 0.15f * (i % 2)), eyeW * 0.1f * a, eyeW * 0.04f), Color(0xFFFFF176)) }
            Effect.Zzz -> repeat(3) { i -> val p = (t + i / 3f) % 1f; drawText(measurer, "z", Offset(midX + half + eyeW * 0.5f + i * eyeW * 0.15f, cy - eyeW * 0.5f - p * eyeW * 0.6f), TextStyle(color = Color.White.copy(alpha = 1f - p), fontSize = (18 + i * 6).sp)) }
            Effect.QuestionMark -> drawText(measurer, "?", Offset(midX + half + eyeW * 0.3f, cy - eyeW * 1.0f), TextStyle(color = Color.White, fontSize = 40.sp))
            Effect.AngerMark -> { val c = Offset(midX + half + eyeW * 0.45f, cy - eyeW * 0.85f); val r = eyeW * 0.14f; listOf(-1, 1).forEach { d -> drawLine(Color(0xFFFF5252), Offset(c.x - r, c.y - d * r), Offset(c.x + r, c.y + d * r), eyeW * 0.04f, StrokeCap.Round) } }
            Effect.SweatDrop -> drawPath(Path().apply { val x = midX + half + eyeW * 0.4f; val y = cy - eyeW * 0.6f + sin(ang) * 4f; moveTo(x, y - eyeW * 0.12f); cubicTo(x - eyeW * 0.1f, y, x - eyeW * 0.08f, y + eyeW * 0.14f, x, y + eyeW * 0.14f); cubicTo(x + eyeW * 0.08f, y + eyeW * 0.14f, x + eyeW * 0.1f, y, x, y - eyeW * 0.12f) }, Color(0xFF81D4FA))
            Effect.Spirals -> Unit // drawn as spiral pupils
        }
    }
}


/**
 * The face's emotion effects (blush, tears, hearts, sparkles, zzz, ...) drawn over any other face, e.g. the 3D avatar.
 * [headX]/[headY]/[headSize] are fractions of this view: where the head is and how tall it is.
 */
@Composable
fun FaceEffectsOverlay(effects: Set<Effect>, headX: Float, headY: Float, headSize: Float, modifier: Modifier = Modifier) {
    if (effects.isEmpty()) return
    val phase by rememberInfiniteTransition(label = "fx").animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "fxphase")
    val measurer = rememberTextMeasurer()
    Canvas(modifier.fillMaxSize()) {
        val eyeW = size.height * headSize * 0.5f
        drawEffects(effects, measurer, (phase * 2 * PI).toFloat(), eyeW, size.width * headX, size.height * headY, eyeW * 0.25f)
    }
}
