package com.rishitgoklani.hermes_front_mobile.testing

import com.rishitgoklani.hermes_front_mobile.domain.routines.Routine
import com.rishitgoklani.hermes_front_mobile.domain.routines.RoutineRepository

class FakeRoutineRepository(var items: MutableList<Routine> = mutableListOf()) : RoutineRepository {
    override suspend fun all() = items.toList()
    override suspend fun save(routine: Routine) { items.removeAll { it.id == routine.id }; items += routine }
    override suspend fun delete(id: String) = items.removeAll { it.id == id }
}
