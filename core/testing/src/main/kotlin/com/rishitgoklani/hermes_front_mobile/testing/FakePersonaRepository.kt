package com.rishitgoklani.hermes_front_mobile.testing

import com.rishitgoklani.hermes_front_mobile.domain.persona.BuiltinPersonas
import com.rishitgoklani.hermes_front_mobile.domain.persona.Persona
import com.rishitgoklani.hermes_front_mobile.domain.persona.PersonaRepository

class FakePersonaRepository : PersonaRepository {
    private val items = BuiltinPersonas.all.associateBy { it.id }.toMutableMap()
    private var activeId = "default"; private var prev: String? = null
    override suspend fun all() = items.values.toList()
    override suspend fun active() = items.getValue(activeId)
    override suspend fun setActive(id: String) { if (id != activeId) prev = activeId; activeId = id }
    override suspend fun previousId() = prev
    override suspend fun save(persona: Persona) { items[persona.id] = persona }
    override suspend fun delete(id: String): Boolean { val p = items[id] ?: return false; if (p.builtin) return false; items.remove(id); if (activeId == id) activeId = "default"; return true }
}
