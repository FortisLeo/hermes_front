package com.rishitgoklani.hermes_front_mobile.ui.face

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.composed
import kotlinx.coroutines.launch

/** Visual state of the swipe: the face follows the finger leftwards, then slides out and back in around the switch. */
class SwipeSlide {
    val offset = Animatable(0f)
    private var a by mutableFloatStateOf(1f)
    val alpha: Float get() = a
    internal fun setAlpha(v: Float) { a = v }
}

/** What a drag of [dx] px (negative = leftwards) over a [width] px wide screen does. */
object SwipeRule {
    const val COMMIT_FRACTION = 0.22f
    fun commits(dx: Float, width: Float) = width > 0 && dx < 0 && -dx >= width * COMMIT_FRACTION
    /** How far the face follows the finger (never rightwards, damped). */
    fun follow(dx: Float) = if (dx < 0) dx * 0.6f else 0f
}

/**
 * Right-to-left swipe. Only leftward drags are consumed, so a rightward drag still reaches the navigation drawer. Taps pass through.
 */
fun Modifier.swipeToNext(slide: SwipeSlide, onNext: (() -> Unit)?): Modifier = if (onNext == null) this else composed {
    val slop = LocalViewConfiguration.current.touchSlop
    val scope = androidx.compose.runtime.rememberCoroutineScope()   // composition scope: carries the frame clock Animatable needs
    pointerInput(onNext) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var total = 0f; var dragging = false
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Main)
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!ch.pressed) break
                total += ch.positionChange().x
                if (!dragging && total > slop) break                    // rightwards: the drawer's gesture
                if (!dragging && total < -slop) dragging = true
                if (dragging) { ch.consume(); scope.launch { slide.offset.snapTo(SwipeRule.follow(total)) } }
            }
            val w = size.width.toFloat()
            if (dragging && SwipeRule.commits(total, w)) {
                scope.launch {
                    slide.offset.animateTo(-w * 0.35f, tween(110)); slide.setAlpha(0f)
                    onNext()
                    slide.offset.snapTo(w * 0.25f); slide.setAlpha(1f); slide.offset.animateTo(0f, tween(170))
                }
            } else if (dragging) scope.launch { slide.offset.animateTo(0f, tween(150)) }
        }
    }
}
