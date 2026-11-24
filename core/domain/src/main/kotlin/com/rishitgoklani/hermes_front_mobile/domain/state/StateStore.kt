package com.rishitgoklani.hermes_front_mobile.domain.state

import kotlinx.serialization.json.Json

/** Port: tiny durable key/value text store for small feature state (rules, configs). Values are JSON text. */
interface StateStore {
    fun load(key: String): String?
    fun save(key: String, value: String)
}

/** Shared lenient JSON used by the feature stores so unknown/removed fields never lose the user's data. */
val StateJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** One unit of background work run by [PeriodicRunner]; each task gates itself on its own interval. */
interface PeriodicTask {
    val name: String
    suspend fun tick(nowMs: Long)
}

/** Registry of periodic tasks driven by a single coarse loop (add a task by registering it; nothing else changes). */
class PeriodicRunner(tasks: List<PeriodicTask> = emptyList(), private val onError: (String, Throwable) -> Unit = { _, _ -> }) {
    private val tasks = tasks.toMutableList()
    fun register(task: PeriodicTask) { require(tasks.none { it.name == task.name }) { "Duplicate task: ${task.name}" }; tasks += task }
    fun names(): List<String> = tasks.map { it.name }
    /** One failing task never blocks the others. */
    suspend fun tickAll(nowMs: Long) {
        tasks.toList().forEach { t -> try { t.tick(nowMs) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Throwable) { onError(t.name, e) } }
    }
}
