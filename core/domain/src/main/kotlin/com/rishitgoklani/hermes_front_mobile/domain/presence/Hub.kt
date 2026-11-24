package com.rishitgoklani.hermes_front_mobile.domain.presence

import com.rishitgoklani.hermes_front_mobile.domain.BotState
import com.rishitgoklani.hermes_front_mobile.domain.face.Expr
import com.rishitgoklani.hermes_front_mobile.domain.routines.DeviceEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject

/** Everything a persona behavior can react to. [Tick] is the heartbeat for time-based behaviors. */
sealed interface Input {
    data class Device(val event: DeviceEvent) : Input
    data class Heard(val text: String) : Input
    data class State(val state: BotState) : Input
    data object Tick : Input
}

enum class Urgency { Low, Normal, Important }

/** What a behavior wants done. Behaviors never touch hardware; a [CueSink] applies these. */
sealed interface Cue {
    data class Speak(val text: String, val urgency: Urgency = Urgency.Normal) : Cue
    data class Express(val expr: Expr) : Cue
    data class Look(val x: Float?, val y: Float?) : Cue
    data class Call(val tool: String, val args: JsonObject = JsonObject(emptyMap())) : Cue
}

/** Strategy: one persona behavior (status face, greeting, escalation...). Add one by adding a class and registering it. */
interface Behavior {
    val id: String
    suspend fun react(input: Input): List<Cue>
}

/** Registry: behaviors by id; registering never edits an existing class. */
class BehaviorRegistry(behaviors: List<Behavior> = emptyList()) {
    private val byId = LinkedHashMap<String, Behavior>()
    init { behaviors.forEach(::register) }
    fun register(b: Behavior) { require(b.id !in byId) { "Duplicate behavior: ${b.id}" }; byId[b.id] = b }
    fun list(): List<Behavior> = byId.values.toList()
    fun get(id: String): Behavior? = byId[id]
}

/** Port: applies a cue to the real body (speech, face, tools). */
fun interface CueSink { suspend fun apply(cue: Cue) }

/**
 * Facade over the behavior registry: feeds inputs to every behavior, gates speech through [QuietPolicy]
 * (#333) and holds blocked speech until the room is quiet-safe again. One failing behavior never stops the others.
 */
class BehaviorHub(
    private val registry: BehaviorRegistry,
    private val quiet: QuietContextPort,
    private val sink: CueSink,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxHeld: Int = 5,
    private val holdTtlMs: Long = 30 * 60_000L,
) {
    private data class Held(val cue: Cue.Speak, val at: Long)
    private val held = ArrayDeque<Held>()
    val heldCount: Int get() = held.size

    suspend fun dispatch(input: Input) {
        if (input is Input.Tick) release()
        for (b in registry.list()) {
            val cues = try { b.react(input) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
            publish(cues)
        }
    }

    suspend fun publish(cues: List<Cue>) {
        for (cue in cues) {
            if (cue !is Cue.Speak) { sink.apply(cue); continue }
            if (QuietPolicy.allows(cue.urgency, quiet.current())) sink.apply(cue) else hold(cue)
        }
    }

    private fun hold(c: Cue.Speak) {
        if (c.urgency == Urgency.Low) return  // low-value chatter is dropped, not queued
        if (held.any { it.cue.text == c.text }) return
        held.addLast(Held(c, clock()))
        while (held.size > maxHeld) held.removeFirst()
    }

    /** Speaks held cues once the context allows. Expired ones are dropped. */
    suspend fun release() {
        val now = clock()
        held.removeAll { now - it.at > holdTtlMs }
        if (held.isEmpty()) return
        val ctx = quiet.current()
        val it = held.iterator()
        while (it.hasNext()) { val h = it.next(); if (QuietPolicy.allows(h.cue.urgency, ctx)) { it.remove(); sink.apply(h.cue) } }
    }

    /** Heartbeat loop for the app scope; cancel to stop. */
    suspend fun runTicks(periodMs: Long) { while (true) { delay(periodMs); dispatch(Input.Tick) } }
}
