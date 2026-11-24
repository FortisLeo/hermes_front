package com.rishitgoklani.hermes_front_mobile.domain.presence

import com.rishitgoklani.hermes_front_mobile.domain.BotState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface TaskState {
    data object None : TaskState
    data class Running(val name: String, val done: Int, val total: Int) : TaskState
    data class Paused(val name: String, val done: Int, val total: Int) : TaskState
    data class Finished(val name: String, val failed: String? = null) : TaskState
}

/**
 * #335 Interruptible tasks: a long multi-step task pauses between steps when the user starts speaking
 * (state Listening) and resumes once the turn is over (state Idle). A step already in flight finishes first.
 */
class TaskSupervisor(private val scope: CoroutineScope) : Behavior {
    override val id = "task-supervisor"
    private val _state = MutableStateFlow<TaskState>(TaskState.None)
    val state: StateFlow<TaskState> = _state
    private val paused = MutableStateFlow(false)
    private var job: Job? = null

    val busy: Boolean get() = _state.value.let { it is TaskState.Running || it is TaskState.Paused }

    /** Starts [steps] in order; false when another task is still running. A step returning a non-null string aborts with that error. */
    fun start(name: String, steps: List<suspend () -> String?>): Boolean {
        if (busy || steps.isEmpty()) return false
        paused.value = false
        _state.value = TaskState.Running(name, 0, steps.size)
        job = scope.launch {
            try {
                steps.forEachIndexed { i, step ->
                    paused.first { !it }
                    _state.value = if (paused.value) TaskState.Paused(name, i, steps.size) else TaskState.Running(name, i, steps.size)
                    val err = step()
                    if (err != null) { _state.value = TaskState.Finished(name, err); return@launch }
                }
                _state.value = TaskState.Finished(name)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.value = TaskState.Finished(name, e.message ?: "failed") }
        }
        return true
    }

    fun pause(): Boolean {
        val s = _state.value as? TaskState.Running ?: return false
        paused.value = true; _state.value = TaskState.Paused(s.name, s.done, s.total); return true
    }

    fun resume(): Boolean {
        val s = _state.value as? TaskState.Paused ?: return false
        paused.value = false; _state.value = TaskState.Running(s.name, s.done, s.total); return true
    }

    fun cancel(): Boolean { if (!busy) return false; job?.cancel(); paused.value = false; _state.value = TaskState.Finished((_state.value as? TaskState.Running)?.name ?: (_state.value as TaskState.Paused).name, "cancelled"); return true }

    override suspend fun react(input: Input): List<Cue> {
        if (input !is Input.State) return emptyList()
        return when (input.state) {
            BotState.Listening -> if (pause()) listOf(Cue.Speak("Pausing the task.", Urgency.Low)) else emptyList()
            BotState.Idle -> if (resume()) listOf(Cue.Speak("Resuming the task.", Urgency.Low)) else emptyList()
            else -> emptyList()
        }
    }
}
