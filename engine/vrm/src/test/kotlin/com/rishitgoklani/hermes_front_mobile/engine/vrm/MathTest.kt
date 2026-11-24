package com.rishitgoklani.hermes_front_mobile.engine.vrm

import com.rishitgoklani.hermes_front_mobile.engine.vrm.math.Mat4
import com.rishitgoklani.hermes_front_mobile.engine.vrm.math.Quat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

class MathTest {
    private val eps = 1e-5f

    @Test fun eulerZQuarterTurnMovesXToY() {
        val q = FloatArray(4); Quat.fromEuler(0f, 0f, (PI / 2).toFloat(), q)
        val v = FloatArray(3); Quat.rotate(q, 1f, 0f, 0f, v)
        assertArrayEquals(floatArrayOf(0f, 1f, 0f), v, eps)
    }

    @Test fun eulerIsXThenYThenZ() {
        // q = qx*qy*qz applies z first: a point on +X is turned to +Y by z, then x leaves it, then y leaves it.
        val q = FloatArray(4); Quat.fromEuler(0f, 0f, (PI / 2).toFloat(), q)
        val qx = FloatArray(4); Quat.fromEuler((PI / 2).toFloat(), 0f, 0f, qx)
        val both = FloatArray(4); Quat.mul(qx, q, both)
        val v = FloatArray(3); Quat.rotate(both, 1f, 0f, 0f, v)
        assertArrayEquals(floatArrayOf(0f, 0f, 1f), v, eps)
    }

    @Test fun conjugateUndoesRotation() {
        val q = FloatArray(4); Quat.fromEuler(0.3f, -0.7f, 1.1f, q)
        val c = FloatArray(4); Quat.conjugate(q, c); val id = FloatArray(4); Quat.mul(q, c, id)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f, 1f), id, eps)
    }

    @Test fun slerpHalfwayIsHalfTheAngle() {
        val a = Quat.identity(); val b = FloatArray(4); Quat.fromAxisAngle(0f, 1f, 0f, 1.0f, b)
        val m = FloatArray(4); Quat.slerp(a, b, 0.5f, m)
        val expect = FloatArray(4); Quat.fromAxisAngle(0f, 1f, 0f, 0.5f, expect)
        assertArrayEquals(expect, m, eps)
    }

    @Test fun slerpTakesTheShortWay() {
        val b = FloatArray(4); Quat.fromAxisAngle(0f, 0f, 1f, 0.4f, b); val nb = floatArrayOf(-b[0], -b[1], -b[2], -b[3])
        val m = FloatArray(4); Quat.slerp(Quat.identity(), nb, 0.5f, m)
        assertEquals(kotlin.math.sin(0.1f), m[2], 1e-4f)
    }

    @Test fun composeThenTransformPoint() {
        val m = FloatArray(16); val q = FloatArray(4); Quat.fromEuler(0f, 0f, (PI / 2).toFloat(), q)
        Mat4.compose(floatArrayOf(10f, 0f, 0f), q, floatArrayOf(2f, 2f, 2f), m)
        val p = FloatArray(3); Mat4.transformPoint(m, 0, 1f, 0f, 0f, p)
        assertArrayEquals(floatArrayOf(10f, 2f, 0f), p, eps)
    }

    @Test fun mulAppliesRightFirst() {
        val t = Mat4.identity(); t[12] = 5f
        val s = Mat4.identity(); s[0] = 2f
        val out = FloatArray(16); Mat4.mul(t, 0, s, 0, out)
        val p = FloatArray(3); Mat4.transformPoint(out, 0, 1f, 0f, 0f, p)
        assertEquals(7f, p[0], eps)
    }

    @Test fun lookAtPutsTargetInFrontOfCamera() {
        val v = FloatArray(16); Mat4.lookAt(0f, 0f, 5f, 0f, 0f, 0f, v)
        val p = FloatArray(3); Mat4.transformPoint(v, 0, 0f, 0f, 0f, p)
        assertArrayEquals(floatArrayOf(0f, 0f, -5f), p, eps)
    }
}
