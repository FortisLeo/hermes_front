package com.rishitgoklani.hermes_front_mobile.data.live

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rishitgoklani.hermes_front_mobile.domain.live.LiveSettings
import com.rishitgoklani.hermes_front_mobile.domain.live.LiveSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.liveStore by preferencesDataStore("live_settings")

/** Gemini backend settings. The API key is stored here and nowhere else: not in source, logs, or the bridge. */
class LiveSettingsStore(private val context: Context) : LiveSettingsRepository {
    private val enabled = booleanPreferencesKey("enabled")
    private val key = stringPreferencesKey("api_key")
    private val model = stringPreferencesKey("model")
    private val voice = stringPreferencesKey("voice")
    private val half = booleanPreferencesKey("half_duplex")
    private val idle = intPreferencesKey("idle_seconds")

    override val settings: Flow<LiveSettings> = context.liveStore.data.map {
        val d = LiveSettings()
        LiveSettings(it[enabled] ?: d.enabled, it[key] ?: d.apiKey, it[model]?.ifBlank { null } ?: d.model, it[voice]?.ifBlank { null } ?: d.voice, it[half] ?: d.halfDuplex, (it[idle] ?: d.idleSeconds).coerceIn(5, 300))
    }

    override suspend fun save(settings: LiveSettings) {
        context.liveStore.edit {
            it[enabled] = settings.enabled; it[key] = settings.apiKey.trim(); it[model] = settings.model.trim(); it[voice] = settings.voice.trim()
            it[half] = settings.halfDuplex; it[idle] = settings.idleSeconds.coerceIn(5, 300)
        }
    }
}
