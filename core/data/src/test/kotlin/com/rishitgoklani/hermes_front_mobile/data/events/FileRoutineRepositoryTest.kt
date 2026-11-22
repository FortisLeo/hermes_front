package com.rishitgoklani.hermes_front_mobile.data.events

import com.rishitgoklani.hermes_front_mobile.domain.routines.Routine
import com.rishitgoklani.hermes_front_mobile.domain.routines.ToolCall
import com.rishitgoklani.hermes_front_mobile.domain.routines.Trigger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileRoutineRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private val r = Routine("night", "Night", Trigger("charger_connected"), listOf(ToolCall("battery")))

    @Test fun saveListPersistAndReplace() = runBlocking {
        val f = File(tmp.root, "x/routines.json"); val repo = FileRoutineRepository(f); repo.save(r); repo.save(r.copy(name = "Night2"))
        assertEquals(listOf("Night2"), FileRoutineRepository(f).all().map { it.name })
    }
    @Test fun deleteAndCorruptFile() = runBlocking {
        val f = File(tmp.root, "r.json"); val repo = FileRoutineRepository(f); repo.save(r)
        assertTrue(repo.delete("night")); assertFalse(repo.delete("night")); f.writeText("garbage"); assertEquals(emptyList<Routine>(), repo.all())
    }
}
