package com.rishitgoklani.hermes_front_mobile.engine.vrm

/** What [ModelSlots] needs from the platform: load a model asynchronously, put it on screen, free it. All callbacks arrive on the main thread. */
interface SlotHost<M : Any> {
    /**
     * Start loading [key]; call [done] once (success or failure) on the main thread. With [withTextures] false only the geometry is
     * parsed and uploaded (a staged model holds no decoded textures); [finishShow] completes it when it is about to go on screen.
     */
    fun load(key: String, withTextures: Boolean, done: (Result<M>) -> Unit)
    /** Decode and upload the textures of a geometry-only model, then call [done] (main thread). The previous model stays on screen meanwhile. */
    fun finishShow(key: String, model: M, done: (Result<Unit>) -> Unit) { done(Result.success(Unit)) }
    fun show(key: String, model: M)
    fun dispose(key: String, model: M)
    /** A load that has not finished was dropped (superseded or released): stop working on it. */
    fun cancel(key: String) {}
    fun shown(key: String, ms: Long, wasStaged: Boolean) {}
    fun staged(key: String, ms: Long) {}
    fun failed(key: String, message: String) {}
}

/**
 * Keeps at most two models in memory: the active one and the next one, staged behind it. The caller only states what it wants
 * ([want]); this reconciles loads and frees everything else, so a quick swipe sequence never leaves extra models resident.
 * The staged model is only loaded once the active one is on screen, so the two never decode at the same time, and it is loaded
 * geometry-only: its textures are decoded when it is promoted, while the previous model stays on screen until it is fully ready.
 */
class ModelSlots<M : Any>(private val host: SlotHost<M>, private val now: () -> Long = System::currentTimeMillis) {
    private class Entry<M>(val key: String, val startedAt: Long, val requestedActive: Boolean) { var model: M? = null; var texturesReady = false; var finishing = false }
    private val entries = LinkedHashMap<String, Entry<M>>()
    private var wantActive: String? = null; private var wantStaged: String? = null
    private var activeKey: String? = null
    private val failedKeys = HashSet<String>()

    val resident get() = entries.values.count { it.model != null }
    val active get() = activeKey

    fun want(active: String?, staged: String?) {
        if (active != wantActive || staged != wantStaged) { failedKeys.removeAll { it != active && it != staged }; if (active != wantActive && active != null) failedKeys.remove(active) }
        wantActive = active; wantStaged = staged?.takeIf { it != active }
        reconcile()
    }

    /** Retry a key that failed earlier (e.g. the user pressed Retry). */
    fun retry(key: String) { failedKeys.remove(key); reconcile() }

    private fun reconcile() {
        val a = wantActive; val s = wantStaged
        // 1. put the wanted active model on screen (once its textures are in)
        val ae = a?.let { entries[it] }
        if (a != null && ae?.model != null && activeKey != a) {
            if (ae.texturesReady) {
                val old = activeKey; activeKey = a; host.show(a, ae.model!!)
                host.shown(a, now() - ae.startedAt, !ae.requestedActive)
                if (old != null && old != a) release(old)   // even when it is the next stop: staged models keep no textures, so it reloads geometry-only
            } else if (!ae.finishing) finishTextures(a, ae)
        }
        if (a == null && activeKey != null) { val old = activeKey!!; activeKey = null; if (old != s) release(old) }
        // 2. free anything not wanted
        // (the model on screen stays until its replacement is fully ready, so a swipe never shows a blank frame)
        val keepOnScreen = if (a != null && activeKey != a) activeKey else null
        for (k in entries.keys.toList()) if (k != a && k != s && k != keepOnScreen && entries[k]?.model != null) release(k)
        // 3. start loads: active first, staged only when the active one is showing
        if (a != null && entries[a] == null && a !in failedKeys) startLoad(a, requestedActive = true)
        if (s != null && entries[s] == null && s !in failedKeys && (a == null || activeKey == a)) startLoad(s, requestedActive = false)
    }

    private fun finishTextures(key: String, e: Entry<M>) {
        e.finishing = true
        host.finishShow(key, e.model!!) { r ->
            if (entries[key] !== e) return@finishShow   // released meanwhile; the model was disposed with it
            e.finishing = false
            r.onSuccess { e.texturesReady = true; reconcile() }.onFailure { t -> release(key); failedKeys += key; host.failed(key, t.message ?: "Could not load the textures"); reconcile() }
        }
    }

    private fun startLoad(key: String, requestedActive: Boolean) {
        val e = Entry<M>(key, now(), requestedActive); entries[key] = e
        host.load(key, withTextures = requestedActive) { r ->
            if (entries[key] !== e) { r.getOrNull()?.let { host.dispose(key, it) }; return@load }   // superseded while loading
            r.onSuccess { m ->
                e.model = m; e.texturesReady = requestedActive
                if (key == wantActive) reconcile()
                else if (key == wantStaged) { host.staged(key, now() - e.startedAt); reconcile() }
                else release(key)
            }.onFailure { t ->
                entries.remove(key); failedKeys += key; host.failed(key, t.message ?: "Could not load the model"); reconcile()
            }
        }
    }

    private fun release(key: String) {
        val e = entries.remove(key) ?: return
        val m = e.model
        if (m != null) host.dispose(key, m) else host.cancel(key)
    }

    /** Drop everything except the model on screen and the active one still loading (memory pressure). The next [want] brings the staged model back. */
    fun dropStaged() { wantStaged = null; for (k in entries.keys.toList()) if (k != activeKey && k != wantActive) release(k) }

    fun releaseAll() { wantActive = null; wantStaged = null; activeKey = null; for (k in entries.keys.toList()) release(k) }
}
