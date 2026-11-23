package com.rishitgoklani.hermes_front_mobile.domain.avatar

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A group of avatars in the OpenSourceAvatars registry (https://github.com/ToxSam/open-source-avatars, data/projects.json). */
data class CatalogCollection(val id: String, val name: String, val license: String, val file: String)

data class CatalogAvatar(val id: String, val name: String, val collection: String, val license: String, val modelUrl: String, val thumbUrl: String)

/** Where catalog data and model files come from. Implemented over HTTP in :core:network; faked in tests. */
interface AvatarCatalogSource {
    suspend fun collections(): List<CatalogCollection>
    suspend fun avatars(collection: CatalogCollection): List<CatalogAvatar>
    /** Model bytes (max [VrmLicense.MAX_BYTES]); throws on network failure or oversize. */
    suspend fun download(url: String): ByteArray
    /** Small preview image bytes, or null if unavailable. */
    suspend fun thumbnail(url: String): ByteArray?
}

/** Parses the registry JSON. Only public, non-draft VRM entries from collections under a permissive license are listed. */
object OsaCatalog {
    const val BASE = "https://raw.githubusercontent.com/ToxSam/open-source-avatars/main/data/"
    private val allowed = setOf("cc0", "cc-by", "cc-by-sa")
    private val json = Json { ignoreUnknownKeys = true }

    fun collections(text: String): List<CatalogCollection> = parseArray(text).mapNotNull { o ->
        val id = o.str("id").ifEmpty { return@mapNotNull null }
        val lic = o.str("license")
        val file = o.str("avatar_data_file")
        if (lic.lowercase() !in allowed || file.isEmpty() || o["is_public"]?.jsonPrimitive?.contentOrNull == "false") null
        else CatalogCollection(id, o.str("name").ifEmpty { id }, lic, file)
    }

    fun avatars(text: String, c: CatalogCollection): List<CatalogAvatar> = parseArray(text).mapNotNull { o ->
        val id = o.str("id").ifEmpty { return@mapNotNull null }
        val url = o.str("model_file_url")
        if (o.str("format").uppercase() != "VRM" || !url.startsWith("https://")) return@mapNotNull null
        if (o["is_public"]?.jsonPrimitive?.contentOrNull == "false" || o["is_draft"]?.jsonPrimitive?.contentOrNull == "true") return@mapNotNull null
        CatalogAvatar(id, o.str("name").ifEmpty { id }, c.id, c.license, url, o.str("thumbnail_url").takeIf { it.startsWith("https://") }.orEmpty())
    }

    fun search(list: List<CatalogAvatar>, query: String): List<CatalogAvatar> {
        val q = query.trim().lowercase(); return if (q.isEmpty()) list else list.filter { q in it.name.lowercase() || q in it.id.lowercase() }
    }

    /** Catalog file for a collection, e.g. data/avatars/100avatars-r1.json. */
    fun fileUrl(c: CatalogCollection) = BASE + c.file.trimStart('/')
    fun projectsUrl() = BASE + "projects.json"

    private fun parseArray(text: String): List<JsonObject> =
        (runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.contentOrNull.orEmpty()
}

sealed interface InstallResult {
    data class Installed(val info: VrmInfo) : InstallResult
    data class Rejected(val reason: String) : InstallResult
}

/** One place that decides whether model bytes become the avatar: license-checks first, installs only accepted models. Used by upload, catalog and the agent tools. */
class AvatarInstaller(private val store: suspend (ByteArray) -> Unit) {
    /** [enforceLicense] is true for downloads; the user's own uploaded files skip the license rule but must still be valid VRMs. */
    suspend fun install(bytes: ByteArray, enforceLicense: Boolean = true): InstallResult = AvatarModelGuard.problem(bytes)?.let { InstallResult.Rejected(it) } ?: when (val c = VrmLicense.check(bytes, enforceLicense)) {
        is VrmCheck.Rejected -> InstallResult.Rejected(c.reason)
        is VrmCheck.Ok -> { store(bytes); InstallResult.Installed(c.info) }
    }
}

/** avatar_catalog: browse the OpenSourceAvatars registry (collections, or avatars of one collection, optionally filtered). */
fun avatarCatalogCapability(source: AvatarCatalogSource) = Capability(
    "avatar_catalog", "Browse the OpenSourceAvatars catalog of free VRM avatars. action=collections lists collections; action=list (collection, optional query, limit) lists avatars. Use avatar_download to install one.",
    """{"type":"object","properties":{"action":{"type":"string","enum":["collections","list"]},"collection":{"type":"string"},"query":{"type":"string"},"limit":{"type":"integer"}}}""",
) { a ->
    runCatching {
        if (a["action"]?.jsonPrimitive?.contentOrNull != "list") {
            ActionResult.completed(source.collections().joinToString("\n") { "${it.id}: ${it.name} (${it.license})" }.ifEmpty { "no collections" })
        } else {
            val col = findCollection(source, a["collection"]?.jsonPrimitive?.contentOrNull) ?: return@runCatching ActionResult.failed("unknown collection; call action=collections")
            val limit = (a["limit"]?.jsonPrimitive?.intOrNull ?: 20).coerceIn(1, 100)
            val found = OsaCatalog.search(source.avatars(col), a["query"]?.jsonPrimitive?.contentOrNull.orEmpty())
            ActionResult.completed(found.take(limit).joinToString("\n") { "${it.id} | ${it.name}" } + if (found.size > limit) "\n… ${found.size - limit} more (narrow with query)" else "")
        }
    }.getOrElse { ActionResult.failed("catalog unavailable: ${it.message}") }
}

/** avatar_download: fetch one catalog avatar, license-check it and make it the avatar. */
fun avatarDownloadCapability(source: AvatarCatalogSource, installer: AvatarInstaller) = Capability(
    "avatar_download", "Download an avatar from the OpenSourceAvatars catalog (collection + id or exact name) and use it. Models with non-permissive licenses are rejected.",
    """{"type":"object","properties":{"collection":{"type":"string"},"id":{"type":"string"}},"required":["collection","id"]}""",
) { a ->
    runCatching {
        val col = findCollection(source, a["collection"]?.jsonPrimitive?.contentOrNull) ?: return@runCatching ActionResult.failed("unknown collection")
        val key = a["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val av = source.avatars(col).firstOrNull { it.id == key } ?: source.avatars(col).firstOrNull { it.name.equals(key, true) } ?: return@runCatching ActionResult.failed("no avatar '$key' in ${col.id}")
        when (val r = installer.install(source.download(av.modelUrl))) {
            is InstallResult.Rejected -> ActionResult.failed("Model rejected: ${r.reason}")
            is InstallResult.Installed -> ActionResult.completed("Using '${av.name}' from ${col.name} (${r.info.license})")
        }
    }.getOrElse { ActionResult.failed("download failed: ${it.message}") }
}

private suspend fun findCollection(source: AvatarCatalogSource, key: String?): CatalogCollection? {
    val all = source.collections(); return if (key.isNullOrEmpty()) all.firstOrNull() else all.firstOrNull { it.id.equals(key, true) || it.name.equals(key, true) }
}
