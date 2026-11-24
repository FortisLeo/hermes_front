package com.rishitgoklani.hermes_front_mobile.testing

import com.rishitgoklani.hermes_front_mobile.domain.DisplayQuality
import com.rishitgoklani.hermes_front_mobile.domain.HermesConfig
import com.rishitgoklani.hermes_front_mobile.domain.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsRepository(initial: HermesConfig = HermesConfig()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val config = state
    private val token = MutableStateFlow("")
    override val bridgeToken = token
    override suspend fun saveBridgeToken(token: String) { this.token.value = token.trim() }
    override suspend fun save(baseUrl: String, apiKey: String) {
        state.value = state.value.copy(baseUrl = baseUrl.trim(), apiKey = apiKey.trim())
    }
    private val done = MutableStateFlow(false)
    override val onboardingDone = done
    override suspend fun setOnboardingDone(done: Boolean) { this.done.value = done }
    private val quality = MutableStateFlow(DisplayQuality.Auto)
    override val displayQuality = quality
    override suspend fun setDisplayQuality(quality: DisplayQuality) { this.quality.value = quality }
}
