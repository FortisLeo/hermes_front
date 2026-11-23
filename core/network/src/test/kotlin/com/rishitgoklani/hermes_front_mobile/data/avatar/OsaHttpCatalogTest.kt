package com.rishitgoklani.hermes_front_mobile.data.avatar

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OsaHttpCatalogTest {
    private lateinit var server: MockWebServer
    @Before fun up() { server = MockWebServer().also { it.start() } }
    @After fun down() = server.shutdown()
    private fun catalog(guard: (String) -> String? = { null }) = OsaHttpCatalog(projectsUrl = server.url("/projects.json").toString(), fileUrl = { server.url("/" + it.file).toString() }, guard = guard)

    @Test fun loadsCollectionsThenAvatarsAndCachesBoth() = runBlocking {
        server.enqueue(MockResponse().setBody("""[{"id":"a","name":"A","license":"CC0","avatar_data_file":"a.json"}]"""))
        server.enqueue(MockResponse().setBody("""[{"id":"1","name":"Devil","format":"VRM","model_file_url":"https://x/1"}]"""))
        val c = catalog(); val cols = c.collections(); assertEquals("a", cols[0].id)
        assertEquals("Devil", c.avatars(cols[0])[0].name)
        c.collections(); c.avatars(cols[0]); assertEquals(2, server.requestCount)
    }
    @Test fun downloadReturnsBytesAndRejectsOversize() = runBlocking {
        server.enqueue(MockResponse().setBody("abc")); assertEquals("abc", String(catalog().download(server.url("/m").toString())))
        server.enqueue(MockResponse().setResponseCode(404)); assertTrue(runCatching { catalog().download(server.url("/m").toString()) }.isFailure)
    }
    @Test fun guardBlocksBadUrlsBeforeAnyRequest() = runBlocking {
        val c = catalog { "blocked" }; assertTrue(runCatching { c.download("http://127.0.0.1/x") }.isFailure); assertEquals(0, server.requestCount)
    }
    @Test fun thumbnailIsNullOnFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500)); assertNull(catalog().thumbnail(server.url("/t").toString()))
        server.enqueue(MockResponse().setBody("img")); assertEquals(3, catalog().thumbnail(server.url("/t").toString())!!.size)
    }
}
