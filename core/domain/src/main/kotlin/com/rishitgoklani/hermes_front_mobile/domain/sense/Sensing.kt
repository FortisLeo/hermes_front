package com.rishitgoklani.hermes_front_mobile.domain.sense

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ln
import kotlin.math.sqrt

data class AccelSample(val x: Float, val y: Float, val z: Float, val tMs: Long) {
    val magnitude get() = sqrt(x * x + y * y + z * z)
}

enum class MotionKind { PickUp, Knock, Tilt, PutDown }

/** Classifies accelerometer streams into desk events. Pure and deterministic: feed samples in time order (~5-20 Hz). */
class MotionDetector(private val gravity: Float = 9.81f) {
    private var stillSince: Long? = null
    private var disturbedSince: Long? = null
    private var baseline: FloatArray? = null
    private var lastEvent = 0L
    private var wasMoving = false

    fun feed(s: AccelSample): MotionKind? {
        val dev = abs(s.magnitude - gravity)
        val calm = dev < 0.6f
        if (calm) {
            if (stillSince == null) stillSince = s.tMs
            disturbedSince = null
            val stillFor = s.tMs - (stillSince ?: s.tMs)
            if (stillFor > 1000) {
                val b = baseline
                if (b == null) baseline = floatArrayOf(s.x, s.y, s.z)
                else if (angleBetween(b, floatArrayOf(s.x, s.y, s.z)) > 35f && s.tMs - lastEvent > 2000) { baseline = floatArrayOf(s.x, s.y, s.z); lastEvent = s.tMs; return MotionKind.Tilt }
            }
            if (wasMoving && stillFor > 800) { wasMoving = false; baseline = floatArrayOf(s.x, s.y, s.z); if (s.tMs - lastEvent > 1500) { lastEvent = s.tMs; return MotionKind.PutDown } }
            return null
        }
        // disturbed
        val wasStillLong = stillSince?.let { s.tMs - it > 1500 } ?: false
        if (disturbedSince == null) disturbedSince = s.tMs
        val disturbedFor = s.tMs - (disturbedSince ?: s.tMs)
        stillSince = null
        if (dev > 4f && disturbedFor < 150 && wasStillLong && s.tMs - lastEvent > 600) { lastEvent = s.tMs; disturbedSince = null; return MotionKind.Knock }
        if (disturbedFor > 300 && dev > 1.2f && s.tMs - lastEvent > 1500) { lastEvent = s.tMs; wasMoving = true; return MotionKind.PickUp }
        if (disturbedFor > 300) wasMoving = true
        return null
    }

    private fun angleBetween(a: FloatArray, b: FloatArray): Float {
        val dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
        val na = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); val nb = sqrt(b[0] * b[0] + b[1] * b[1] + b[2] * b[2])
        if (na == 0f || nb == 0f) return 0f
        return Math.toDegrees(acos((dot / (na * nb)).coerceIn(-1f, 1f)).toDouble()).toFloat()
    }
}

/** Maps ambient lux to a comfortable brightness percent with hysteresis so the screen does not hunt. */
class AmbientBrightness(private val minPercent: Int = 5, private val maxPercent: Int = 100, private val hysteresis: Int = 8) {
    private var current: Int? = null

    fun percentFor(lux: Float): Int {
        val l = lux.coerceAtLeast(0f)
        if (l < 1f) return minPercent
        val p = minPercent + (maxPercent - minPercent) * (ln(1 + l) / ln(1 + 1000f)).coerceIn(0f, 1f)
        return p.toInt().coerceIn(minPercent, maxPercent)
    }

    /** Returns a new percent to apply, or null if the change is below the hysteresis threshold. */
    fun update(lux: Float): Int? {
        val target = percentFor(lux); val cur = current
        if (cur != null && abs(target - cur) < hysteresis) return null
        current = target; return target
    }
}

/** Port: counts faces in a JPEG (no identity). */
fun interface FaceCounter { suspend fun count(jpeg: ByteArray): Int }
