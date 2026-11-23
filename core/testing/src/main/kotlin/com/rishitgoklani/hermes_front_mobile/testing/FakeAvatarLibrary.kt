package com.rishitgoklani.hermes_front_mobile.testing

import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.ActiveSlotStore
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarEntry
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarFiles
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarLibrary
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarSlot
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeAvatarLibrary(initial: List<AvatarEntry> = emptyList()) : AvatarLibrary {
    private val rows = MutableStateFlow(initial.associateBy { it.id })
    override fun entries(): Flow<List<AvatarEntry>> = rows.map { it.values.toList() }
    override suspend fun get(id: String) = rows.value[id]
    override suspend fun all() = rows.value.values.toList()
    override suspend fun add(entry: AvatarEntry) { rows.value = rows.value + (entry.id to entry) }
    override suspend fun remove(id: String) { rows.value = rows.value - id }
    override suspend fun rename(id: String, name: String) = update(id) { it.copy(name = name) }
    override suspend fun setStatus(id: String, status: AvatarStatus) = update(id) { it.copy(status = status) }
    override suspend fun touch(id: String, at: Long) = update(id) { it.copy(lastUsedAt = at) }
    override suspend fun setThumb(id: String, path: String) = update(id) { it.copy(thumbPath = path) }
    private fun update(id: String, f: (AvatarEntry) -> AvatarEntry) { rows.value[id]?.let { rows.value = rows.value + (id to f(it)) } }
}

class FakeActiveSlotStore(initial: AvatarSlot = AvatarSlot.Eyes) : ActiveSlotStore {
    private val state = MutableStateFlow(initial)
    override fun slot(): Flow<AvatarSlot> = state
    override suspend fun current() = state.value
    override suspend fun set(slot: AvatarSlot) { state.value = slot }
}

class FakeAvatarFiles : AvatarFiles {
    val models = LinkedHashMap<String, ByteArray>(); val thumbs = LinkedHashMap<String, ByteArray>()
    override suspend fun write(id: String, bytes: ByteArray) { models[id] = bytes }
    override suspend fun read(id: String) = models[id]
    override fun modelPath(id: String) = if (id in models) "/fake/$id.vrm" else null
    override suspend fun writeThumb(id: String, png: ByteArray): String { thumbs[id] = png; return "/fake/thumbs/$id.png" }
    override suspend fun delete(id: String) { models.remove(id); thumbs.remove(id) }
}
