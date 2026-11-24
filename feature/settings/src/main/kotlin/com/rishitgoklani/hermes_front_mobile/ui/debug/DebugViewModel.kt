package com.rishitgoklani.hermes_front_mobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rishitgoklani.hermes_front_mobile.domain.behavior.DebugSnapshot
import com.rishitgoklani.hermes_front_mobile.domain.behavior.DebugSource
import com.rishitgoklani.hermes_front_mobile.domain.diagnostics.CrashLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DebugUiState(val loading: Boolean = false, val snapshot: DebugSnapshot? = null, val error: String? = null, val lastCrash: String? = null)

class DebugViewModel(private val source: DebugSource, private val crashes: CrashLog? = null) : ViewModel() {
    private val _state = MutableStateFlow(DebugUiState(lastCrash = crashes?.latest()))
    val state: StateFlow<DebugUiState> = _state
    init { refresh() }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null, lastCrash = crashes?.latest()) }
        viewModelScope.launch {
            val crash = crashes?.latest()
            _state.value = try { DebugUiState(snapshot = source.snapshot(), lastCrash = crash) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { DebugUiState(error = e.message ?: "failed", lastCrash = crash) }
        }
    }

    fun clearCrash() { crashes?.clear(); _state.update { it.copy(lastCrash = null) } }
}
