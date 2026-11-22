package com.rishitgoklani.hermes_front_mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rishitgoklani.hermes_front_mobile.domain.HermesConfig
import com.rishitgoklani.hermes_front_mobile.domain.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("settings")

class SettingsStore(private val context: Context) : SettingsRepository {
    private val url = stringPreferencesKey("base_url")
    private val key = stringPreferencesKey("api_key")
    private val bridge = stringPreferencesKey("bridge_token")
    override val bridgeToken: Flow<String> = context.store.data.map { it[bridge] ?: "" }
    override suspend fun saveBridgeToken(token: String) { context.store.edit { it[bridge] = token.trim() } }

    override val config: Flow<HermesConfig> = context.store.data.map {
        val d = HermesConfig()
        d.copy(baseUrl = it[url] ?: d.baseUrl, apiKey = it[key] ?: d.apiKey)
    }

    override suspend fun save(baseUrl: String, apiKey: String) {
        context.store.edit { it[url] = baseUrl.trim(); it[key] = apiKey.trim() }
    }
}
