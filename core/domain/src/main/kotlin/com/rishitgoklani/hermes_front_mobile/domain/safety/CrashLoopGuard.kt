package com.rishitgoklani.hermes_front_mobile.domain.safety

/** Port: persisted launch timestamps. */
interface LaunchStore { fun load(): List<Long>; fun save(times: List<Long>) }

/** Crash-loop detection (#389): too many launches inside the window without an intervening healthy mark = safe mode. */
class CrashLoopGuard(private val store: LaunchStore, private val clock: () -> Long = System::currentTimeMillis, private val threshold: Int = 4, private val windowMs: Long = 5 * 60_000) {
    /** Call once per process start. Returns true when the app should start in safe mode. */
    fun onLaunch(): Boolean {
        val now = clock(); val recent = (store.load() + now).filter { now - it <= windowMs }; store.save(recent)
        return recent.size >= threshold
    }
    /** Call after the app has been up long enough to be considered healthy; clears the history. */
    fun markHealthy() = store.save(emptyList())
}
