package com.rishitgoklani.hermes_front_mobile.domain.avatar.library

import com.rishitgoklani.hermes_front_mobile.domain.avatar.ImageDims
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class OptimizeResult(val bytes: ByteArray, val shrunkImages: Int, val savedBytes: Long)

/**
 * Rewrites a GLB/VRM so no embedded image is larger than the cap: oversized images go through [ImageTranscoder], the binary chunk is
 * rebuilt and every bufferView offset/length (and the buffer length) is updated. Anything unexpected returns the original bytes.
 */
class GlbOptimizer(private val transcoder: ImageTranscoder) {
    fun optimize(glb: ByteArray, maxSide: Int, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): OptimizeResult {
        val untouched = OptimizeResult(glb, 0, 0)
        val parsed = Glb.parse(glb) ?: return untouched
        val root = parsed.json
        val views = (root["bufferViews"] as? JsonArray)?.map { it as? JsonObject ?: return untouched } ?: return untouched
        if (((root["buffers"] as? JsonArray)?.size ?: 0) != 1 || views.any { (it["buffer"]?.jsonPrimitive?.intOrNull ?: 0) != 0 }) return untouched
        val images = (root["images"] as? JsonArray).orEmpty().map { it as? JsonObject }
        val mimeByView = HashMap<Int, String>()
        images.forEach { img -> val v = img?.get("bufferView")?.jsonPrimitive?.intOrNull; if (v != null) mimeByView[v] = img["mimeType"]?.jsonPrimitive?.contentOrNull ?: "image/png" }

        val newData = ArrayList<ByteArray>(views.size); var shrunk = 0; var done = 0
        views.forEachIndexed { i, v ->
            val off = v["byteOffset"]?.jsonPrimitive?.intOrNull ?: 0; val len = v["byteLength"]?.jsonPrimitive?.intOrNull ?: return untouched
            if (off < 0 || len < 0 || off + len > parsed.bin.size) return untouched
            var data = parsed.bin.copyOfRange(off, off + len)
            val mime = mimeByView[i]
            if (mime != null) {
                val dim = ImageDims.of(data.copyOf(minOf(data.size, 262144)))
                if (dim != null && maxOf(dim.w, dim.h) > maxSide) transcoder.shrink(data, mime, maxSide)?.takeIf { it.size < data.size }?.let { data = it; shrunk++ }
                onProgress(++done, mimeByView.size)
            }
            newData += data
        }
        if (shrunk == 0) return untouched

        val bin = java.io.ByteArrayOutputStream(); val newViews = ArrayList<JsonObject>()
        views.forEachIndexed { i, v ->
            while (bin.size() % 4 != 0) bin.write(0)
            val start = bin.size(); bin.write(newData[i])
            newViews += JsonObject(v + mapOf("byteOffset" to JsonPrimitive(start), "byteLength" to JsonPrimitive(newData[i].size)))
        }
        val total = bin.size()
        val buffer0 = (root["buffers"] as JsonArray)[0].jsonObject
        val newRoot = JsonObject(root + mapOf("bufferViews" to JsonArray(newViews), "buffers" to JsonArray(listOf(JsonObject(buffer0 + mapOf("byteLength" to JsonPrimitive(total)))))))
        val out = Glb.write(newRoot, bin.toByteArray())
        return OptimizeResult(out, shrunk, (glb.size - out.size).toLong())
    }
    private fun JsonArray?.orEmpty() = this ?: JsonArray(emptyList())
}

/** Minimal GLB container reader/writer (one JSON chunk, one optional BIN chunk). */
object Glb {
    class Parsed(val json: JsonObject, val bin: ByteArray)
    private fun le(b: ByteArray, o: Int) = (b[o].toInt() and 255) or (b[o + 1].toInt() and 255 shl 8) or (b[o + 2].toInt() and 255 shl 16) or (b[o + 3].toInt() and 255 shl 24)
    private fun put(out: java.io.ByteArrayOutputStream, v: Int) { out.write(v and 255); out.write(v shr 8 and 255); out.write(v shr 16 and 255); out.write(v shr 24 and 255) }

    fun parse(glb: ByteArray): Parsed? {
        if (glb.size < 28 || String(glb, 0, 4, Charsets.US_ASCII) != "glTF") return null
        val jl = le(glb, 12); if (jl <= 0 || 20 + jl + 8 > glb.size) return null
        val json = runCatching { Json.parseToJsonElement(String(glb, 20, jl, Charsets.UTF_8)).jsonObject }.getOrNull() ?: return null
        val bl = le(glb, 20 + jl); val start = 20 + jl + 8
        if (bl < 0 || start + bl > glb.size) return null
        return Parsed(json, glb.copyOfRange(start, start + bl))
    }

    fun write(json: JsonObject, bin: ByteArray): ByteArray {
        var j = Json.encodeToString(JsonObject.serializer(), json).toByteArray(Charsets.UTF_8)
        val jp = (4 - j.size % 4) % 4; j += ByteArray(jp) { ' '.code.toByte() }
        val bp = (4 - bin.size % 4) % 4
        val out = java.io.ByteArrayOutputStream()
        out.write("glTF".toByteArray()); put(out, 2); put(out, 12 + 8 + j.size + 8 + bin.size + bp)
        put(out, j.size); out.write("JSON".toByteArray()); out.write(j)
        put(out, bin.size + bp); out.write(byteArrayOf('B'.code.toByte(), 'I'.code.toByte(), 'N'.code.toByte(), 0)); out.write(bin); out.write(ByteArray(bp))
        return out.toByteArray()
    }
}
