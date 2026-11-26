package com.rishitgoklani.hermes_front_mobile.engine.vrm

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.IdentityHashMap

/** Identifies the exact source file a cache was made from: if the model file changes, its caches no longer match. */
data class SourceStamp(val length: Long, val modified: Long) {
    companion object { fun of(file: File) = SourceStamp(file.length(), file.lastModified()) }
}

/**
 * The parsed form of a model ([VrmAsset]) saved as one binary file, so the next load skips the GLB's JSON and the vertex packing.
 * Vertex, index and morph data are stored raw (already packed for the GPU) and come back as slices of the memory-mapped file, so
 * reading costs a few milliseconds whatever the model size. Buffers that several primitives share stay shared (the renderer relies
 * on that to upload them once). A cache is used only for the [SourceStamp] it was made from, this [FORMAT] and the parser's
 * [VrmParser.OUTPUT_VERSION]; anything else (or a damaged file) reads as null and the model is parsed again.
 *
 * Layout: header (magic, format, parser version, source stamp, metadata length), metadata (DataOutput, big-endian), padding to 16,
 * then the raw blobs (little-endian, each 16-byte aligned) that the metadata points into.
 */
object AssetCache {
    const val FORMAT = 1
    private const val MAGIC = 0x56524D47   // "VRMG"
    private const val ALIGN = 16

    fun write(asset: VrmAsset, out: File, source: SourceStamp) {
        val blobs = Blobs()
        val meta = ByteArrayOutputStream(); DataOutputStream(meta).use { d -> writeMeta(d, asset, blobs) }
        val metaBytes = meta.toByteArray()
        val header = ByteArrayOutputStream(); DataOutputStream(header).use { d ->
            d.writeInt(MAGIC); d.writeInt(FORMAT); d.writeInt(VrmParser.OUTPUT_VERSION); d.writeLong(source.length); d.writeLong(source.modified); d.writeInt(metaBytes.size)
        }
        val tmp = File(out.parentFile, out.name + ".tmp")
        RandomAccessFile(tmp, "rw").use { raf ->
            raf.setLength(0)
            val ch = raf.channel
            ch.write(ByteBuffer.wrap(header.toByteArray())); ch.write(ByteBuffer.wrap(metaBytes))
            padTo(ch, ALIGN)
            val dataStart = ch.position()
            for ((i, b) in blobs.list.withIndex()) { check(ch.position() - dataStart == blobs.offsets[i]); ch.write(b.duplicate().also { it.clear() }); padTo(ch, ALIGN) }
            ch.force(false)
        }
        if (!tmp.renameTo(out)) { out.delete(); check(tmp.renameTo(out)) { "could not move ${tmp.name} into place" } }
    }

