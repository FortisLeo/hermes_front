package com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf

import kotlinx.serialization.json.JsonObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Reads glTF accessors (with byteStride, normalization and sparse overrides) out of the BIN chunk. */
class Accessors(private val root: JsonObject, bin: ByteBuffer) {
    private val bin = bin.duplicate().order(ByteOrder.LITTLE_ENDIAN)
    private val accessors = root.a("accessors"); private val views = root.a("bufferViews")

    fun count(i: Int) = accessors.getOrNull(i).obj()?.i("count") ?: 0
    fun components(i: Int) = when (accessors.getOrNull(i).obj()?.s("type")) { "SCALAR" -> 1; "VEC2" -> 2; "VEC3" -> 3; "VEC4" -> 4; "MAT2" -> 4; "MAT3" -> 9; "MAT4" -> 16; else -> 1 }
    fun exists(i: Int?) = i != null && accessors.getOrNull(i) != null

    private fun compSize(t: Int) = when (t) { 5120, 5121 -> 1; 5122, 5123 -> 2; else -> 4 }

    private fun read(t: Int, at: Int, normalized: Boolean): Float = when (t) {
        5120 -> bin.get(at).toFloat().let { if (normalized) maxOf(it / 127f, -1f) else it }
        5121 -> (bin.get(at).toInt() and 0xFF).toFloat().let { if (normalized) it / 255f else it }
        5122 -> bin.getShort(at).toFloat().let { if (normalized) maxOf(it / 32767f, -1f) else it }
        5123 -> (bin.getShort(at).toInt() and 0xFFFF).toFloat().let { if (normalized) it / 65535f else it }
        5125 -> (bin.getInt(at).toLong() and 0xFFFFFFFFL).toFloat()
        else -> bin.getFloat(at)
    }

    /** All elements as floats (count * components). Missing bufferView means zeros, as the spec says. */
    fun floats(i: Int): FloatArray {
        val a = accessors.getOrNull(i).obj() ?: return FloatArray(0)
        val n = a.i("count") ?: 0; val comps = components(i); val t = a.i("componentType") ?: 5126; val norm = a.b("normalized") ?: false
        val out = FloatArray(n * comps)
        val view = a.i("bufferView")
        if (view != null) {
            val v = views.getOrNull(view).obj() ?: return out
            val start = (v.i("byteOffset") ?: 0) + (a.i("byteOffset") ?: 0); val size = compSize(t); val stride = (v.i("byteStride") ?: 0).takeIf { it > 0 } ?: (size * comps)
            for (e in 0 until n) for (c in 0 until comps) out[e * comps + c] = read(t, start + e * stride + c * size, norm)
        }
        a.o("sparse")?.let { sp ->
            val cnt = sp.i("count") ?: 0; val idx = sp.o("indices"); val vals = sp.o("values")
            if (idx != null && vals != null) {
                val iv = views.getOrNull(idx.i("bufferView") ?: -1).obj(); val vv = views.getOrNull(vals.i("bufferView") ?: -1).obj()
                if (iv != null && vv != null) {
                    val it = idx.i("componentType") ?: 5125; val ibase = (iv.i("byteOffset") ?: 0) + (idx.i("byteOffset") ?: 0); val vbase = (vv.i("byteOffset") ?: 0) + (vals.i("byteOffset") ?: 0); val size = compSize(t)
                    for (k in 0 until cnt) {
                        val target = read(it, ibase + k * compSize(it), false).toInt()
                        for (c in 0 until comps) if (target * comps + c < out.size) out[target * comps + c] = read(t, vbase + (k * comps + c) * size, norm)
                    }
                }
            }
        }
        return out
    }

    /** Integer elements (indices, joints); keeps full unsigned range. */
    fun ints(i: Int): IntArray {
        val a = accessors.getOrNull(i).obj() ?: return IntArray(0)
        val n = a.i("count") ?: 0; val comps = components(i); val t = a.i("componentType") ?: 5125
        val v = views.getOrNull(a.i("bufferView") ?: -1).obj() ?: return IntArray(n * comps)
        val start = (v.i("byteOffset") ?: 0) + (a.i("byteOffset") ?: 0); val size = compSize(t); val stride = (v.i("byteStride") ?: 0).takeIf { it > 0 } ?: (size * comps)
        val out = IntArray(n * comps)
        for (e in 0 until n) for (c in 0 until comps) { val at = start + e * stride + c * size; out[e * comps + c] = when (t) { 5121 -> bin.get(at).toInt() and 0xFF; 5123 -> bin.getShort(at).toInt() and 0xFFFF; 5125 -> bin.getInt(at); 5120 -> bin.get(at).toInt(); else -> bin.getShort(at).toInt() } }
        return out
    }

    /** The raw bytes of a bufferView (images are stored this way). */
    fun viewBytes(view: Int): ByteArray {
        val v = views.getOrNull(view).obj() ?: return ByteArray(0)
        val off = v.i("byteOffset") ?: 0; val len = v.i("byteLength") ?: 0
        if (off < 0 || len < 0 || off + len > bin.limit()) return ByteArray(0)
        return ByteArray(len).also { bin.duplicate().order(ByteOrder.LITTLE_ENDIAN).let { d -> d.position(off); d.get(it) } }
    }
}
