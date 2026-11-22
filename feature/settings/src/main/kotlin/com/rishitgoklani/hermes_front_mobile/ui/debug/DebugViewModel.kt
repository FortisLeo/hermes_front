package com.rishitgoklani.hermes_front_mobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rishitgoklani.hermes_front_mobile.domain.behavior.DebugSnapshot
import com.rishitgoklani.hermes_front_mobile.domain.behavior.DebugSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DebugUiState(val loading: Boolean = false, val snapshot: DebugSnapshot? = null, val error: String? = null)

class DebugViewModel(private val source: DebugSource) : ViewModel() {
    private val _state = MutableStateFlow(DebugUiState())
    val state: StateFlow<DebugUiState> = _state
    init { refresh() }

    fun refresh() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            _state.value = try { DebugUiState(snapshot = source.snapshot()) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { DebugUiState(error = e.message ?: "failed") }
        }
    }
}
