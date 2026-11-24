package com.rishitgoklani.hermes_front_mobile.domain

import kotlinx.coroutines.flow.Flow

/** Port: persisted Hermes connection settings and small app preferences. */
interface SettingsRepository {
    val config: Flow<HermesConfig>
    suspend fun save(baseUrl: String, apiKey: String)
    val bridgeToken: Flow<String>
    suspend fun saveBridgeToken(token: String)
    /** True once the first-run introduction was finished or skipped. */
    val onboardingDone: Flow<Boolean>
    suspend fun setOnboardingDone(done: Boolean)
    /** Read later by the avatar renderer. */
    val displayQuality: Flow<DisplayQuality>
    suspend fun setDisplayQuality(quality: DisplayQuality)
}
