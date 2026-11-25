package com.rishitgoklani.hermes_front_mobile.ui.face

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Visual state of the swipe: the face follows the finger leftwards, then slides out and back in around the switch. */
class SwipeSlide {
    val offset = Animatable(0f)
    internal val fade = Animatable(1f)
    val alpha: Float get() = fade.value
    /** A switch is animating: a second swipe must wait, or two transitions would fight over the offset. */
    internal var busy = false
}

/** What a drag of [dx] px (negative = leftwards) over a [width] px wide screen does. */
object SwipeRule {
    const val COMMIT_FRACTION = 0.22f
    fun commits(dx: Float, width: Float) = width > 0 && dx < 0 && -dx >= width * COMMIT_FRACTION
    /** A quick flick commits after a short drag too: [vx] is the release velocity in px/s (negative = leftwards). */
    const val FLING_VELOCITY = 900f; const val FLING_MIN_FRACTION = 0.07f
    fun commits(dx: Float, vx: Float, width: Float) = commits(dx, width) || (width > 0 && dx < 0 && -dx >= width * FLING_MIN_FRACTION && vx <= -FLING_VELOCITY)
    /** How far the face follows the finger (never rightwards, damped). */
    fun follow(dx: Float) = if (dx < 0) dx * 0.6f else 0f
}

/**
 * Right-to-left swipe. Only leftward drags are consumed, so a rightward drag still reaches the navigation drawer. Taps pass through.
 */
fun Modifier.swipeToNext(slide: SwipeSlide, onNext: (suspend () -> Unit)?): Modifier = if (onNext == null) this else composed {
    val slop = LocalViewConfiguration.current.touchSlop
    val scope = androidx.compose.runtime.rememberCoroutineScope()   // composition scope: carries the frame clock Animatable needs
    pointerInput(onNext) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var total = 0f; var dragging = false; val velocity = VelocityTracker()
            if (slide.busy) { waitForUpOrCancellation(); return@awaitEachGesture }
            velocity.addPointerInputChange(down)
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Main)
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!ch.pressed) break
                total += ch.positionChange().x; velocity.addPointerInputChange(ch)
                if (!dragging && total > slop) break                    // rightwards: the drawer's gesture
                if (!dragging && total < -slop) dragging = true
                if (dragging) { ch.consume(); val f = SwipeRule.follow(total); scope.launch { slide.offset.snapTo(f); slide.fade.snapTo(1f - minOf(0.45f, -f / size.width * 1.4f)) } }
            }
            val w = size.width.toFloat(); val vx = velocity.calculateVelocity().x
            if (dragging && SwipeRule.commits(total, vx, w)) {
                slide.busy = true
                scope.launch {
                    try {
                        // Out: slide left and fade together. Then switch and WAIT until the new face is really on screen, so it can slide in
                        // from the right instead of the old face coming back and the new one popping in later.
                        coroutineScope {
                            launch { slide.offset.animateTo(-w * 0.3f, tween(140, easing = FastOutLinearInEasing)) }
                            launch { slide.fade.animateTo(0f, tween(140, easing = FastOutLinearInEasing)) }
                        }
                        onNext()
                        slide.offset.snapTo(w * 0.18f)
                        coroutineScope {
                            launch { slide.offset.animateTo(0f, tween(260, easing = FastOutSlowInEasing)) }
                            launch { slide.fade.animateTo(1f, tween(220, easing = LinearOutSlowInEasing)) }
                        }
                    } finally { slide.busy = false }
                }
            } else if (dragging) scope.launch { coroutineScope { launch { slide.offset.animateTo(0f, tween(180, easing = FastOutSlowInEasing)) }; launch { slide.fade.animateTo(1f, tween(180)) } } }
        }
    }
}
