package com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/** A GLB container: the JSON chunk parsed, the BIN chunk exposed as a (memory-mapped when from a file) read-only buffer. */
class Glb(val json: JsonObject, val bin: ByteBuffer) {
    companion object {
        private const val MAGIC = 0x46546C67; private const val JSON = 0x4E4F534A; private const val BIN = 0x004E4942

        /** Maps the file instead of reading it, so the raw model stays in the page cache and not on the Java heap. */
        fun open(file: File): Glb = RandomAccessFile(file, "r").use { raf -> parse(raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length())) }

        fun parse(bytes: ByteArray): Glb = parse(ByteBuffer.wrap(bytes))

        fun parse(buf: ByteBuffer): Glb {
            val b = buf.duplicate().order(ByteOrder.LITTLE_ENDIAN); b.position(0)
            require(b.remaining() >= 20 && b.getInt(0) == MAGIC) { "Not a GLB file" }
            val total = minOf(b.getInt(8).toLong() and 0xFFFFFFFFL, b.limit().toLong()).toInt()
            var pos = 12; var json: JsonObject? = null; var bin: ByteBuffer? = null
            while (pos + 8 <= total) {
                val len = b.getInt(pos); val type = b.getInt(pos + 4); val start = pos + 8
                require(len >= 0 && start + len <= total) { "Corrupt GLB chunk" }
                val chunk = b.duplicate().order(ByteOrder.LITTLE_ENDIAN).also { it.position(start); it.limit(start + len) }.slice().order(ByteOrder.LITTLE_ENDIAN)
                if (type == JSON && json == null) {
                    val raw = ByteArray(len); chunk.get(raw)
                    json = Json.parseToJsonElement(String(raw, Charsets.UTF_8).trimEnd('\u0000', ' ')) as? JsonObject ?: error("GLB JSON is not an object")
                } else if (type == BIN && bin == null) bin = chunk.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)
                pos = start + ((len + 3) and 3.inv())
            }
            return Glb(json ?: error("GLB has no JSON chunk"), bin ?: ByteBuffer.allocate(0).order(ByteOrder.LITTLE_ENDIAN))
        }
    }
}

// Small JSON helpers: glTF is loosely typed and every field is optional.
internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject
internal fun JsonElement?.arr(): JsonArray = this as? JsonArray ?: JsonArray(emptyList())
internal fun JsonObject.o(k: String): JsonObject? = this[k] as? JsonObject
internal fun JsonObject.a(k: String): JsonArray = this[k].arr()
internal fun JsonObject.i(k: String): Int? = (this[k] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toInt() }
internal fun JsonObject.f(k: String): Float? = (this[k] as? JsonPrimitive)?.floatOrNull
internal fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
internal fun JsonObject.b(k: String): Boolean? = (this[k] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
internal fun JsonArray.floats(): FloatArray = FloatArray(size) { (this[it] as? JsonPrimitive)?.floatOrNull ?: 0f }
internal fun JsonArray.ints(): IntArray = IntArray(size) { (this[it] as? JsonPrimitive)?.intOrNull ?: 0 }
