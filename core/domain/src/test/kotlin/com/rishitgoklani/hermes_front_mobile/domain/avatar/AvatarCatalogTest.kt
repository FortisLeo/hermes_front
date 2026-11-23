package com.rishitgoklani.hermes_front_mobile.domain.avatar

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarCatalogTest {
    private val projects = """[
      {"id":"a","name":"A","license":"CC0","avatar_data_file":"avatars/a.json"},
      {"id":"b","name":"B","license":"CC-BY","avatar_data_file":"avatars/b.json"},
      {"id":"nc","name":"NC","license":"CC-BY-NC","avatar_data_file":"avatars/nc.json"},
      {"id":"nofile","name":"X","license":"CC0"}]"""
    private val col = CatalogCollection("a", "A", "CC0", "avatars/a.json")
    private val avatars = """[
      {"id":"1","name":"Devil","format":"VRM","model_file_url":"https://x/1","thumbnail_url":"https://x/t1","is_public":true,"is_draft":false},
      {"id":"2","name":"Angel","format":"VRM","model_file_url":"https://x/2"},
      {"id":"3","name":"Fbx","format":"FBX","model_file_url":"https://x/3"},
      {"id":"4","name":"Draft","format":"VRM","model_file_url":"https://x/4","is_draft":true},
      {"id":"5","name":"Insecure","format":"VRM","model_file_url":"http://x/5"}]"""

    private fun glb(json: String): ByteArray {
        var j = json.toByteArray(); while (j.size % 4 != 0) j += ' '.code.toByte()
        fun le(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
        return "glTF".toByteArray() + le(2) + le(20 + j.size) + le(j.size) + "JSON".toByteArray() + j
    }
    private fun model(lic: String) = glb("""{"extensions":{"VRM":{"meta":{"title":"T","author":"A","licenseName":"$lic"}}}}""")

    private class FakeSource(val bytes: ByteArray, val fail: Boolean = false) : AvatarCatalogSource {
        val col = CatalogCollection("a", "A", "CC0", "avatars/a.json")
        override suspend fun collections() = if (fail) error("offline") else listOf(col)
        override suspend fun avatars(collection: CatalogCollection) = listOf(CatalogAvatar("1", "Devil", "a", "CC0", "https://x/1", ""), CatalogAvatar("2", "Angel", "a", "CC0", "https://x/2", ""))
        override suspend fun download(url: String) = bytes
        override suspend fun thumbnail(url: String): ByteArray? = null
    }

    @Test fun listsOnlyPermissiveCollectionsWithData() { assertEquals(listOf("a", "b"), OsaCatalog.collections(projects).map { it.id }) }
    @Test fun listsOnlyPublicHttpsVrmEntries() { val l = OsaCatalog.avatars(avatars, col); assertEquals(listOf("1", "2"), l.map { it.id }); assertEquals("https://x/t1", l[0].thumbUrl); assertEquals("", l[1].thumbUrl) }
    @Test fun garbageYieldsEmptyLists() { assertTrue(OsaCatalog.collections("not json").isEmpty()); assertTrue(OsaCatalog.avatars("{}", col).isEmpty()) }
    @Test fun searchMatchesNameCaseInsensitively() { val l = OsaCatalog.avatars(avatars, col); assertEquals(listOf("Devil"), OsaCatalog.search(l, "dev").map { it.name }); assertEquals(2, OsaCatalog.search(l, " ").size) }
    @Test fun urlsPointAtTheRegistry() { assertEquals(OsaCatalog.BASE + "avatars/a.json", OsaCatalog.fileUrl(col)); assertTrue(OsaCatalog.projectsUrl().endsWith("/data/projects.json")) }

    @Test fun installerOnlyInstallsAcceptedModels() = runBlocking {
        var n = 0; val i = AvatarInstaller { n++ }
        assertTrue(i.install(model("CC0")) is InstallResult.Installed); assertTrue(i.install(model("Other")) is InstallResult.Rejected); assertTrue(i.install(byteArrayOf(1)) is InstallResult.Rejected); assertEquals(1, n)
    }
    @Test fun catalogToolListsCollectionsAndAvatars() = runBlocking {
        val c = avatarCatalogCapability(FakeSource(model("CC0")))
        assertTrue(c.executor.execute(buildJsonObject { }).message.contains("a: A (CC0)"))
        val l = c.executor.execute(buildJsonObject { put("action", "list"); put("collection", "a"); put("query", "ang") }); assertEquals("2 | Angel", l.message)
        assertEquals("failed", c.executor.execute(buildJsonObject { put("action", "list"); put("collection", "zz") }).status)
        assertEquals("failed", avatarCatalogCapability(FakeSource(model("CC0"), fail = true)).executor.execute(buildJsonObject { }).status)
    }
    @Test fun downloadToolInstallsByIdOrNameAndRejectsBadLicense() = runBlocking {
        var n = 0
        val ok = avatarDownloadCapability(FakeSource(model("CC0")), AvatarInstaller { n++ })
        assertEquals("completed", ok.executor.execute(buildJsonObject { put("collection", "a"); put("id", "1") }).status)
        assertEquals("completed", ok.executor.execute(buildJsonObject { put("collection", "A"); put("id", "angel") }).status)
        assertEquals("failed", ok.executor.execute(buildJsonObject { put("collection", "a"); put("id", "nope") }).status)
        val bad = avatarDownloadCapability(FakeSource(model("CC_BY_NC")), AvatarInstaller { n++ })
        assertEquals("failed", bad.executor.execute(buildJsonObject { put("collection", "a"); put("id", "1") }).status); assertEquals(2, n)
    }
}
