package com.rishitgoklani.hermes_front_mobile.domain.avatar.library

import com.rishitgoklani.hermes_front_mobile.domain.avatar.InstallResult
import com.rishitgoklani.hermes_front_mobile.testing.FakeActiveSlotStore
import com.rishitgoklani.hermes_front_mobile.testing.FakeAvatarFiles
import com.rishitgoklani.hermes_front_mobile.testing.FakeAvatarLibrary
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryInstallerTest {
    private val lib = FakeAvatarLibrary(); private val files = FakeAvatarFiles(); private val active = FakeActiveSlotStore()
    private var ids = 0
    private val import = ImportAvatar(lib, files, GlbOptimizer { d, _, _ -> d }, { "id${++ids}" }, { 1L })
    private val select = SelectAvatar(lib, active) { 2L }
    private val installer = LibraryInstaller(import, select)
    private fun glb(license: String = "CC0") = com.rishitgoklani.hermes_front_mobile.domain.avatar.TestGlb.build(listOf(com.rishitgoklani.hermes_front_mobile.domain.avatar.TestGlb.png(64, 64))).let { String(it, Charsets.ISO_8859_1).replace("CC0", license).toByteArray(Charsets.ISO_8859_1) }

    @Test fun installAddsToTheLibraryAndSelectsIt() = runBlocking {
        val r = installer.install(glb(), enforceLicense = true, name = "Devil", source = AvatarSource.CATALOG)
        assertTrue(r is InstallResult.Installed); assertEquals(AvatarSlot.Model("id1"), active.current()); assertEquals("Devil", lib.get("id1")!!.name); assertEquals(AvatarSource.CATALOG, lib.get("id1")!!.source)
    }
    @Test fun rejectedModelsChangeNothing() = runBlocking {
        assertTrue(installer.install(byteArrayOf(1, 2, 3)) is InstallResult.Rejected); assertTrue(lib.all().isEmpty()); assertEquals(AvatarSlot.Eyes, active.current())
    }
    @Test fun migrationImportsTheLegacyModelOnceAndSelectsItWhenTheAvatarWasOn() = runBlocking {
        val m = MigrateLegacyAvatar(lib, import, MarkProven(lib), select)
        assertNull(m(null, true)); val e = m(glb(), true)!!
        assertEquals(AvatarStatus.OK, lib.get(e.id)!!.status); assertEquals(AvatarSlot.Model(e.id), active.current()); assertNull(m(glb(), true)); assertEquals(1, lib.all().size)
    }
    @Test fun migrationKeepsEyesWhenTheAvatarWasOff() = runBlocking { MigrateLegacyAvatar(lib, import, MarkProven(lib), select)(glb(), false); assertEquals(AvatarSlot.Eyes, active.current()); assertEquals(1, lib.all().size) }

    @Test fun libraryToolListsSelectsCyclesAndRemoves() = runBlocking {
        installer.install(glb(), name = "A"); installer.install(glb(), name = "B")
        val tool = avatarLibraryCapability(lib, active, select, CycleAvatar(lib, active, select), RemoveAvatar(lib, files, active))
        fun call(vararg kv: Pair<String, String>) = runBlocking { tool.executor.execute(buildJsonObject { kv.forEach { put(it.first, JsonPrimitive(it.second)) } }) }
        val list = call("action" to "list").message; assertTrue(list.contains("id1 | A") && list.contains("id2 | B | ok | showing") || list.contains("pending"))
        assertEquals("completed", call("action" to "select", "id" to "id1").status); assertEquals(AvatarSlot.Model("id1"), active.current())
        call("action" to "next"); assertEquals(AvatarSlot.Model("id2"), active.current()); call("action" to "eyes"); assertEquals(AvatarSlot.Eyes, active.current())
        assertEquals("failed", call("action" to "select", "id" to "zz").status); assertEquals("completed", call("action" to "remove", "id" to "id1").status); assertEquals(1, lib.all().size); assertEquals("failed", call("action" to "bogus").status)
    }
}
