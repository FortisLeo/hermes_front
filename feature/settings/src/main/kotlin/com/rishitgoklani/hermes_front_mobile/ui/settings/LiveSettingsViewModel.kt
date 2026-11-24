package com.rishitgoklani.hermes_front_mobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rishitgoklani.hermes_front_mobile.domain.live.LiveSettings
import com.rishitgoklani.hermes_front_mobile.domain.live.LiveSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LiveSettingsUiState(val settings: LiveSettings = LiveSettings(), val saved: Boolean = false)

/** One ViewModel for the Gemini Live section: switches and voice persist at once, the key and model text on Save. */
class LiveSettingsViewModel(private val repo: LiveSettingsRepository) : ViewModel() {
    private val _state = MutableStateFlow(LiveSettingsUiState())
    val state: StateFlow<LiveSettingsUiState> = _state

    init { viewModelScope.launch { val s = repo.settings.first(); _state.update { it.copy(settings = s) } } }

    fun onEnabled(v: Boolean) = change(persist = true) { it.copy(enabled = v) }
    fun onVoice(v: String) = change(persist = true) { it.copy(voice = v) }
    fun onHalfDuplex(v: Boolean) = change(persist = true) { it.copy(halfDuplex = v) }
    fun onAllTools(v: Boolean) = change(persist = true) { it.copy(allTools = v) }
    fun onApiKey(v: String) = change(persist = false) { it.copy(apiKey = v) }
    fun onModel(v: String) = change(persist = false) { it.copy(model = v) }
    fun save() = change(persist = true) { it }

    private fun change(persist: Boolean, f: (LiveSettings) -> LiveSettings) {
        val next = f(_state.value.settings)
        _state.update { it.copy(settings = next, saved = false) }
        if (persist) viewModelScope.launch { repo.save(next); _state.update { it.copy(saved = true) } }
    }
}
