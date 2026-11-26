package com.rishitgoklani.hermes_front_mobile.engine.vrmgl

import android.os.SystemClock
import android.util.Log

/**
 * Phase timings for one model load, logged as a single line under [TAG] (`adb logcat -s DeskbotVrmPerf`). Times are wall-clock ms.
 * Cheap enough to leave on: a few clock reads and one log line per load.
 */
internal class LoadPerf(private val what: String, private val key: String) {
    companion object { const val TAG = "DeskbotVrmPerf"; fun nowMs() = SystemClock.elapsedRealtime() }
    private val t0 = nowMs(); private var last = t0
    private val parts = StringBuilder()
    /** Time since the previous mark, under [name]. */
    fun mark(name: String) { val n = nowMs(); parts.append(name).append('=').append(n - last).append(' '); last = n }
    /** A value measured elsewhere (e.g. GL queue wait), not advancing the mark clock. */
    fun put(name: String, v: Any) { parts.append(name).append('=').append(v).append(' ') }
    fun resetMark() { last = nowMs() }
    fun log() = Log.i(TAG, "$what ${key.take(8)} total=${nowMs() - t0} $parts")
}
