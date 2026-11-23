package com.rishitgoklani.hermes_front_mobile.data.avatar

import com.rishitgoklani.hermes_front_mobile.domain.avatar.AvatarCatalogSource
import com.rishitgoklani.hermes_front_mobile.domain.avatar.CatalogAvatar
import com.rishitgoklani.hermes_front_mobile.domain.avatar.CatalogCollection
import com.rishitgoklani.hermes_front_mobile.domain.avatar.OsaCatalog
import com.rishitgoklani.hermes_front_mobile.domain.avatar.VrmLicense
import com.rishitgoklani.hermes_front_mobile.domain.system.UrlGuard
import kotlinx.coroutines.Dispatchers
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
    private val client: OkHttpClient = OkHttpClient(),
    private val projectsUrl: String = OsaCatalog.projectsUrl(),
    private val fileUrl: (CatalogCollection) -> String = OsaCatalog::fileUrl,
    private val guard: (String) -> String? = UrlGuard::check,
) : AvatarCatalogSource {
    private var cachedCollections: List<CatalogCollection>? = null
    private val cachedAvatars = HashMap<String, List<CatalogAvatar>>()

    override suspend fun collections(): List<CatalogCollection> =
        cachedCollections ?: OsaCatalog.collections(String(get(projectsUrl, JSON_MAX), Charsets.UTF_8)).also { if (it.isNotEmpty()) cachedCollections = it }

    override suspend fun avatars(collection: CatalogCollection): List<CatalogAvatar> =
        cachedAvatars[collection.id] ?: OsaCatalog.avatars(String(get(fileUrl(collection), JSON_MAX), Charsets.UTF_8), collection).also { cachedAvatars[collection.id] = it }

    override suspend fun download(url: String): ByteArray = get(url, VrmLicense.MAX_BYTES)

    override suspend fun thumbnail(url: String): ByteArray? = runCatching { get(url, THUMB_MAX) }.getOrNull()

    private suspend fun get(url: String, max: Int): ByteArray = withContext(Dispatchers.IO) {
        guard(url)?.let { throw IOException(it) }
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val body = r.body ?: throw IOException("empty response")
            if (body.contentLength() > max) throw IOException("file larger than ${max / 1024 / 1024} MB")
            val out = ByteArrayOutputStream(); val buf = ByteArray(64 * 1024); var total = 0
            body.byteStream().use { input -> while (true) { val n = input.read(buf); if (n < 0) break; total += n; if (total > max) throw IOException("file larger than ${max / 1024 / 1024} MB"); out.write(buf, 0, n) } }
            out.toByteArray()
        }
    }

    private companion object { const val JSON_MAX = 12 * 1024 * 1024; const val THUMB_MAX = 3 * 1024 * 1024 }
}
