package com.rishitgoklani.hermes_front_mobile.data.events

import com.rishitgoklani.hermes_front_mobile.domain.safety.LaunchStore
import com.rishitgoklani.hermes_front_mobile.domain.sense.Place
import com.rishitgoklani.hermes_front_mobile.domain.sense.PlaceRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

class FilePlaceRepository(private val file: File) : PlaceRepository {
    private val lock = Mutex(); private val ser = ListSerializer(Place.serializer()); private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private fun read(): List<Place> = runCatching { json.decodeFromString(ser, file.readText()) }.getOrDefault(emptyList())
    private fun write(l: List<Place>) { file.parentFile?.mkdirs(); file.writeText(json.encodeToString(ser, l)) }
    override suspend fun all(): List<Place> = lock.withLock { read() }
    override suspend fun save(place: Place) { lock.withLock { write(read().filterNot { it.id == place.id } + place) } }
    override suspend fun delete(id: String): Boolean = lock.withLock { val l = read(); val n = l.filterNot { it.id == id }; if (n.size == l.size) false else { write(n); true } }
}

/** Launch timestamps in a plain text file (one epoch-ms per line); survives crashes, unlike in-memory state. */
class FileLaunchStore(private val file: File) : LaunchStore {
    override fun load(): List<Long> = runCatching { file.readLines().mapNotNull { it.trim().toLongOrNull() } }.getOrDefault(emptyList())
    override fun save(times: List<Long>) { runCatching { file.parentFile?.mkdirs(); file.writeText(times.joinToString("\n")) } }
}
