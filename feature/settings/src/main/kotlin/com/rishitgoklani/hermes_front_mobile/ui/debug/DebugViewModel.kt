package com.rishitgoklani.hermes_front_mobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rishitgoklani.hermes_front_mobile.domain.behavior.DebugSnapshot
import com.rishitgoklani.hermes_front_mobile.domain.behavior.DebugSource
import com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiLogEntry
import com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiLogFormat
import com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiLogReader
import com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiSource
import com.rishitgoklani.hermes_front_mobile.domain.diagnostics.CrashLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DebugUiState(
    val loading: Boolean = false, val snapshot: DebugSnapshot? = null, val error: String? = null, val lastCrash: String? = null,
    /** Every recorded API exchange, newest first, and the source filter applied to it (null = all). */
    val apiCalls: List<ApiLogEntry> = emptyList(), val apiFilter: ApiSource? = null,
) {
    val shownApiCalls: List<ApiLogEntry> get() = if (apiFilter == null) apiCalls else apiCalls.filter { it.source == apiFilter }
}

class DebugViewModel(private val source: DebugSource, private val crashes: CrashLog? = null, private val apiLog: ApiLogReader? = null) : ViewModel() {
    private val _state = MutableStateFlow(DebugUiState(lastCrash = crashes?.latest()))
    val state: StateFlow<DebugUiState> = _state
    init {
        refresh()
        apiLog?.let { log -> viewModelScope.launch { log.entries.collect { all -> _state.update { it.copy(apiCalls = all) } } } }
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null, lastCrash = crashes?.latest()) }
        viewModelScope.launch {
            val crash = crashes?.latest()
            _state.update { cur -> try { cur.copy(loading = false, snapshot = source.snapshot(), error = null, lastCrash = crash) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { cur.copy(loading = false, snapshot = null, error = e.message ?: "failed", lastCrash = crash) } }
        }
    }

    fun setApiFilter(source: ApiSource?) { _state.update { it.copy(apiFilter = source) } }
    fun clearApiLog() { apiLog?.clear() }
    /** Plain text of the calls currently shown, for the clipboard. */
    fun apiLogText(timeText: (Long) -> String): String = ApiLogFormat.all(_state.value.shownApiCalls, timeText)

    fun clearCrash() { crashes?.clear(); _state.update { it.copy(lastCrash = null) } }
}