    /** The cached asset, or null if [file] is missing, damaged or was made from another source/version. */
    fun read(file: File, source: SourceStamp, imageBytes: (Int) -> ByteArray): VrmAsset? {
        if (!file.isFile) return null
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val map = raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length())
                val h = DataInputStream(ByteBufferInputStream(map.duplicate()))
                if (h.readInt() != MAGIC || h.readInt() != FORMAT || h.readInt() != VrmParser.OUTPUT_VERSION) return null
                if (SourceStamp(h.readLong(), h.readLong()) != source) return null
                val metaLen = h.readInt(); val metaStart = 4 * 4 + 8 * 2
                val dataStart = align(metaStart + metaLen.toLong(), ALIGN)
                val meta = map.duplicate().also { it.position(metaStart); it.limit(metaStart + metaLen) }.slice()
                readMeta(DataInputStream(ByteBufferInputStream(meta)), map, dataStart, imageBytes)
            }
        } catch (_: Exception) { null }
    }

    // ---- blobs: every distinct array is stored once and referenced by id ----
    private class Blobs {
        val list = ArrayList<ByteBuffer>(); val offsets = ArrayList<Long>(); private var end = 0L
        private val ids = IdentityHashMap<Any, Int>()
        private fun add(key: Any, b: ByteBuffer): Int = ids.getOrPut(key) { list += b; offsets += end; end = align(end + b.capacity(), ALIGN); list.size - 1 }
        fun buf(b: ByteBuffer) = add(b, b)
        fun floats(a: FloatArray) = add(a, ByteBuffer.allocate(a.size * 4).order(ByteOrder.LITTLE_ENDIAN).also { it.asFloatBuffer().put(a) })
        fun ints(a: IntArray) = add(a, ByteBuffer.allocate(a.size * 4).order(ByteOrder.LITTLE_ENDIAN).also { it.asIntBuffer().put(a) })
        fun length(id: Int) = list[id].capacity()
    }

    private fun writeMeta(d: DataOutputStream, a: VrmAsset, blobs: Blobs) {
        d.writeBoolean(a.isVrm0)
        d.writeInt(a.nodes.size)
        for (n in a.nodes) { d.writeInt(n.index); d.writeUTF(n.name); d.writeInt(n.parent); ints(d, n.children); floats(d, n.t); floats(d, n.r); floats(d, n.s); d.writeInt(n.mesh); d.writeInt(n.skin) }
        ints(d, a.roots)
        d.writeInt(a.materials.size)
        for (m in a.materials) {
            d.writeUTF(m.name); floats(d, m.baseColor); floats(d, m.shadeColor); d.writeInt(m.baseImage); d.writeInt(m.shadeImage); floats(d, m.emissive); d.writeInt(m.emissiveImage)
            d.writeInt(m.alphaMode.ordinal); d.writeFloat(m.cutoff); d.writeBoolean(m.doubleSided); d.writeFloat(m.shadeShift); d.writeFloat(m.shadeToony); d.writeFloat(m.outlineWidth); floats(d, m.outlineColor); d.writeBoolean(m.unlit)
        }
        d.writeInt(a.skins.size); for (s in a.skins) { ints(d, s.joints); d.writeInt(blobs.floats(s.inverseBind)) }
        // Morph sets: the base positions and target list a group of primitives shares.
        val sets = IdentityHashMap<List<MorphTarget>, Int>(); val setList = ArrayList<List<MorphTarget>>()
        for (p in a.primitives) if (p.morphTargets.isNotEmpty()) sets.getOrPut(p.morphTargets) { setList += p.morphTargets; setList.size - 1 }
        d.writeInt(setList.size); for (set in setList) { d.writeInt(set.size); for (t in set) { d.writeInt(blobs.ints(t.vertices)); d.writeInt(blobs.floats(t.deltas)) } }
        d.writeInt(a.primitives.size)
        for (p in a.primitives) {
            d.writeInt(p.mesh); d.writeInt(p.node); d.writeInt(p.skin); d.writeInt(p.material); d.writeInt(p.vertexCount)
            d.writeInt(blobs.buf(p.positions)); d.writeInt(blobs.buf(p.attributes)); d.writeInt(p.attributeStride); d.writeInt(blobs.buf(p.indices)); d.writeInt(p.indexCount); d.writeBoolean(p.indicesAreInt)
            d.writeInt(p.morphBase?.let { blobs.floats(it) } ?: -1); d.writeInt(if (p.morphTargets.isEmpty()) -1 else sets.getValue(p.morphTargets))
        }
        strIntMap(d, a.humanoid)
        d.writeInt(a.expressions.size)
        for ((name, binds) in a.expressions) { d.writeUTF(name); d.writeInt(binds.size); for (b in binds) { d.writeInt(b.mesh); d.writeInt(b.index); d.writeFloat(b.weight) } }
        d.writeInt(a.meshTargetCounts.size); for ((k, v) in a.meshTargetCounts) { d.writeInt(k); d.writeInt(v) }
        d.writeInt(a.images.size); for (im in a.images) { d.writeUTF(im.mime); d.writeInt(im.view) }
        d.writeInt(a.springs.size)
        for (c in a.springs) {
            d.writeInt(c.joints.size)
            for (j in c.joints) { d.writeInt(j.node); floats(d, j.tailLocal); d.writeFloat(j.stiffness); d.writeFloat(j.gravityPower); floats(d, j.gravityDir); d.writeFloat(j.dragForce); d.writeFloat(j.hitRadius) }
            d.writeInt(c.center); ints(d, c.colliders)
        }
        d.writeInt(a.colliders.size)
        for (c in a.colliders) { d.writeInt(c.node); floats(d, c.offset); d.writeFloat(c.radius); d.writeBoolean(c.tail != null); c.tail?.let { floats(d, it) } }
        floats(d, a.boundsMin); floats(d, a.boundsMax)
        d.writeInt(a.expressionMeta.size)
        for ((name, m) in a.expressionMeta) { d.writeUTF(name); d.writeBoolean(m.isBinary); d.writeUTF(m.overrideBlink); d.writeUTF(m.overrideLookAt); d.writeUTF(m.overrideMouth) }
        d.writeInt(a.morphTargetNames.size)
        for ((mesh, names) in a.morphTargetNames) { d.writeInt(mesh); d.writeInt(names.size); names.forEach { d.writeUTF(it) } }
        // Blob table last: the ids above are final only now.
        d.writeInt(blobs.list.size); for (i in blobs.list.indices) { d.writeLong(blobs.offsets[i]); d.writeInt(blobs.length(i)) }
    }

    private fun readMeta(d: DataInputStream, map: ByteBuffer, dataStart: Long, imageBytes: (Int) -> ByteArray): VrmAsset {
        // The blob table sits at the end of the metadata; read everything else into lambdas that resolve blobs afterwards.
        val isVrm0 = d.readBoolean()
        val nodes = List(d.readInt()) { VrmNode(d.readInt(), d.readUTF(), d.readInt(), ints(d), floats(d), floats(d), floats(d), d.readInt(), d.readInt()) }
        val roots = ints(d)
        val materials = List(d.readInt()) {
            VrmMaterial(d.readUTF(), floats(d), floats(d), d.readInt(), d.readInt(), floats(d), d.readInt(), AlphaMode.entries[d.readInt()], d.readFloat(), d.readBoolean(),
                d.readFloat(), d.readFloat(), d.readFloat(), floats(d), d.readBoolean())
        }
        class SkinRef(val joints: IntArray, val ib: Int)
        val skinRefs = List(d.readInt()) { SkinRef(ints(d), d.readInt()) }
        val setRefs = List(d.readInt()) { List(d.readInt()) { d.readInt() to d.readInt() } }
        class PrimRef(val mesh: Int, val node: Int, val skin: Int, val material: Int, val n: Int, val pos: Int, val attr: Int, val stride: Int, val idx: Int, val idxCount: Int, val asInt: Boolean, val base: Int, val set: Int)
        val primRefs = List(d.readInt()) { PrimRef(d.readInt(), d.readInt(), d.readInt(), d.readInt(), d.readInt(), d.readInt(), d.readInt(), d.readInt(), d.readInt(), d.readInt(), d.readBoolean(), d.readInt(), d.readInt()) }
        val humanoid = strIntMap(d)
        val expressions = LinkedHashMap<String, List<ExpressionBind>>()
        repeat(d.readInt()) { val name = d.readUTF(); expressions[name] = List(d.readInt()) { ExpressionBind(d.readInt(), d.readInt(), d.readFloat()) } }
        val targetCounts = LinkedHashMap<Int, Int>(); repeat(d.readInt()) { targetCounts[d.readInt()] = d.readInt() }
        val images = List(d.readInt()) { ImageRef(d.readUTF(), d.readInt()) }
        val springs = List(d.readInt()) {
            val joints = List(d.readInt()) { SpringJoint(d.readInt(), floats(d), d.readFloat(), d.readFloat(), floats(d), d.readFloat(), d.readFloat()) }
            SpringChain(joints, d.readInt(), ints(d))
        }
        val colliders = List(d.readInt()) { SphereCollider(d.readInt(), floats(d), d.readFloat(), if (d.readBoolean()) floats(d) else null) }
        val bMin = floats(d); val bMax = floats(d)
        val exprMeta = LinkedHashMap<String, ExpressionMeta>(); repeat(d.readInt()) { exprMeta[d.readUTF()] = ExpressionMeta(d.readBoolean(), d.readUTF(), d.readUTF(), d.readUTF()) }
        val names = LinkedHashMap<Int, List<String>>(); repeat(d.readInt()) { val mesh = d.readInt(); names[mesh] = List(d.readInt()) { d.readUTF() } }
        val table = List(d.readInt()) { d.readLong() to d.readInt() }

        // Resolve blobs: one ByteBuffer per id (sharing preserved), arrays copied out for the CPU-side data.
        val bufs = HashMap<Int, ByteBuffer>()
        fun buf(id: Int): ByteBuffer = bufs.getOrPut(id) {
            val (off, len) = table[id]; val start = dataStart + off
            require(start + len <= map.limit()) { "blob $id past end of file" }
            map.duplicate().also { it.position(start.toInt()); it.limit((start + len).toInt()) }.slice().order(ByteOrder.LITTLE_ENDIAN)
        }
        val floatCache = HashMap<Int, FloatArray>(); val intCache = HashMap<Int, IntArray>()
        fun floatsOf(id: Int) = floatCache.getOrPut(id) { val b = buf(id); FloatArray(b.capacity() / 4).also { b.duplicate().order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(it) } }
        fun intsOf(id: Int) = intCache.getOrPut(id) { val b = buf(id); IntArray(b.capacity() / 4).also { b.duplicate().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(it) } }
        val sets = setRefs.map { set -> set.map { (v, dl) -> MorphTarget(intsOf(v), floatsOf(dl)) } }
        val prims = primRefs.map { p ->
            VrmPrimitive(p.mesh, p.node, p.skin, p.material, p.n, buf(p.pos), buf(p.attr), p.stride, buf(p.idx), p.idxCount, p.asInt,
                if (p.base >= 0) floatsOf(p.base) else null, if (p.set >= 0) sets[p.set] else emptyList())
        }
        val skins = skinRefs.map { VrmSkin(it.joints, floatsOf(it.ib)) }
        return VrmAsset(isVrm0, nodes, roots, prims, materials, skins, humanoid, expressions, targetCounts, images, springs, colliders, bMin, bMax, exprMeta, names) { view -> imageBytes(view) }
    }

    private fun ints(d: DataOutputStream, a: IntArray) { d.writeInt(a.size); a.forEach { d.writeInt(it) } }
    private fun floats(d: DataOutputStream, a: FloatArray) { d.writeInt(a.size); a.forEach { d.writeFloat(it) } }
    private fun ints(d: DataInputStream) = IntArray(d.readInt()) { d.readInt() }
    private fun floats(d: DataInputStream) = FloatArray(d.readInt()) { d.readFloat() }
    private fun strIntMap(d: DataOutputStream, m: Map<String, Int>) { d.writeInt(m.size); for ((k, v) in m) { d.writeUTF(k); d.writeInt(v) } }
    private fun strIntMap(d: DataInputStream): Map<String, Int> = LinkedHashMap<String, Int>().also { m -> repeat(d.readInt()) { m[d.readUTF()] = d.readInt() } }
    private fun align(v: Long, a: Int) = (v + a - 1) / a * a
    private fun padTo(ch: FileChannel, a: Int) { val p = ch.position(); val target = align(p, a); if (target > p) ch.write(ByteBuffer.allocate((target - p).toInt())) }
}
