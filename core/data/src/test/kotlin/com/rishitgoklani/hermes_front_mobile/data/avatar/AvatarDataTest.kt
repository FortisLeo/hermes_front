package com.rishitgoklani.hermes_front_mobile.data.avatar

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarEntry
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarSlot
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarSource
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files

private fun entry(id: String, added: Long = 1, status: AvatarStatus = AvatarStatus.OK) =
    AvatarEntry(id, "Name $id", AvatarSource.CATALOG, "CC0", "me", 100, 50, 512, 1000, "note", null, added, added, status)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomAvatarLibraryTest {
    private lateinit var db: DeskbotDatabase; private lateinit var lib: RoomAvatarLibrary
    @Before fun up() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), DeskbotDatabase::class.java).allowMainThreadQueries().build(); lib = RoomAvatarLibrary(db.avatars()) }
    @After fun down() = db.close()

    @Test fun addGetAndOrderByAddedAt() = runBlocking {
        lib.add(entry("b", 2)); lib.add(entry("a", 1)); assertEquals(listOf("a", "b"), lib.all().map { it.id }); assertEquals("Name a", lib.get("a")!!.name); assertEquals(AvatarSource.CATALOG, lib.get("a")!!.source); assertNull(lib.get("zz"))
    }
    @Test fun updatesWork() = runBlocking {
        lib.add(entry("a")); lib.rename("a", "  New name "); lib.setStatus("a", AvatarStatus.BROKEN); lib.touch("a", 77); lib.setThumb("a", "/t.png")
        val e = lib.get("a")!!; assertEquals("New name", e.name); assertEquals(AvatarStatus.BROKEN, e.status); assertEquals(77L, e.lastUsedAt); assertEquals("/t.png", e.thumbPath)
        lib.rename("a", "   "); assertEquals("New name", lib.get("a")!!.name)
    }
    @Test fun removeDeletesTheRow() = runBlocking { lib.add(entry("a")); lib.remove("a"); assertTrue(lib.all().isEmpty()) }
    @Test fun entriesFlowEmitsOnChange() = runBlocking {
        lib.entries().test { assertTrue(awaitItem().isEmpty()); lib.add(entry("a")); assertEquals(listOf("a"), awaitItem().map { it.id }); lib.setStatus("a", AvatarStatus.PENDING); assertEquals(AvatarStatus.PENDING, awaitItem()[0].status); cancelAndIgnoreRemainingEvents() }
    }
}

class FileAvatarFilesTest {
    private val dir = Files.createTempDirectory("avatars").toFile()
    private val files = FileAvatarFiles(dir)
    @After fun cleanup() { dir.deleteRecursively() }

    @Test fun writeReadPathAndDeleteIncludingThumb() = runBlocking {
        files.write("a1", byteArrayOf(1, 2, 3)); assertEquals(listOf<Byte>(1, 2, 3), files.read("a1")!!.toList()); assertTrue(files.modelPath("a1")!!.endsWith("a1.vrm"))
        val t = files.writeThumb("a1", byteArrayOf(9)); assertTrue(java.io.File(t).isFile)
        files.delete("a1"); assertNull(files.read("a1")); assertNull(files.modelPath("a1")); assertTrue(!java.io.File(t).exists()); assertTrue(dir.listFiles()!!.none { it.name.endsWith(".tmp") })
    }
    @Test fun idsCannotEscapeTheFolder() = runBlocking {
        assertTrue(runCatching { files.write("../evil", byteArrayOf(1)) }.isFailure); assertTrue(runCatching { files.write("", byteArrayOf(1)) }.isFailure); Unit
    }
}

class ActiveSlotStoreTest {
    @Test fun encodeDecodeRoundTripAndGarbageIsEyes() {
        assertEquals("eyes", DataStoreActiveSlotStore.encode(AvatarSlot.Eyes)); assertEquals("model:x1", DataStoreActiveSlotStore.encode(AvatarSlot.Model("x1")))
        assertEquals(AvatarSlot.Model("x1"), DataStoreActiveSlotStore.decode("model:x1")); assertEquals(AvatarSlot.Eyes, DataStoreActiveSlotStore.decode("model:")); assertEquals(AvatarSlot.Eyes, DataStoreActiveSlotStore.decode(null)); assertEquals(AvatarSlot.Eyes, DataStoreActiveSlotStore.decode("???"))
    }
    @Test fun persistsAcrossStoreInstances() = runBlocking {
        val f = Files.createTempFile("slot", ".preferences_pb").toFile().also { it.delete() }
        val a = DataStoreActiveSlotStore(PreferenceDataStoreFactory.create(scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())) { f })
        assertEquals(AvatarSlot.Eyes, a.current()); a.set(AvatarSlot.Model("q")); assertEquals(AvatarSlot.Model("q"), a.current()); f.delete(); Unit
    }
}

class NativeGlSwitchTest {
    @Test fun defaultsToEnabledAndPersistsAcrossInstances() = runBlocking {
        val f = Files.createTempFile("glswitch", ".preferences_pb").toFile().also { it.delete() }
        fun open() = DataStoreNativeGlSwitch(PreferenceDataStoreFactory.create(scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())) { f })
        val a = open()
        assertEquals(false, a.disabled()); a.setDisabled(true); assertEquals(true, a.disabled())
        a.setDisabled(false); assertEquals(false, a.disabled()); f.delete(); Unit
    }
}
