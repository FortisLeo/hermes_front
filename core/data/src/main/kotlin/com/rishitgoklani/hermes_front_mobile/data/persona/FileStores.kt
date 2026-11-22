package com.rishitgoklani.hermes_front_mobile.data.persona

import com.rishitgoklani.hermes_front_mobile.domain.behavior.DefaultQuickActions
import com.rishitgoklani.hermes_front_mobile.domain.behavior.QuickAction
import com.rishitgoklani.hermes_front_mobile.domain.behavior.QuickActionsRepository
import com.rishitgoklani.hermes_front_mobile.domain.persona.BuiltinPersonas
import com.rishitgoklani.hermes_front_mobile.domain.persona.Persona
import com.rishitgoklani.hermes_front_mobile.domain.persona.PersonaRepository
import com.rishitgoklani.hermes_front_mobile.domain.persona.PersonaValidator
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

/** One JSON file per user pack in [dir]; active/previous ids in small text files. Invalid files are ignored, never crash. */
class FilePersonaRepository(private val dir: File) : PersonaRepository {
    private val lock = Mutex()
    private val activeFile get() = File(dir, "active.txt")
    private val prevFile get() = File(dir, "previous.txt")

    private fun user(): List<Persona> = dir.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }.mapNotNull { f ->
        runCatching { json.decodeFromString(Persona.serializer(), f.readText()).copy(builtin = false) }.getOrNull()?.takeIf { PersonaValidator.check(it) == null }
    }

    private fun allLocked(): List<Persona> = BuiltinPersonas.all + user().filter { u -> BuiltinPersonas.all.none { it.id == u.id } }

    override suspend fun all(): List<Persona> = lock.withLock { allLocked() }

    override suspend fun active(): Persona = lock.withLock {
        val id = runCatching { activeFile.readText().trim() }.getOrDefault("default")
        allLocked().firstOrNull { it.id == id } ?: BuiltinPersonas.all.first()
    }

    override suspend fun setActive(id: String) {
        lock.withLock {
            dir.mkdirs()
            val cur = runCatching { activeFile.readText().trim() }.getOrNull()
            if (cur != null && cur != id) prevFile.writeText(cur)
            activeFile.writeText(id)
        }
    }

    override suspend fun previousId(): String? = lock.withLock { runCatching { prevFile.readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() } }

    override suspend fun save(persona: Persona) {
        require(PersonaValidator.check(persona) == null) { "invalid persona" }
        require(BuiltinPersonas.all.none { it.id == persona.id }) { "cannot overwrite built-in persona" }
        lock.withLock { dir.mkdirs(); File(dir, "${persona.id}.json").writeText(json.encodeToString(Persona.serializer(), persona.copy(builtin = false))) }
    }

    override suspend fun delete(id: String): Boolean = lock.withLock {
        if (BuiltinPersonas.all.any { it.id == id }) return@withLock false
        val f = File(dir, "$id.json")
        if (!f.exists()) return@withLock false
        val ok = f.delete()
        if (ok && runCatching { activeFile.readText().trim() }.getOrNull() == id) activeFile.writeText("default")
        ok
    }
}

class FileQuickActionsRepository(private val file: File) : QuickActionsRepository {
    private val lock = Mutex()
    private val ser = ListSerializer(QuickAction.serializer())

    override suspend fun all(): List<QuickAction> = lock.withLock {
        runCatching { json.decodeFromString(ser, file.readText()) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: DefaultQuickActions.all
    }

    override suspend fun set(actions: List<QuickAction>) {
        lock.withLock { file.parentFile?.mkdirs(); file.writeText(json.encodeToString(ser, actions)) }
    }
}
