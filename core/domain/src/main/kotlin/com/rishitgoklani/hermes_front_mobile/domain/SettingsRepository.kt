package com.rishitgoklani.hermes_front_mobile.domain

import kotlinx.coroutines.flow.Flow

/** Port: persisted Hermes connection settings. */
interface SettingsRepository {
    val config: Flow<HermesConfig>
    suspend fun save(baseUrl: String, apiKey: String)
    val bridgeToken: Flow<String>
    suspend fun saveBridgeToken(token: String)
}
