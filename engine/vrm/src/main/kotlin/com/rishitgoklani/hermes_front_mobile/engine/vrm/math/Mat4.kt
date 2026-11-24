package com.rishitgoklani.hermes_front_mobile.engine.vrm.math

import kotlin.math.tan

/** 4x4 matrices as FloatArray(16), column-major (element [col * 4 + row]), exactly what OpenGL expects. */
object Mat4 {
    fun identity() = FloatArray(16).also { it[0] = 1f; it[5] = 1f; it[10] = 1f; it[15] = 1f }

    fun setIdentity(m: FloatArray, o: Int = 0) { for (i in 0 until 16) m[o + i] = 0f; m[o] = 1f; m[o + 5] = 1f; m[o + 10] = 1f; m[o + 15] = 1f }

    /** out = translation * rotation(q) * scale. */
    fun compose(t: FloatArray, q: FloatArray, s: FloatArray, out: FloatArray, o: Int = 0) {
        val x = q[0]; val y = q[1]; val z = q[2]; val w = q[3]
        val x2 = x + x; val y2 = y + y; val z2 = z + z
        val xx = x * x2; val xy = x * y2; val xz = x * z2; val yy = y * y2; val yz = y * z2; val zz = z * z2; val wx = w * x2; val wy = w * y2; val wz = w * z2
        out[o] = (1 - (yy + zz)) * s[0]; out[o + 1] = (xy + wz) * s[0]; out[o + 2] = (xz - wy) * s[0]; out[o + 3] = 0f
        out[o + 4] = (xy - wz) * s[1]; out[o + 5] = (1 - (xx + zz)) * s[1]; out[o + 6] = (yz + wx) * s[1]; out[o + 7] = 0f
        out[o + 8] = (xz + wy) * s[2]; out[o + 9] = (yz - wx) * s[2]; out[o + 10] = (1 - (xx + yy)) * s[2]; out[o + 11] = 0f
        out[o + 12] = t[0]; out[o + 13] = t[1]; out[o + 14] = t[2]; out[o + 15] = 1f
    }

    /** out = a * b, where a lives at [ao] in its array, b at [bo], result at [oo]. out must not alias a or b. */
    fun mul(a: FloatArray, ao: Int, b: FloatArray, bo: Int, out: FloatArray, oo: Int = 0) {
        for (c in 0 until 4) for (r in 0 until 4) {
            out[oo + c * 4 + r] = a[ao + r] * b[bo + c * 4] + a[ao + 4 + r] * b[bo + c * 4 + 1] + a[ao + 8 + r] * b[bo + c * 4 + 2] + a[ao + 12 + r] * b[bo + c * 4 + 3]
        }
    }

    fun perspective(fovYRad: Float, aspect: Float, near: Float, far: Float, out: FloatArray) {
        val f = 1f / tan(fovYRad / 2); for (i in 0 until 16) out[i] = 0f
        out[0] = f / aspect; out[5] = f; out[10] = (far + near) / (near - far); out[11] = -1f; out[14] = 2 * far * near / (near - far)
    }

    /** View matrix looking from [eye] to [target] with +Y up. */
    fun lookAt(ex: Float, ey: Float, ez: Float, tx: Float, ty: Float, tz: Float, out: FloatArray) {
        var fx = tx - ex; var fy = ty - ey; var fz = tz - ez; val fl = kotlin.math.sqrt(fx * fx + fy * fy + fz * fz); fx /= fl; fy /= fl; fz /= fl
        var sx = fy * 0f - fz * 1f; var sy = fz * 0f - fx * 0f; var sz = fx * 1f - fy * 0f
        val sl = kotlin.math.sqrt(sx * sx + sy * sy + sz * sz); sx /= sl; sy /= sl; sz /= sl
        val ux = sy * fz - sz * fy; val uy = sz * fx - sx * fz; val uz = sx * fy - sy * fx
        out[0] = sx; out[1] = ux; out[2] = -fx; out[3] = 0f; out[4] = sy; out[5] = uy; out[6] = -fy; out[7] = 0f
        out[8] = sz; out[9] = uz; out[10] = -fz; out[11] = 0f
        out[12] = -(sx * ex + sy * ey + sz * ez); out[13] = -(ux * ex + uy * ey + uz * ez); out[14] = fx * ex + fy * ey + fz * ez; out[15] = 1f
    }

    /** Transforms the point (x, y, z) by the matrix at [o] into out[0..2]. */
    fun transformPoint(m: FloatArray, o: Int, x: Float, y: Float, z: Float, out: FloatArray) {
        out[0] = m[o] * x + m[o + 4] * y + m[o + 8] * z + m[o + 12]
        out[1] = m[o + 1] * x + m[o + 5] * y + m[o + 9] * z + m[o + 13]
        out[2] = m[o + 2] * x + m[o + 6] * y + m[o + 10] * z + m[o + 14]
    }
}
