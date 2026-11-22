package com.rishitgoklani.hermes_front_mobile.data.persona

import com.rishitgoklani.hermes_front_mobile.domain.behavior.DefaultQuickActions
import com.rishitgoklani.hermes_front_mobile.domain.behavior.QuickAction
import com.rishitgoklani.hermes_front_mobile.domain.persona.Persona
import com.rishitgoklani.hermes_front_mobile.domain.persona.PersonaLicense
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileStoresTest {
    @get:Rule val tmp = TemporaryFolder()
    private val mine = Persona("mine", "Mine", eyeColorHex = "#112233", license = PersonaLicense("me", "me", "MIT", "personal"))

    @Test fun defaultsToBuiltinDefault() = runBlocking { val r = FilePersonaRepository(File(tmp.root, "p")); assertEquals("default", r.active().id); assertTrue(r.all().any { it.id == "night-owl" }) }
    @Test fun saveThenListAndPersistAcrossInstances() = runBlocking {
        val dir = File(tmp.root, "p"); FilePersonaRepository(dir).save(mine)
        val again = FilePersonaRepository(dir); assertTrue(again.all().any { it.id == "mine" && !it.builtin })
    }
    @Test fun activeAndPrevious() = runBlocking {
        val r = FilePersonaRepository(File(tmp.root, "p")); r.save(mine); r.setActive("default"); r.setActive("mine"); assertEquals("mine", r.active().id); assertEquals("default", r.previousId())
    }
    @Test fun unknownActiveFallsBackToDefault() = runBlocking { val r = FilePersonaRepository(File(tmp.root, "p")); r.setActive("ghost"); assertEquals("default", r.active().id) }
    @Test fun deleteUserButNotBuiltinAndResetsActive() = runBlocking {
        val r = FilePersonaRepository(File(tmp.root, "p")); r.save(mine); r.setActive("mine")
        assertFalse(r.delete("default")); assertTrue(r.delete("mine")); assertEquals("default", r.active().id); assertFalse(r.delete("mine"))
    }
    @Test fun cannotOverwriteBuiltinOrSaveInvalid() = runBlocking {
        val r = FilePersonaRepository(File(tmp.root, "p"))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { r.save(mine.copy(id = "default")) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { r.save(mine.copy(license = PersonaLicense())) } }
        Unit
    }
    @Test fun corruptOrUnlicensedFilesIgnored() = runBlocking {
        val dir = File(tmp.root, "p").also { it.mkdirs() }; File(dir, "bad.json").writeText("{nope"); File(dir, "rip.json").writeText("""{"id":"rip","name":"R","license":{"license":"MIT","allowedUse":"x","ripped":true}}""")
        assertEquals(4, FilePersonaRepository(dir).all().size)
    }
    @Test fun quickActionsDefaultSetAndCorruptFallback() = runBlocking {
        val f = File(tmp.root, "qa/actions.json"); val r = FileQuickActionsRepository(f)
        assertEquals(DefaultQuickActions.all, r.all()); r.set(listOf(QuickAction("a", "A", "battery"))); assertEquals(listOf("a"), FileQuickActionsRepository(f).all().map { it.id })
        f.writeText("garbage"); assertEquals(DefaultQuickActions.all, r.all())
    }
}
