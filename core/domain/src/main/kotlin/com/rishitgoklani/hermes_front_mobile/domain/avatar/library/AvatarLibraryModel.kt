package com.rishitgoklani.hermes_front_mobile.domain.avatar.library

import kotlinx.coroutines.flow.Flow

enum class AvatarSource { UPLOAD, CATALOG, AGENT }

/** OK = proven to render; PENDING = installed but the viewer has not proven it yet (a crash marks it BROKEN); BROKEN = killed the app, kept so the user can remove it. */
enum class AvatarStatus { OK, PENDING, BROKEN }

/** One installed avatar: the optimized file on disk plus what we learned while optimizing it. */
data class AvatarEntry(
    val id: String,
    val name: String,
    val source: AvatarSource,
    val license: String,
    val author: String,
    val originalBytes: Long,
    val storedBytes: Long,
    /** Longest texture side the viewer may decode; the stored file already has textures at most this big. */
    val textureCap: Int,
    /** Estimated GPU texture memory of the stored model. */
    val estTexBytes: Long,
    val note: String?,
    val thumbPath: String?,
    val addedAt: Long,
    val lastUsedAt: Long,
    val status: AvatarStatus,
)

/** What the face screen shows: the eyes, or one installed avatar. */
sealed interface AvatarSlot {
    data object Eyes : AvatarSlot
    data class Model(val id: String) : AvatarSlot
}

/** Persistence port for the installed avatars (Room implements it). */
interface AvatarLibrary {
    fun entries(): Flow<List<AvatarEntry>>
    suspend fun get(id: String): AvatarEntry?
    suspend fun all(): List<AvatarEntry>
    suspend fun add(entry: AvatarEntry)
    suspend fun remove(id: String)
    suspend fun rename(id: String, name: String)
    suspend fun setStatus(id: String, status: AvatarStatus)
    suspend fun touch(id: String, at: Long)
    suspend fun setThumb(id: String, path: String)
}

/** Which slot is showing; survives restarts. */
interface ActiveSlotStore {
    fun slot(): Flow<AvatarSlot>
    suspend fun current(): AvatarSlot
    suspend fun set(slot: AvatarSlot)
}

/** Where model files and thumbnails live. */
interface AvatarFiles {
    suspend fun write(id: String, bytes: ByteArray)
    suspend fun read(id: String): ByteArray?
    /** A path the viewer can be pointed at, or null when the file is missing. */
    fun modelPath(id: String): String?
    suspend fun writeThumb(id: String, png: ByteArray): String
    suspend fun delete(id: String)
}

/** Re-encodes one embedded image no larger than [maxSide] px on its long side. Returns null when it cannot (the image is then kept as is). */
fun interface ImageTranscoder { fun shrink(data: ByteArray, mime: String, maxSide: Int): ByteArray? }
