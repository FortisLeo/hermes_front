package com.rishitgoklani.hermes_front_mobile.ui.avatar

import com.rishitgoklani.hermes_front_mobile.domain.avatar.AvatarCatalogSource
import com.rishitgoklani.hermes_front_mobile.domain.avatar.AvatarInstaller
import com.rishitgoklani.hermes_front_mobile.domain.avatar.CatalogAvatar
import com.rishitgoklani.hermes_front_mobile.domain.avatar.CatalogCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AvatarLibraryViewModelTest {
    @Before fun up() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun down() = Dispatchers.resetMain()

    private fun glb(json: String): ByteArray {
        var j = json.toByteArray(); while (j.size % 4 != 0) j += ' '.code.toByte()
        fun le(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
        return "glTF".toByteArray() + le(2) + le(20 + j.size) + le(j.size) + "JSON".toByteArray() + j
    }
    private fun model(lic: String) = glb("""{"extensions":{"VRM":{"meta":{"title":"Devil","author":"A","licenseName":"$lic"}}}}""")

    private class Src(var bytes: ByteArray, var offline: Boolean = false) : AvatarCatalogSource {
        val a = CatalogCollection("a", "A", "CC0", "a.json"); val b = CatalogCollection("b", "B", "CC0", "b.json")
        var thumbCalls = 0
        override suspend fun collections() = if (offline) error("offline") else listOf(a, b)
        override suspend fun avatars(collection: CatalogCollection) = if (collection.id == "a") listOf(CatalogAvatar("1", "Devil", "a", "CC0", "https://x/1", "https://x/t"), CatalogAvatar("2", "Angel", "a", "CC0", "https://x/2", "")) else emptyList()
        override suspend fun download(url: String) = bytes
        override suspend fun thumbnail(url: String): ByteArray? { thumbCalls++; return byteArrayOf(1) }
    }
    private fun vm(src: Src, installed: MutableList<Int> = mutableListOf()) = AvatarLibraryViewModel(src, AvatarInstaller { installed += it.size })

    @Test fun loadsCollectionsAndFirstCollectionAvatars() = runTest {
        val v = vm(Src(model("CC0"))); v.load(); advanceUntilIdle()
        val s = v.state.value; assertEquals(2, s.collections.size); assertEquals("a", s.collection?.id); assertEquals(2, s.avatars.size); assertTrue(!s.loading)
    }
    @Test fun offlineShowsMessageAndRetryWorks() = runTest {
        val src = Src(model("CC0"), offline = true); val v = vm(src); v.load(); advanceUntilIdle()
        assertTrue(v.state.value.message!!.startsWith("Catalog unavailable")); src.offline = false; v.load(); advanceUntilIdle(); assertEquals(2, v.state.value.avatars.size)
    }
    @Test fun queryFiltersAndSelectSwitchesCollection() = runTest {
        val v = vm(Src(model("CC0"))); v.load(); advanceUntilIdle()
        v.onQuery("ang"); assertEquals(listOf("Angel"), v.state.value.shown.map { it.name })
        v.select(v.state.value.collections[1]); advanceUntilIdle(); assertTrue(v.state.value.avatars.isEmpty())
    }
    @Test fun installingAcceptedModelReportsAndInstalls() = runTest {
        val got = mutableListOf<Int>(); val v = vm(Src(model("CC0")), got); v.load(); advanceUntilIdle()
        v.install(v.state.value.avatars[0]); advanceUntilIdle()
        assertEquals(1, got.size); assertEquals("Using 'Devil' (CC0)", v.state.value.message); assertNull(v.state.value.installingId)
        v.consumeMessage(); assertNull(v.state.value.message)
    }
    @Test fun rejectedLicenseIsNotInstalled() = runTest {
        val got = mutableListOf<Int>(); val v = vm(Src(model("CC_BY_NC")), got); v.load(); advanceUntilIdle()
        v.install(v.state.value.avatars[0]); advanceUntilIdle(); assertTrue(got.isEmpty()); assertTrue(v.state.value.message!!.startsWith("Model rejected"))
    }
    @Test fun importFileHandlesUnreadableAndGoodFiles() = runTest {
        val got = mutableListOf<Int>(); val v = vm(Src(model("CC0")), got)
        v.importFile("x.vrm", null); assertEquals("Could not read x.vrm", v.state.value.message)
        v.importFile("x.vrm", model("CC0")); advanceUntilIdle(); assertEquals(1, got.size); assertTrue(v.state.value.message!!.startsWith("Using"))
        v.importFile("y.vrm", byteArrayOf(1, 2)); advanceUntilIdle(); assertEquals(1, got.size); assertTrue(v.state.value.message!!.startsWith("Model rejected"))
    }
    @Test fun thumbnailsAreCached() = runTest {
        val src = Src(model("CC0")); val v = vm(src); v.thumbnail("https://x/t"); v.thumbnail("https://x/t"); assertEquals(1, src.thumbCalls); assertNull(v.thumbnail(""))
    }
}
