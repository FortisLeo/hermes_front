package com.rishitgoklani.hermes_front_mobile.domain.sense

/** Adaptive loudness spike detector (#125): flags a sample far above the slowly-moving room baseline (doorbell, clap, glass), with a cooldown. */
class SpikeDetector(private val marginDb: Double = 18.0, private val cooldownMs: Long = 20_000, private val clock: () -> Long = System::currentTimeMillis) {
    private var baseline: Double? = null
    private var last = Long.MIN_VALUE / 2
    /** Returns the dB above baseline when this sample is a spike, else null. */
    fun update(dbfs: Double): Double? {
        val b = baseline; if (b == null) { baseline = dbfs; return null }
        val over = dbfs - b; val now = clock()
        if (over >= marginDb && now - last >= cooldownMs) { last = now; return over }
        baseline = b * 0.9 + dbfs * 0.1   // follow slow changes (ventilation, music) but spikes barely move it
        return null
    }
}
