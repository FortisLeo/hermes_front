package com.rishitgoklani.hermes_front_mobile

import android.os.Process
import android.os.SystemClock
import android.util.Log

/** Startup milestones as "ms since the process started", logged under DeskbotVrmPerf so one logcat filter shows the whole cold start. Stateless. */
internal object StartupTrace {
    fun mark(name: String) = Log.i("DeskbotVrmPerf", "startup $name=${SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()}")
    inline fun <T> span(name: String, block: () -> T): T { val t = SystemClock.elapsedRealtime(); return block().also { Log.i("DeskbotVrmPerf", "startup $name took=${SystemClock.elapsedRealtime() - t}") } }
}
