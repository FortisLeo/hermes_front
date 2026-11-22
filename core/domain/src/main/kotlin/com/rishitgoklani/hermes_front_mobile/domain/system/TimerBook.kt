package com.rishitgoklani.hermes_front_mobile.domain.system

data class TimerEntry(val name: String, val endsAt: Long, val totalMs: Long)

/** Named timers and stopwatches (pure; the scheduler that fires them is injected). */
class TimerBook(private val clock: () -> Long = System::currentTimeMillis) {
    private val timers = LinkedHashMap<String, TimerEntry>()
    private val stopwatches = HashMap<String, Long>()

    @Synchronized fun start(name: String, durationMs: Long): TimerEntry {
        require(durationMs in 1..(24 * 3600_000L)) { "duration must be 1 ms .. 24 h" }
        return TimerEntry(name, clock() + durationMs, durationMs).also { timers[name.lowercase()] = it }
    }
    @Synchronized fun cancel(name: String): Boolean = timers.remove(name.lowercase()) != null
    @Synchronized fun remaining(name: String): Long? = timers[name.lowercase()]?.let { (it.endsAt - clock()).coerceAtLeast(0) }
    @Synchronized fun list(): List<Pair<TimerEntry, Long>> = timers.values.map { it to (it.endsAt - clock()).coerceAtLeast(0) }
    @Synchronized fun expire(name: String) { timers.remove(name.lowercase()) }

    @Synchronized fun stopwatchStart(name: String) { stopwatches[name.lowercase()] = clock() }
    @Synchronized fun stopwatchStop(name: String): Long? = stopwatches.remove(name.lowercase())?.let { clock() - it }

    companion object {
        /** "1h 30m", "90s", "5 min", "1:30:00", "45" (seconds) -> ms; null if unparsable. */
        fun parseDuration(text: String): Long? {
            val t = text.trim().lowercase()
            if (t.isEmpty()) return null
            Regex("""^(\d+):(\d{1,2})(?::(\d{1,2}))?$""").matchEntire(t)?.let {
                val (a, b, c) = it.destructured
                return if (c.isEmpty()) (a.toLong() * 60 + b.toLong()) * 1000 else (a.toLong() * 3600 + b.toLong() * 60 + c.toLong()) * 1000
            }
            t.toLongOrNull()?.let { return it * 1000 }
            var total = 0L; var matched = false
            Regex("""(\d+(?:\.\d+)?)\s*(h|hr|hrs|hour|hours|m|min|mins|minute|minutes|s|sec|secs|second|seconds)\b""").findAll(t).forEach {
                matched = true
                val v = it.groupValues[1].toDouble()
                total += (v * when (it.groupValues[2][0]) { 'h' -> 3600_000.0; 'm' -> 60_000.0; else -> 1000.0 }).toLong()
            }
            return if (matched) total else null
        }

        fun format(ms: Long): String {
            val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
            return when { h > 0 -> "${h}h ${m}m ${sec}s"; m > 0 -> "${m}m ${sec}s"; else -> "${sec}s" }
        }
    }
}
