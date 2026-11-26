package com.rishitgoklani.hermes_front_mobile.engine.vrm

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max

/**
 * A model's textures already compressed for the GPU (ETC2), every image with its whole mip chain, so a load uploads them as they
 * are: no PNG/JPEG decode, no mipmap generation, a quarter to an eighth of the memory. The chain is built at the model's largest
 * allowed size; a smaller budget simply starts further down it ([Image.firstLevelFor]). Valid only for the [SourceStamp] and
 * encoder version it was written with; anything else (or a damaged file) reads as null and the model is decoded as before.
 *
 * Layout: header and image table (DataOutput, big-endian), padding to 16, then the compressed levels (each 16-byte aligned).
 */
object TextureCacheFile {
    const val FORMAT = 1
    private const val MAGIC = 0x56525458   // "VRTX"
    private const val ALIGN = 16

    class Level(val width: Int, val height: Int, val offset: Long, val size: Int)

    class Image(val index: Int, val glFormat: Int, val alpha: Boolean, val levels: List<Level>) {
        val width get() = levels[0].width
        val height get() = levels[0].height
        /** The first mip level whose longest side fits [cap]: the same size a decode capped at [cap] would give. */
        fun firstLevelFor(cap: Int): Int { var l = 0; while (l < levels.size - 1 && max(levels[l].width, levels[l].height) > cap) l++; return l }
        /** GPU bytes of the chain from [level] down. */
        fun bytesFrom(level: Int): Long = levels.drop(level).sumOf { it.size.toLong() }
    }

    /** An opened cache file: the image table and the memory-mapped data. */
    class Contents internal constructor(val cap: Int, val images: Map<Int, Image>, private val map: ByteBuffer, private val dataStart: Long) {
        /** The compressed bytes of [level], a slice of the mapped file (no copy). */
        fun data(level: Level): ByteBuffer {
            val start = (dataStart + level.offset).toInt()
            return map.duplicate().also { it.position(start); it.limit(start + level.size) }.slice()
        }
        /** Covers every image in [used]: the model can be loaded from the cache alone. */
        fun covers(used: Collection<Int>) = used.all { it in images }
    }

    /** Collects compressed images in memory, then writes them in one go. */
    class Builder(private val cap: Int) {
        private class Pending(val index: Int, val glFormat: Int, val alpha: Boolean, val levels: List<Triple<Int, Int, ByteArray>>)
        private val images = ArrayList<Pending>()
        val count get() = images.size

        /** [levels] = (width, height, compressed bytes) from the largest level down to 1x1. */
        fun add(index: Int, glFormat: Int, alpha: Boolean, levels: List<Triple<Int, Int, ByteArray>>) {
            require(levels.isNotEmpty()) { "image $index has no levels" }
            images += Pending(index, glFormat, alpha, levels)
        }

        fun write(out: File, source: SourceStamp, encoderVersion: Int) {
            var offset = 0L
            val table = ByteArrayOutputStream()
            DataOutputStream(table).use { d ->
                d.writeInt(MAGIC); d.writeInt(FORMAT); d.writeInt(encoderVersion); d.writeLong(source.length); d.writeLong(source.modified); d.writeInt(cap)
                d.writeInt(images.size)
                for (im in images) {
                    d.writeInt(im.index); d.writeInt(im.glFormat); d.writeBoolean(im.alpha); d.writeInt(im.levels.size)
                    for ((w, h, bytes) in im.levels) { d.writeInt(w); d.writeInt(h); d.writeLong(offset); d.writeInt(bytes.size); offset = align(offset + bytes.size) }
                }
            }
            val tmp = File(out.parentFile, out.name + ".tmp")
            RandomAccessFile(tmp, "rw").use { raf ->
                raf.setLength(0); val ch = raf.channel
                val t = table.toByteArray(); ch.write(ByteBuffer.wrap(ByteBuffer.allocate(4).putInt(t.size).array())); ch.write(ByteBuffer.wrap(t)); pad(ch)
                for (im in images) for ((_, _, bytes) in im.levels) { ch.write(ByteBuffer.wrap(bytes)); pad(ch) }
                ch.force(false)
            }
            if (!tmp.renameTo(out)) { out.delete(); check(tmp.renameTo(out)) { "could not move ${tmp.name} into place" } }
        }
    }

    /** The cache for [source], or null when missing, damaged, or made from another source or encoder version. */
    fun read(file: File, source: SourceStamp, encoderVersion: Int): Contents? {
        if (!file.isFile) return null
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val map = raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length())
                val tableLen = map.duplicate().int
                val d = DataInputStream(ByteBufferInputStream(map.duplicate().also { it.position(4); it.limit(4 + tableLen) }.slice()))
                if (d.readInt() != MAGIC || d.readInt() != FORMAT || d.readInt() != encoderVersion) return null
                if (SourceStamp(d.readLong(), d.readLong()) != source) return null
                val cap = d.readInt()
                val images = LinkedHashMap<Int, Image>()
                repeat(d.readInt()) {
                    val index = d.readInt(); val fmt = d.readInt(); val alpha = d.readBoolean()
                    images[index] = Image(index, fmt, alpha, List(d.readInt()) { Level(d.readInt(), d.readInt(), d.readLong(), d.readInt()) })
                }
                val dataStart = align(4L + tableLen)
                val end = images.values.flatMap { it.levels }.maxOfOrNull { dataStart + it.offset + it.size } ?: dataStart
                if (end > map.limit()) return null   // truncated
                Contents(cap, images, map, dataStart)
            }
        } catch (_: Exception) { null }
    }

    private fun align(v: Long) = (v + ALIGN - 1) / ALIGN * ALIGN
    private fun pad(ch: FileChannel) { val p = ch.position(); val t = align(p); if (t > p) ch.write(ByteBuffer.allocate((t - p).toInt())) }
}
