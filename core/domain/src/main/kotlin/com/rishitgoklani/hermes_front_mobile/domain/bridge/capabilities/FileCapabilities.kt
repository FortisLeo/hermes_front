package com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.system.FileSandbox
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

private fun JsonObject.s(k: String) = this[k]?.jsonPrimitive?.contentOrNull
private const val MAX_READ = 64_000

fun fileCapabilities(sandbox: FileSandbox): List<Capability> {
    fun file(a: JsonObject): File? = sandbox.resolve(a.s("path").orEmpty())
    val denied = ActionResult(ActionResult.DENIED, "Path outside allowed folders")
    return listOf(
        Capability("file_list", "List a folder (name, size, is_dir).", """{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}""") { a ->
            val dir = file(a) ?: return@Capability denied
            if (!dir.isDirectory) return@Capability ActionResult.failed("Not a directory")
            val items = dir.listFiles().orEmpty().sortedBy { it.name.lowercase() }.take(300)
            ActionResult.completed(items.joinToString("\n") { (if (it.isDirectory) "d " else "- ") + it.name + (if (it.isFile) "  ${it.length()}" else "") },
                buildJsonObject { put("count", items.size) })
        },
        Capability("file_read", "Read a text file (first 64 KB; use offset for more).", """{"type":"object","properties":{"path":{"type":"string"},"offset":{"type":"integer"}},"required":["path"]}""") { a ->
            val f = file(a) ?: return@Capability denied
            if (!f.isFile) return@Capability ActionResult.failed("Not a file")
            val off = (a["offset"]?.jsonPrimitive?.intOrNull ?: 0).coerceAtLeast(0)
            val bytes = f.inputStream().use { it.skip(off.toLong()); it.readNBytes(MAX_READ) }
            ActionResult.completed(String(bytes), buildJsonObject { put("bytes", bytes.size); put("total", f.length()) })
        },
        Capability("file_write", "Write (or append) text to a file, creating parent folders.", """{"type":"object","properties":{"path":{"type":"string"},"content":{"type":"string"},"append":{"type":"boolean"}},"required":["path","content"]}""") { a ->
            val f = file(a) ?: return@Capability denied
            f.parentFile?.mkdirs()
            if (a["append"]?.jsonPrimitive?.booleanOrNull == true) f.appendText(a.s("content").orEmpty()) else f.writeText(a.s("content").orEmpty())
            ActionResult.completed("Wrote ${f.length()} bytes")
        },
        Capability("file_delete", "Delete a file or an empty folder (recursive=true for non-empty).", """{"type":"object","properties":{"path":{"type":"string"},"recursive":{"type":"boolean"}},"required":["path"]}""") { a ->
            val f = file(a) ?: return@Capability denied
            if (!f.exists()) return@Capability ActionResult.failed("Does not exist")
            val ok = if (a["recursive"]?.jsonPrimitive?.booleanOrNull == true) f.deleteRecursively() else f.delete()
            if (ok) ActionResult.completed("Deleted") else ActionResult.failed("Could not delete (folder not empty?)")
        },
        Capability("file_search", "Find files under a folder whose name contains `name` (case-insensitive), or text files containing `text`.",
            """{"type":"object","properties":{"path":{"type":"string"},"name":{"type":"string"},"text":{"type":"string"},"limit":{"type":"integer"}},"required":["path"]}""") { a ->
            val dir = file(a) ?: return@Capability denied
            val limit = a["limit"]?.jsonPrimitive?.intOrNull ?: 50
            val name = a.s("name"); val text = a.s("text")
            val hits = dir.walkTopDown().onEnter { it.name != "proc" }.filter { it.isFile }
                .filter { name == null || it.name.contains(name, true) }
                .filter { text == null || (it.length() < 2_000_000 && runCatching { it.readText().contains(text, true) }.getOrDefault(false)) }
                .take(limit).toList()
            ActionResult.completed(if (hits.isEmpty()) "No matches" else hits.joinToString("\n") { it.path }, buildJsonObject { put("count", hits.size) })
        },
        Capability("file_move", "Move or rename a file/folder within allowed folders.", """{"type":"object","properties":{"from":{"type":"string"},"to":{"type":"string"}},"required":["from","to"]}""") { a ->
            val from = sandbox.resolve(a.s("from").orEmpty()) ?: return@Capability denied
            val to = sandbox.resolve(a.s("to").orEmpty()) ?: return@Capability denied
            to.parentFile?.mkdirs()
            if (from.renameTo(to)) ActionResult.completed("Moved") else ActionResult.failed("Move failed")
        },
    )
}
