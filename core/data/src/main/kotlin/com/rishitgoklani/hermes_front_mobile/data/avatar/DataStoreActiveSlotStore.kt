package com.rishitgoklani.hermes_front_mobile.data.avatar

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.ActiveSlotStore
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Active slot in DataStore: "eyes" or "model:<id>". */
class DataStoreActiveSlotStore(private val store: DataStore<Preferences>) : ActiveSlotStore {
    private val key = stringPreferencesKey("avatar_active_slot")
    override fun slot(): Flow<AvatarSlot> = store.data.map { decode(it[key]) }
    override suspend fun current(): AvatarSlot = slot().first()
    override suspend fun set(slot: AvatarSlot) { store.edit { it[key] = encode(slot) } }

    companion object {
        fun encode(s: AvatarSlot) = when (s) { AvatarSlot.Eyes -> "eyes"; is AvatarSlot.Model -> "model:${s.id}" }
        fun decode(v: String?): AvatarSlot = if (v != null && v.startsWith("model:") && v.length > 6) AvatarSlot.Model(v.removePrefix("model:")) else AvatarSlot.Eyes
    }
}
