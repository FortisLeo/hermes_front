package com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf

/** IEEE half-float conversion (pure JVM; android.util.Half is not available here). Used to halve UV memory on the GPU. */
object Half {
    fun fromFloat(f: Float): Short {
        val bits = f.toRawBits(); val sign = (bits ushr 16) and 0x8000; val exp = ((bits ushr 23) and 0xFF) - 127 + 15; var mant = bits and 0x7FFFFF
        if (((bits ushr 23) and 0xFF) == 0xFF) return (sign or 0x7C00 or (if (mant != 0) 0x200 else 0)).toShort()
        if (exp >= 31) return (sign or 0x7C00).toShort()
        if (exp <= 0) {
            if (exp < -10) return sign.toShort()
            mant = mant or 0x800000
            return (sign or ((mant + (1 shl (13 - exp))) shr (14 - exp))).toShort()
        }
        return (sign or ((exp shl 10) + ((mant + 0x1000) shr 13))).toShort()
    }

    fun toFloat(h: Short): Float {
        val v = h.toInt() and 0xFFFF; val sign = if (v and 0x8000 != 0) -1f else 1f; val exp = (v shr 10) and 0x1F; val mant = v and 0x3FF
        return when (exp) {
            0 -> sign * mant * (1f / 16384f / 1024f)
            31 -> if (mant == 0) sign * Float.POSITIVE_INFINITY else Float.NaN
            else -> sign * (1f + mant / 1024f) * Math.pow(2.0, (exp - 15).toDouble()).toFloat()
        }
    }
}
