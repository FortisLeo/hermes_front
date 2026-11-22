package com.rishitgoklani.hermes_front_mobile.domain.sense

import kotlin.math.abs

/** Compares tiny grayscale thumbnails to decide whether the scene changed (#116). Inputs are equal-length 0..255 byte arrays. */
object FrameDiff {
    /** Mean absolute difference, 0..255. */
    fun score(a: ByteArray, b: ByteArray): Double {
        require(a.size == b.size && a.isNotEmpty()) { "thumbnails must be the same non-zero size" }
        var sum = 0L; for (i in a.indices) sum += abs((a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)); return sum.toDouble() / a.size
    }
}

/** Stateful watcher: reports a change when the score exceeds [threshold], at most once per [cooldownMs]; adapts the baseline slowly to lighting drift. */
class ChangeDetector(private val threshold: Double = 12.0, private val cooldownMs: Long = 30_000, private val clock: () -> Long = System::currentTimeMillis) {
    private var baseline: ByteArray? = null
    private var lastEvent = Long.MIN_VALUE / 2

    /** Returns the change score if this frame counts as a change event, else null. */
    fun update(thumb: ByteArray): Double? {
        val b = baseline
        if (b == null || b.size != thumb.size) { baseline = thumb; return null }
        val s = FrameDiff.score(b, thumb); val now = clock()
        return if (s >= threshold && now - lastEvent >= cooldownMs) { lastEvent = now; baseline = thumb; s }
        else { if (s < threshold) baseline = ByteArray(b.size) { i -> (((b[i].toInt() and 0xFF) * 3 + (thumb[i].toInt() and 0xFF)) / 4).toByte() }; null }
    }
    fun reset() { baseline = null }
}
