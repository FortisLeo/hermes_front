package com.rishitgoklani.hermes_front_mobile.data.avatar

import com.rishitgoklani.hermes_front_mobile.domain.avatar.AvatarCatalogSource
import com.rishitgoklani.hermes_front_mobile.domain.avatar.CatalogAvatar
import com.rishitgoklani.hermes_front_mobile.domain.avatar.CatalogCollection
import com.rishitgoklani.hermes_front_mobile.domain.avatar.OsaCatalog
import com.rishitgoklani.hermes_front_mobile.domain.avatar.VrmLicense
import com.rishitgoklani.hermes_front_mobile.domain.system.UrlGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Reads the OpenSourceAvatars registry (JSON on GitHub) and its model files over HTTPS.
 * Every URL, including those found inside the registry data, goes through [guard] (no local or private hosts), and downloads are size-capped.
 */
class OsaHttpCatalog(
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS).readTimeout(15, java.util.concurrent.TimeUnit.SECONDS).build(),
    private val projectsUrl: String = OsaCatalog.projectsUrl(),
    private val fileUrl: (CatalogCollection) -> String = OsaCatalog::fileUrl,
    private val guard: (String) -> String? = UrlGuard::check,
    private val retryDelayMs: Long = 1500,
) : AvatarCatalogSource {
    /** Preview images come from a host that answers 429 when many are requested at once, so only a few run in parallel. */
    private val thumbSlots = Semaphore(3)
    private var cachedCollections: List<CatalogCollection>? = null
    private val cachedAvatars = HashMap<String, List<CatalogAvatar>>()

    override suspend fun collections(): List<CatalogCollection> =
        cachedCollections ?: OsaCatalog.collections(String(get(projectsUrl, JSON_MAX), Charsets.UTF_8)).also { if (it.isNotEmpty()) cachedCollections = it }

    override suspend fun avatars(collection: CatalogCollection): List<CatalogAvatar> =
        cachedAvatars[collection.id] ?: OsaCatalog.avatars(String(get(fileUrl(collection), JSON_MAX), Charsets.UTF_8), collection).also { cachedAvatars[collection.id] = it }

    override suspend fun download(url: String): ByteArray = firstOf(url, VrmLicense.MAX_BYTES)

    override suspend fun thumbnail(url: String): ByteArray? = thumbSlots.withPermit { runCatching { firstOf(url, THUMB_MAX) }.getOrNull() }

    /** arweave.net is often slow or down; the same transaction id is served by another public gateway, so try that when the first fails. */
    private suspend fun firstOf(url: String, max: Int): ByteArray {
        val urls = listOf(url) + (ARWEAVE.matchEntire(url)?.let { listOf(MIRROR + it.groupValues[1]) } ?: emptyList())
        var last: Exception? = null
        for (u in urls) try { return get(u, max) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { last = e }
        throw last ?: IOException("download failed")
    }

    /** GET with up to two retries when the server says 429/503 (waits for Retry-After, capped at 6 s). */
    private suspend fun get(url: String, max: Int): ByteArray {
        guard(url)?.let { throw IOException(it) }
        var attempt = 0
        while (true) {
            try { return withContext(Dispatchers.IO) { fetch(url, max) } }
            catch (e: Busy) { if (++attempt > 2) throw IOException("server busy (HTTP ${e.code}), try again in a minute"); delay(e.waitMs ?: (retryDelayMs * attempt)) }
        }
    }

    private class Busy(val code: Int, val waitMs: Long?) : IOException()

    private fun fetch(url: String, max: Int): ByteArray {
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (r.code == 429 || r.code == 503) throw Busy(r.code, r.header("Retry-After")?.toLongOrNull()?.let { (it * 1000).coerceIn(0, 6000) })
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val body = r.body ?: throw IOException("empty response")
            if (body.contentLength() > max) throw IOException("file larger than ${max / 1024 / 1024} MB")
            val out = ByteArrayOutputStream(); val buf = ByteArray(64 * 1024); var total = 0
            body.byteStream().use { input -> while (true) { val n = input.read(buf); if (n < 0) break; total += n; if (total > max) throw IOException("file larger than ${max / 1024 / 1024} MB"); out.write(buf, 0, n) } }
            return out.toByteArray()
        }
    }

    private companion object { val ARWEAVE = Regex("https://arweave\\.net/([A-Za-z0-9_-]{43})"); const val MIRROR = "https://turbo-gateway.com/"; const val JSON_MAX = 12 * 1024 * 1024; const val THUMB_MAX = 3 * 1024 * 1024 }
}
