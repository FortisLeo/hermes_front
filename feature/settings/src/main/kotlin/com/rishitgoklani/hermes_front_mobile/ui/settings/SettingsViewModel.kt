package com.rishitgoklani.hermes_front_mobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rishitgoklani.hermes_front_mobile.domain.DisplayQuality
import com.rishitgoklani.hermes_front_mobile.domain.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Read-only facts for the About block. Filled in by the app (BuildConfig lives there). */
data class AboutInfo(val versionName: String = "", val buildType: String = "", val docs: String = "docs/SETUP.md in the project repository")

data class SettingsUiState(
    val baseUrl: String = "",
    val apiKey: String = "",
    val saved: Boolean = false,
    val displayQuality: DisplayQuality = DisplayQuality.Auto,
    val about: AboutInfo = AboutInfo(),
)

class SettingsViewModel(private val repo: SettingsRepository, about: AboutInfo = AboutInfo()) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState(about = about))
    val state: StateFlow<SettingsUiState> = _state

    init {
        viewModelScope.launch {
            val c = repo.config.first()
            _state.update { it.copy(baseUrl = c.baseUrl, apiKey = c.apiKey) }
        }
        viewModelScope.launch { repo.displayQuality.collect { q -> _state.update { it.copy(displayQuality = q) } } }
    }

    fun onBaseUrl(v: String) { _state.update { it.copy(baseUrl = v, saved = false) } }
    fun onApiKey(v: String) { _state.update { it.copy(apiKey = v, saved = false) } }

    fun onDisplayQuality(q: DisplayQuality) {
        _state.update { it.copy(displayQuality = q) }
        viewModelScope.launch { repo.setDisplayQuality(q) }
    }

    fun save() {
        val s = _state.value
        viewModelScope.launch {
            repo.save(s.baseUrl, s.apiKey)
            _state.update { it.copy(saved = true) }
        }
    }
}
