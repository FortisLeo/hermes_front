package com.rishitgoklani.hermes_front_mobile.domain

/** Picks the highest JPEG quality whose output fits the budget (descending candidates); falls back to the lowest. */
object QualityPlanner {
    inline fun pick(maxBytes: Int, candidates: List<Int> = listOf(85, 75, 65, 55, 45, 35, 25), sizeAt: (Int) -> Int): Int =
        candidates.firstOrNull { sizeAt(it) <= maxBytes } ?: candidates.last()

    /** Longest-side scale factor (<=1) to bring an image down to [targetLongSide]. */
    fun scaleFor(width: Int, height: Int, targetLongSide: Int): Float {
        val longest = maxOf(width, height)
        return if (longest <= targetLongSide || longest <= 0) 1f else targetLongSide.toFloat() / longest
    }
}
