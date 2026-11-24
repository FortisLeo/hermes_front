package com.rishitgoklani.hermes_front_mobile.data.avatar

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.NativeGlSwitch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val android.content.Context.nativeGlStore by preferencesDataStore("native_gl")

/** Remembers across launches that the native renderer hit a fatal GPU error, so a crash-looping driver is not retried until the user asks. */
class DataStoreNativeGlSwitch(private val store: DataStore<Preferences>) : NativeGlSwitch {
    private val key = booleanPreferencesKey("native_gl_disabled")
    override suspend fun disabled(): Boolean = store.data.map { it[key] ?: false }.first()
    override suspend fun setDisabled(disabled: Boolean) { store.edit { it[key] = disabled } }

    companion object { fun create(context: android.content.Context) = DataStoreNativeGlSwitch(context.applicationContext.nativeGlStore) }
}
