package com.rishitgoklani.hermes_front_mobile.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rishitgoklani.hermes_front_mobile.domain.SettingsRepository
import com.rishitgoklani.hermes_front_mobile.domain.behavior.DebugSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep { Welcome, Permissions, Connection }

/** The permissions already declared in the manifest, grouped the way a person thinks about them. */
enum class PermissionGroup(val title: String, val why: String) {
    Microphone("Microphone", "So Deskbot can hear you: push-to-talk, the \"hey bot\" wake word and the voice conversation."),
    Camera("Camera", "So Deskbot can see: noticing that someone is at the desk and looking at what you show it. Frames are not stored."),
    Notifications("Notifications", "So Deskbot can show its status while it runs in the background and tell you when it needs attention."),
}

/** Pure step machine: only legal moves, no Android. */
data class OnboardingProgress(val step: OnboardingStep = OnboardingStep.Welcome, val finished: Boolean = false) {
    val isFirst: Boolean get() = step == OnboardingStep.Welcome
    val isLast: Boolean get() = step == OnboardingStep.entries.last()
    /** 1-based, for "Step 2 of 3". */
    val number: Int get() = step.ordinal + 1
    val total: Int get() = OnboardingStep.entries.size

    fun next(): OnboardingProgress = when { finished -> this; isLast -> copy(finished = true); else -> copy(step = OnboardingStep.entries[step.ordinal + 1]) }
    fun back(): OnboardingProgress = if (finished || isFirst) this else copy(step = OnboardingStep.entries[step.ordinal - 1])
    fun skip(): OnboardingProgress = copy(finished = true)
}

sealed interface ConnectionStatus {
    data object Idle : ConnectionStatus
    data object Testing : ConnectionStatus
    data class Ok(val version: String) : ConnectionStatus
    data class Failed(val reason: String) : ConnectionStatus
}

data class OnboardingUiState(
    /** False until the stored flag is read, so the intro never flashes for returning users. */
    val visible: Boolean = false,
    val progress: OnboardingProgress = OnboardingProgress(),
    val permissions: Map<PermissionGroup, Boolean> = PermissionGroup.entries.associateWith { false },
    val connection: ConnectionStatus = ConnectionStatus.Idle,
)

/** First-run introduction: shown once, skippable, reachable again from Settings through [replay]. */
class OnboardingViewModel(private val settings: SettingsRepository, private val debug: DebugSource) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state

    init { viewModelScope.launch { val done = settings.onboardingDone.first(); _state.update { it.copy(visible = !done) } } }

    fun next() = advance { it.next() }
    fun back() = advance { it.back() }
    fun skip() = advance { it.skip() }

    private fun advance(move: (OnboardingProgress) -> OnboardingProgress) {
        val p = move(_state.value.progress)
        if (p.finished) { _state.update { it.copy(visible = false, progress = p) }; viewModelScope.launch { settings.setOnboardingDone(true) } }
        else _state.update { it.copy(progress = p) }
    }

    fun replay() { _state.update { OnboardingUiState(visible = true, permissions = it.permissions) } }

    /** The app reports what the system says is granted; it is re-read after every permission dialog and on resume. */
    fun setPermissions(granted: Map<PermissionGroup, Boolean>) { _state.update { it.copy(permissions = granted) } }

    fun testConnection() {
        if (_state.value.connection == ConnectionStatus.Testing) return
        _state.update { it.copy(connection = ConnectionStatus.Testing) }
        viewModelScope.launch {
            val result = try {
                val s = debug.snapshot()
                if (s.hermesHealthy) ConnectionStatus.Ok(s.hermesVersion) else ConnectionStatus.Failed(s.note.ifBlank { "Hermes did not answer. Is it running in Termux?" })
            } catch (e: CancellationException) { throw e } catch (e: Exception) { ConnectionStatus.Failed(e.message ?: "Hermes did not answer") }
            _state.update { it.copy(connection = result) }
        }
    }
}
