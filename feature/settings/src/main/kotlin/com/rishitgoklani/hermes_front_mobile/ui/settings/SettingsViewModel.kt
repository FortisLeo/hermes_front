package com.rishitgoklani.hermes_front_mobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rishitgoklani.hermes_front_mobile.domain.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SettingsUiState(val baseUrl: String = "", val apiKey: String = "", val saved: Boolean = false)

class SettingsViewModel(private val repo: SettingsRepository) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state

    init {
        viewModelScope.launch {
            val c = repo.config.first()
            _state.value = SettingsUiState(c.baseUrl, c.apiKey)
        }
    }

    fun onBaseUrl(v: String) { _state.value = _state.value.copy(baseUrl = v, saved = false) }
    fun onApiKey(v: String) { _state.value = _state.value.copy(apiKey = v, saved = false) }

    fun save() {
        val s = _state.value
        viewModelScope.launch {
            repo.save(s.baseUrl, s.apiKey)
            _state.value = _state.value.copy(saved = true)
        }
    }
}
