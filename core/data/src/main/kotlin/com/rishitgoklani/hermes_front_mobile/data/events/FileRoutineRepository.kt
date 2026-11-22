package com.rishitgoklani.hermes_front_mobile.data.events

import com.rishitgoklani.hermes_front_mobile.domain.routines.Routine
import com.rishitgoklani.hermes_front_mobile.domain.routines.RoutineRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

class FileRoutineRepository(private val file: File) : RoutineRepository {
    private val lock = Mutex()
    private val ser = ListSerializer(Routine.serializer())
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    private fun read(): List<Routine> = runCatching { json.decodeFromString(ser, file.readText()) }.getOrDefault(emptyList())
    private fun write(list: List<Routine>) { file.parentFile?.mkdirs(); file.writeText(json.encodeToString(ser, list)) }

    override suspend fun all(): List<Routine> = lock.withLock { read() }
    override suspend fun save(routine: Routine) { lock.withLock { write(read().filterNot { it.id == routine.id } + routine) } }
    override suspend fun delete(id: String): Boolean = lock.withLock { val l = read(); val n = l.filterNot { it.id == id }; if (n.size == l.size) false else { write(n); true } }
}
