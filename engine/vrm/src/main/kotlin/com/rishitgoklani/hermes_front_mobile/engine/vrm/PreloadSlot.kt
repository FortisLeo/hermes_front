package com.rishitgoklani.hermes_front_mobile.engine.vrm

import java.util.concurrent.Executor
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/**
 * Holds at most one piece of work done ahead of time (a model parsed and decoded while the app's UI is still starting), for the one
 * caller that will want it. [take] hands it over once; anything not taken in time, or asked for under another key, is [discard]ed
 * so decoded images never linger in memory.
 */
class PreloadSlot<T : Any>(private val discard: (T) -> Unit, private val ttlMs: Long = 8_000, private val now: () -> Long = System::currentTimeMillis) {
    private class Job<T>(val key: String, val task: FutureTask<T?>, val startedAt: Long)
    private var job: Job<T>? = null

    /** Starts [work] for [key] on [executor] unless the same key is already held. Replaces (and frees) anything else. */
    fun start(key: String, executor: Executor, work: () -> T?) {
        val old: Job<T>?; val fresh: Job<T>
        synchronized(this) {
            if (job?.key == key) return
            old = job; fresh = Job(key, FutureTask(work), now()); job = fresh
        }
        old?.let(::drop)
        executor.execute(fresh.task)
    }

    /** The result for [key] (waiting up to [waitMs] if it is still being made), or null. Whatever was held is gone afterwards. */
    fun take(key: String, waitMs: Long): T? {
        val j = synchronized(this) { job.also { job = null } } ?: return null
        if (j.key != key || now() - j.startedAt > ttlMs) { drop(j); return null }
        return try { j.task.get(waitMs, TimeUnit.MILLISECONDS) } catch (_: Throwable) { drop(j); null }
    }

    /** Frees the held result if it is still for [key] and older than the time-to-live (call it from a timer). */
    fun expire(key: String) {
        val j = synchronized(this) { job?.takeIf { it.key == key && now() - it.startedAt >= ttlMs }?.also { job = null } } ?: return
        drop(j)
    }

    fun clear() { synchronized(this) { job.also { job = null } }?.let(::drop) }

    val heldKey: String? get() = synchronized(this) { job?.key }

    private fun drop(j: Job<T>) {
        if (j.task.isDone) runCatching { j.task.get() }.getOrNull()?.let(discard)
        else j.task.cancel(true)   // still running: its result is never handed out; the GC frees it
    }
}
