package com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.system.Archives
import com.rishitgoklani.hermes_front_mobile.domain.system.FileSandbox
import com.rishitgoklani.hermes_front_mobile.domain.system.HealthScore
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun JsonObject.s(k: String) = this[k]?.jsonPrimitive?.contentOrNull
private fun JsonObject.i(k: String) = this[k]?.jsonPrimitive?.intOrNull

/** Archive, hash and note tools that work inside the file sandbox. */
fun toolboxCapabilities(sandbox: FileSandbox, notesFile: File, hash: (File, String) -> String): List<Capability> {
    val denied = ActionResult(ActionResult.DENIED, "Path outside allowed folders")
    return listOf(
        Capability("archive_zip", "Zip a file or folder (inside allowed folders) into dest.zip.", """{"type":"object","properties":{"source":{"type":"string"},"dest":{"type":"string"}},"required":["source","dest"]}""") { a ->
            val src = sandbox.resolve(a.s("source").orEmpty()) ?: return@Capability denied; val dest = sandbox.resolve(a.s("dest").orEmpty()) ?: return@Capability denied
            if (!src.exists()) return@Capability ActionResult.failed("source not found"); val n = Archives.zip(src, dest); ActionResult.completed("Zipped $n files (${dest.length() / 1024} KB)")
        },
        Capability("archive_unzip", "Extract a zip (zip-slip and size guarded) into a folder.", """{"type":"object","properties":{"zip":{"type":"string"},"dest":{"type":"string"}},"required":["zip","dest"]}""") { a ->
            val z = sandbox.resolve(a.s("zip").orEmpty()) ?: return@Capability denied; val d = sandbox.resolve(a.s("dest").orEmpty()) ?: return@Capability denied
            if (!z.isFile) return@Capability ActionResult.failed("zip not found")
            try { ActionResult.completed("Extracted ${Archives.unzip(z, d)} files") } catch (e: SecurityException) { ActionResult(ActionResult.DENIED, e.message ?: "unsafe archive") }
        },
        Capability("file_hash", "Checksum of a file (algo sha256 default, md5, sha1).", """{"type":"object","properties":{"path":{"type":"string"},"algo":{"type":"string"}},"required":["path"]}""") { a ->
            val f = sandbox.resolve(a.s("path").orEmpty()) ?: return@Capability denied; if (!f.isFile) return@Capability ActionResult.failed("not a file")
            val algo = when (a.s("algo")?.lowercase()) { null, "sha256", "sha-256" -> "SHA-256"; "md5" -> "MD5"; "sha1", "sha-1" -> "SHA-1"; else -> return@Capability ActionResult.failed("algo must be sha256, md5 or sha1") }
            val h = hash(f, algo); ActionResult.completed(h, buildJsonObject { put("hash", h); put("algo", algo) })
        },
        Capability("quick_note", "Append a timestamped note (or saved link) to the notes file for later recall; optional tag.", """{"type":"object","properties":{"text":{"type":"string"},"tag":{"type":"string"}},"required":["text"]}""") { a ->
            val t = a.s("text")?.trim().orEmpty(); if (t.isEmpty()) return@Capability ActionResult.failed("text required")
            notesFile.parentFile?.mkdirs(); notesFile.appendText("- ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())} ${a.s("tag")?.let { "[$it] " }.orEmpty()}${t.take(2000)}\n"); ActionResult.completed("Noted")
        },
        Capability("notes_search", "Search quick notes by text (case-insensitive); returns up to 20 recent matches.", """{"type":"object","properties":{"query":{"type":"string"}}}""") { a ->
            val q = a.s("query").orEmpty(); val hits = runCatching { notesFile.readLines() }.getOrDefault(emptyList()).filter { it.contains(q, true) }.takeLast(20)
            ActionResult.completed(if (hits.isEmpty()) "No notes" else hits.joinToString("\n"))
        },
    )
}

/** Combines signals into one 0-100 health score (#186). Inputs come from injected readers so it stays testable. */
fun healthScoreCapability(read: suspend () -> HealthScore.Input) = Capability("health_score", "One 0-100 device health score with the worst issue (battery, heat, storage, network, tool failures, kill switch).") {
    val r = HealthScore.compute(read())
    ActionResult.completed("${r.score}/100" + if (r.issue != "none") " (${r.issue})" else "", buildJsonObject { put("score", r.score); put("issue", r.issue) })
}
