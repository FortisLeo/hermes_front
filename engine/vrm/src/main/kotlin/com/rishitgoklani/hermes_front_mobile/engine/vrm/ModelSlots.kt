package com.rishitgoklani.hermes_front_mobile.engine.vrm

/** What [ModelSlots] needs from the platform: load a model asynchronously, put it on screen, free it. All callbacks arrive on the main thread. */
interface SlotHost<M : Any> {
    /** Start loading [key]; call [done] once (success or failure) on the main thread. */
    fun load(key: String, done: (Result<M>) -> Unit)
    fun show(key: String, model: M)
    fun dispose(key: String, model: M)
    fun shown(key: String, ms: Long, wasStaged: Boolean) {}
    fun staged(key: String, ms: Long) {}
    fun failed(key: String, message: String) {}
}

/**
 * Keeps at most two models in memory: the active one and the next one, staged behind it. The caller only states what it wants
 * ([want]); this reconciles loads and frees everything else, so a quick swipe sequence never leaves extra models resident.
 * The staged model is only loaded once the active one is on screen, so the two never decode at the same time.
 */
class ModelSlots<M : Any>(private val host: SlotHost<M>, private val now: () -> Long = System::currentTimeMillis) {
    private class Entry<M>(val key: String, val startedAt: Long, val requestedActive: Boolean) { var model: M? = null }
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
        // 1. put the wanted active model on screen
        if (a != null) entries[a]?.takeIf { it.model != null && activeKey != a }?.let { e ->
            val old = activeKey; activeKey = a; host.show(a, e.model!!)
            host.shown(a, now() - e.startedAt, !e.requestedActive)
            if (old != null && old != s && old != a) release(old)
        }
        if (a == null && activeKey != null) { val old = activeKey!!; activeKey = null; if (old != s) release(old) }
        // 2. free anything not wanted
        // (the model on screen stays until its replacement is ready, so a swipe never shows a blank frame)
        val keepOnScreen = if (a != null && entries[a]?.model == null) activeKey else null
        for (k in entries.keys.toList()) if (k != a && k != s && k != keepOnScreen && entries[k]?.model != null) release(k)
        // 3. start loads: active first, staged only when the active one is showing
        if (a != null && entries[a] == null && a !in failedKeys) startLoad(a, requestedActive = true)
        if (s != null && entries[s] == null && s !in failedKeys && (a == null || activeKey == a)) startLoad(s, requestedActive = false)
    }

    private fun startLoad(key: String, requestedActive: Boolean) {
        val e = Entry<M>(key, now(), requestedActive); entries[key] = e
        host.load(key) { r ->
            if (entries[key] !== e) { r.getOrNull()?.let { host.dispose(key, it) }; return@load }   // superseded while loading
            r.onSuccess { m ->
                e.model = m
                if (key == wantActive) reconcile()
                else if (key == wantStaged) { host.staged(key, now() - e.startedAt); reconcile() }
                else release(key)
            }.onFailure { t ->
                entries.remove(key); failedKeys += key; host.failed(key, t.message ?: "Could not load the model"); reconcile()
            }
        }
    }

    private fun release(key: String) { entries.remove(key)?.model?.let { host.dispose(key, it) } }

    fun releaseAll() { wantActive = null; wantStaged = null; activeKey = null; for (k in entries.keys.toList()) release(k) }
}
