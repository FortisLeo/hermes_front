package com.rishitgoklani.hermes_front_mobile.engine.vrm.math

import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Quaternions as FloatArray(4) = x, y, z, w. Functions write into [out] so the per-frame path allocates nothing. */
object Quat {
    fun identity() = floatArrayOf(0f, 0f, 0f, 1f)

    /** out = a * b (apply b first, then a). Safe when out aliases a or b. */
    fun mul(a: FloatArray, b: FloatArray, out: FloatArray) {
        val ax = a[0]; val ay = a[1]; val az = a[2]; val aw = a[3]; val bx = b[0]; val by = b[1]; val bz = b[2]; val bw = b[3]
        out[0] = aw * bx + ax * bw + ay * bz - az * by
        out[1] = aw * by - ax * bz + ay * bw + az * bx
        out[2] = aw * bz + ax * by - ay * bx + az * bw
        out[3] = aw * bw - ax * bx - ay * by - az * bz
    }

    fun conjugate(a: FloatArray, out: FloatArray) { out[0] = -a[0]; out[1] = -a[1]; out[2] = -a[2]; out[3] = a[3] }

    fun normalize(a: FloatArray) {
        val l = sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2] + a[3] * a[3])
        if (l < 1e-9f) { a[0] = 0f; a[1] = 0f; a[2] = 0f; a[3] = 1f } else { a[0] /= l; a[1] /= l; a[2] /= l; a[3] /= l }
    }

    /** Euler angles in radians, XYZ order (the same convention the agent's poses were authored in): q = qx * qy * qz. */
    fun fromEuler(x: Float, y: Float, z: Float, out: FloatArray) {
        val c1 = cos(x / 2); val c2 = cos(y / 2); val c3 = cos(z / 2); val s1 = sin(x / 2); val s2 = sin(y / 2); val s3 = sin(z / 2)
        out[0] = s1 * c2 * c3 + c1 * s2 * s3
        out[1] = c1 * s2 * c3 - s1 * c2 * s3
        out[2] = c1 * c2 * s3 + s1 * s2 * c3
        out[3] = c1 * c2 * c3 - s1 * s2 * s3
    }

    fun fromAxisAngle(ax: Float, ay: Float, az: Float, angle: Float, out: FloatArray) {
        val s = sin(angle / 2); out[0] = ax * s; out[1] = ay * s; out[2] = az * s; out[3] = cos(angle / 2)
    }

    fun slerp(a: FloatArray, b: FloatArray, t: Float, out: FloatArray) {
        var bx = b[0]; var by = b[1]; var bz = b[2]; var bw = b[3]
        var dot = a[0] * bx + a[1] * by + a[2] * bz + a[3] * bw
        if (dot < 0f) { dot = -dot; bx = -bx; by = -by; bz = -bz; bw = -bw }
        if (dot > 0.9995f) {
            out[0] = a[0] + (bx - a[0]) * t; out[1] = a[1] + (by - a[1]) * t; out[2] = a[2] + (bz - a[2]) * t; out[3] = a[3] + (bw - a[3]) * t
            normalize(out); return
        }
        val th = acos(dot); val st = sin(th); val wa = sin((1 - t) * th) / st; val wb = sin(t * th) / st
        out[0] = a[0] * wa + bx * wb; out[1] = a[1] * wa + by * wb; out[2] = a[2] * wa + bz * wb; out[3] = a[3] * wa + bw * wb
    }

    /** Rotates the vector (x, y, z) by [q] into [out] (3 floats). */
    fun rotate(q: FloatArray, x: Float, y: Float, z: Float, out: FloatArray) {
        val qx = q[0]; val qy = q[1]; val qz = q[2]; val qw = q[3]
        val tx = 2 * (qy * z - qz * y); val ty = 2 * (qz * x - qx * z); val tz = 2 * (qx * y - qy * x)
        out[0] = x + qw * tx + (qy * tz - qz * ty); out[1] = y + qw * ty + (qz * tx - qx * tz); out[2] = z + qw * tz + (qx * ty - qy * tx)
    }
}
