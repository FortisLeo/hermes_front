package com.rishitgoklani.hermes_front_mobile.engine.vrm

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Decodes a model's images on a pool and hands each one to the GL thread as soon as it is ready, instead of decoding one, waiting for
 * its upload, then decoding the next. At most [maxAlive] decoded images exist at once (being decoded or waiting for upload), because
 * one image can be 16 MB. [run] blocks until every image is uploaded or skipped, one fails, or the abort check throws.
 */
class TexturePipeline(private val pool: Executor, private val maxAlive: Int = 3) {
    /**
     * @param decode runs on the pool; null skips the image.
     * @param upload hands a decoded item to the GL thread; must call its callback exactly once (null = uploaded, else the failure).
     * @param discard frees an item that will not be uploaded (stopped early).
     * @param abortCheck called about every [tickMs] while waiting; throw to stop (cancelled, GL gone, waited too long).
     */
    fun <T : Any> run(
        images: List<Int>, decode: (Int) -> T?, upload: (Int, T, (Throwable?) -> Unit) -> Unit, discard: (T) -> Unit,
        abortCheck: () -> Unit = {}, tickMs: Long = 250,
    ) {
        if (images.isEmpty()) return
        val alive = Semaphore(maxAlive); val left = CountDownLatch(images.size)
        val error = AtomicReference<Throwable?>(); val stop = AtomicBoolean(false)
        fun fail(t: Throwable) { error.compareAndSet(null, t); stop.set(true) }
        for (img in images) pool.execute {
            var permit = false; var handedOff = false
            try {
                while (!stop.get() && !permit) permit = alive.tryAcquire(tickMs, TimeUnit.MILLISECONDS)
                if (!permit) return@execute
                val item = decode(img) ?: return@execute
                if (stop.get()) { discard(item); return@execute }
                handedOff = true
                upload(img, item) { t -> if (t != null) fail(t); alive.release(); left.countDown() }
            } catch (t: Throwable) { fail(t) }
            finally { if (!handedOff) { if (permit) alive.release(); left.countDown() } }
        }
        while (!left.await(tickMs, TimeUnit.MILLISECONDS)) {
            if (stop.get()) break
            try { abortCheck() } catch (t: Throwable) { stop.set(true); throw t }
        }
        error.get()?.let { throw it }
    }
}
