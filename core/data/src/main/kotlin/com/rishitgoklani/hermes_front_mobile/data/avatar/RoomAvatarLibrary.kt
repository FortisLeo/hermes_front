package com.rishitgoklani.hermes_front_mobile.data.avatar

import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarEntry
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarLibrary
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [AvatarLibrary] over Room. The library list is a Flow, so the UI updates itself when a row changes. */
class RoomAvatarLibrary(private val dao: AvatarDao) : AvatarLibrary {
    override fun entries(): Flow<List<AvatarEntry>> = dao.observe().map { rows -> rows.map { it.toEntry() } }
    override suspend fun get(id: String) = dao.get(id)?.toEntry()
    override suspend fun all() = dao.all().map { it.toEntry() }
    override suspend fun add(entry: AvatarEntry) = dao.upsert(entry.toRow())
    override suspend fun remove(id: String) = dao.delete(id)
    override suspend fun rename(id: String, name: String) = dao.rename(id, name.trim().take(60).ifEmpty { return })
    override suspend fun setStatus(id: String, status: AvatarStatus) = dao.setStatus(id, status.name)
    override suspend fun touch(id: String, at: Long) = dao.touch(id, at)
    override suspend fun setThumb(id: String, path: String) = dao.setThumb(id, path)
}
