package com.rishitgoklani.hermes_front_mobile.engine.vrm

import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Decides which vsyncs to draw. With a 60 Hz display and the defaults it draws every 2nd vsync while something moves (30 fps) and every
 * 6th while idle (10 fps); on 120 Hz it scales to every 4th / 12th. Time based with half a vsync of slack, so scheduling jitter does not skip a frame.
 */
class FramePacer(val vsyncMs: Float = 16.67f, movingFps: Int = 30, idleFps: Int = 10) {
    val movingEvery = max(1, (1000f / vsyncMs / movingFps).roundToInt())
    val idleEvery = max(movingEvery, (1000f / vsyncMs / idleFps).roundToInt())
    private val vsyncNanos = (vsyncMs * 1_000_000f).toLong()
    private var last = Long.MIN_VALUE

    /** True when a frame should be drawn at [nowNanos]. [urgent] (GL work waiting) draws on every vsync. */
    fun shouldRender(moving: Boolean, nowNanos: Long, urgent: Boolean = false): Boolean {
        val every = if (urgent) 1 else if (moving) movingEvery else idleEvery
        if (last == Long.MIN_VALUE || nowNanos - last >= vsyncNanos * every - vsyncNanos / 2) { last = nowNanos; return true }
        return false
    }

    /** How long to wait before looking again while idle (just under one idle interval, so the next vsync lands on a draw). */
    fun idleDelayMs(): Long = ((idleEvery - 1) * vsyncMs).toLong()

    fun reset() { last = Long.MIN_VALUE }
}

/**
 * Says when rendering may stop completely: nothing moved for [quietNanos] and no extra awake time was requested.
 * [touch] is called for anything that could change the picture (input, new model, snapshot); [update] reports ongoing motion.
 */
class IdleGate(private val quietNanos: Long = 3_000_000_000L) {
    private var awakeUntil = Long.MIN_VALUE
    private var started = false

    fun touch(nowNanos: Long, forNanos: Long = quietNanos) { started = true; awakeUntil = max(awakeUntil, nowNanos + forNanos) }
    fun update(busy: Boolean, nowNanos: Long) { if (busy || !started) touch(nowNanos) }
    fun asleep(nowNanos: Long): Boolean = started && nowNanos >= awakeUntil
}

/**
 * GL work handed over by loader threads, run on the GL thread at the start of a frame. At most [maxTasks] run per frame and no new
 * one starts once [budgetNanos] have been spent, so a big texture upload never stalls animation for long.
 */
class GlWorkQueue(private val clock: () -> Long = System::nanoTime) {
    private val tasks = ConcurrentLinkedQueue<() -> Unit>()

    /** The "needs GL work" flag: the render loop must keep running (and wake from sleep) while this is true. */
    val hasWork: Boolean get() = !tasks.isEmpty()

    fun add(task: () -> Unit) { tasks.add(task) }

    /** Runs queued tasks (GL thread). The first always runs; more only while under budget and [maxTasks]. Returns how many ran. */
    fun drain(budgetNanos: Long = 4_000_000L, maxTasks: Int = 1): Int {
        val start = clock(); var ran = 0
        while (ran < maxTasks) {
            if (ran > 0 && clock() - start >= budgetNanos) break
            val t = tasks.poll() ?: break
            runCatching(t); ran++   // a task reports its own failure to whoever waits for it
        }
        return ran
    }

    fun clear() { tasks.clear() }
}

enum class TrimAction { NONE, DROP_STAGED, RELEASE_ALL }

/** What to free when Android asks for memory back. Levels are the ComponentCallbacks2.TRIM_MEMORY_* constants. */
class MemoryPolicy(private val lowRamDevice: Boolean, val backgroundDelayMs: Long = 30_000L) {
    companion object { const val RUNNING_LOW = 10; const val RUNNING_CRITICAL = 15; const val UI_HIDDEN = 20; const val BACKGROUND = 40 }

    fun onTrim(level: Int): TrimAction = when {
        level >= BACKGROUND -> TrimAction.RELEASE_ALL                       // our process is on the kill list: give everything back
        level >= UI_HIDDEN -> if (lowRamDevice) TrimAction.RELEASE_ALL else TrimAction.DROP_STAGED
        level >= RUNNING_LOW -> TrimAction.DROP_STAGED
        else -> TrimAction.NONE
    }
}
