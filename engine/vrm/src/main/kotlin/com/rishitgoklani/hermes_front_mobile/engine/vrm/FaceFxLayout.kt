package com.rishitgoklani.hermes_front_mobile.engine.vrm

import com.rishitgoklani.hermes_front_mobile.engine.vrm.math.Mat4
import com.rishitgoklani.hermes_front_mobile.engine.vrm.math.Quat
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Where the on-face effects (blush, tears, sweat drop, anger mark) sit in 3D, recomputed every frame from the posed skeleton.
 *
 * The anchor is the model's own eyes: cheeks hang below each eye, tears start at the lower lid, and every size is a fraction of the distance
 * between the eyes, so the effects fit a chibi and a realistic head alike. The decals turn with the head bone, so they tilt, nod and hide when
 * the head turns away. Everything is in draw space (the space after [Rig.modelMatrix], where every model faces +Z toward the camera).
 *
 * Models without eye bones fall back to the head bone plus the model's height. Allocation-free per frame.
 */
class FaceFxLayout(private val rig: Rig) {
    /** One quad. [matrix] maps the unit square [-1, 1]^2 to the decal's place in draw space. */
    class Decal(val kind: Int, val side: Float, val surface: Boolean) {
        val matrix = FloatArray(16); var weight = 0f
        /** Half extents in units of the eye distance. */
        var halfW = 0f; var halfH = 0f
    }

    companion object {
        /** Used when the skin cannot be measured: how far in front of the eyeball centre a skin decal floats (eye distances). */
        const val SKIN_FORWARD = 0.26f
        /** A skin decal floats this much (eye distances) in front of the measured skin so the cheek's curve does not bury its edges. */
        const val SKIN_MARGIN = 0.08f
        /** Comic symbols (sweat, anger) float at least this far in front of the eyes, and always clear of the skin, so hair never hides them. */
        const val SYMBOL_FORWARD = 0.7f
        const val FALLBACK_EYE_DISTANCE = 0.039f   // of the model's height
        /** Eye bones closer together than this fraction of the head's height are not where the drawn eyes are (stylised faces keep them near the nose bridge). */
        const val MIN_BONE_SPACING = 0.2f
        /** Eye spacing used instead, as a fraction of the head's height (bone at the neck top to the top of the model). */
        const val STYLISED_SPACING = 0.33f
    }

    // Order matters: the renderer draws in this order. Cheeks and tears sit on the skin (depth tested), symbols float on top.
    val decals: Array<Decal> = arrayOf(
        Decal(FaceMixer.FX_BLUSH, -1f, true).also { it.halfW = 0.44f; it.halfH = 0.27f },
        Decal(FaceMixer.FX_BLUSH, 1f, true).also { it.halfW = 0.44f; it.halfH = 0.27f },
        Decal(FaceMixer.FX_TEARS, -1f, true).also { it.halfW = 0.16f; it.halfH = 0.7f },
        Decal(FaceMixer.FX_TEARS, 1f, true).also { it.halfW = 0.16f; it.halfH = 0.7f },
        Decal(FaceMixer.FX_SWEAT, 1f, false).also { it.halfW = 0.2f; it.halfH = 0.32f },
        Decal(FaceMixer.FX_ANGER, 1f, false).also { it.halfW = 0.3f; it.halfH = 0.3f },
    )

    private val headNode = rig.asset.humanoid["head"] ?: -1
    private val eyeA = rig.asset.humanoid["leftEye"] ?: -1; private val eyeB = rig.asset.humanoid["rightEye"] ?: -1
    private val hasEyes = eyeA >= 0 && eyeB >= 0
    private val height = (rig.asset.boundsMax[1] - rig.asset.boundsMin[1]).coerceAtLeast(0.3f)

    /** The head's axes in draw space, unit length. */
    val right = FloatArray(3); val up = FloatArray(3); val forward = FloatArray(3)
    /** Eye positions in draw space; [eyeViewerLeft] is the one on the viewer's left. */
    val eyeViewerLeft = FloatArray(3); val eyeViewerRight = FloatArray(3)
    var eyeDistance = 0f; private set
    /** 0..1: how much the face points at the camera (+Z in draw space). Effects fade out as the head turns away. */
    var facing = 1f; private set

    /** How far in front of the eyes the skin below them is, in eye distances, measured once from the model's own vertices. */
    @Volatile private var measuredSkin = Float.NaN
    /** The measured value once [prepare] ran, the default before; never computed on the render thread. */
    private val skinForward: Float get() = measuredSkin.let { if (it.isNaN()) SKIN_FORWARD else it }

    /** Factor that turns the eye bones' spacing into the spacing of the drawn eyes: 1 unless the bones are implausibly close together. */
    private val spacingScale: Float by lazy {
        if (!hasEyes) return@lazy 1f
        val rest = VrmParser.restWorldMatrices(rig.asset.nodes)
        fun at(n: Int, k: Int) = rest[n * 16 + 12 + k]
        val bones = sqrt((at(eyeA, 0) - at(eyeB, 0)).let { it * it } + (at(eyeA, 1) - at(eyeB, 1)).let { it * it } + (at(eyeA, 2) - at(eyeB, 2)).let { it * it })
        val headHeight = rig.asset.boundsMax[1] - at(headNode, 1)
        if (bones > 1e-4f && headHeight > 0.05f && bones < MIN_BONE_SPACING * headHeight) STYLISED_SPACING * headHeight / bones else 1f
    }

    private val q = FloatArray(4); private val qr = FloatArray(4); private val qd = FloatArray(4)
    private val pa = FloatArray(3); private val pb = FloatArray(3); private val tmp = FloatArray(3)

    /**
     * Measures the skin depth (a scan over the model's vertices). Call it on a background thread once the model is on screen: it must never
     * run on the render thread (it stalled the first frame for most of a second on a 300k-vertex model), and nothing needs it before an
     * effect shows. Until it has run, skin decals use [SKIN_FORWARD].
     */
    fun prepare() { if (measuredSkin.isNaN()) measuredSkin = if (hasEyes) measureSkinForward() else SKIN_FORWARD }

    /** Recomputes the head frame, then every decal's matrix and fade. Call after [Rig.update] / the spring step. */
    fun update(mixer: FaceMixer) {
        if (headNode < 0) { for (d in decals) d.weight = 0f; return }
        rig.worldRotation(headNode, q); rig.restWorldRotation(headNode, qr); Quat.conjugate(qr, qr); Quat.mul(q, qr, qd)
        if (rig.asset.isVrm0) { qd[0] = -qd[0]; qd[2] = -qd[2] }          // VRM 0.x models are turned half way round about Y to face the camera
        Quat.rotate(qd, 1f, 0f, 0f, right); Quat.rotate(qd, 0f, 1f, 0f, up); Quat.rotate(qd, 0f, 0f, 1f, forward)

        if (hasEyes) {
            rig.worldPosition(eyeA, tmp); Mat4.transformPoint(rig.modelMatrix, 0, tmp[0], tmp[1], tmp[2], pa)
            rig.worldPosition(eyeB, tmp); Mat4.transformPoint(rig.modelMatrix, 0, tmp[0], tmp[1], tmp[2], pb)
            val d = dist(pa, pb)
            if (d in 0.01f..0.2f) {
                val scale = spacingScale; eyeDistance = d * scale; place(pa, pb)
                if (scale != 1f) { for (k in 0..2) { val c = (pa[k] + pb[k]) * 0.5f; eyeViewerLeft[k] = c - right[k] * eyeDistance * 0.5f; eyeViewerRight[k] = c + right[k] * eyeDistance * 0.5f } }
                return finish(mixer)
            }
        }
        // No usable eye bones: eyes sit a fixed fraction of the model's height above the head bone, a bit forward.
        rig.worldPosition(headNode, tmp); Mat4.transformPoint(rig.modelMatrix, 0, tmp[0], tmp[1], tmp[2], pa)
        eyeDistance = FALLBACK_EYE_DISTANCE * height
        val e = eyeDistance
        for (k in 0..2) { val c = pa[k] + up[k] * e * 1.9f + forward[k] * e * 1.0f; eyeViewerLeft[k] = c - right[k] * e * 0.5f; eyeViewerRight[k] = c + right[k] * e * 0.5f }
        finish(mixer)
    }

    private fun place(a: FloatArray, b: FloatArray) {
        val aIsLeft = (a[0] - b[0]) * right[0] + (a[1] - b[1]) * right[1] + (a[2] - b[2]) * right[2] < 0f
        System.arraycopy(if (aIsLeft) a else b, 0, eyeViewerLeft, 0, 3); System.arraycopy(if (aIsLeft) b else a, 0, eyeViewerRight, 0, 3)
    }

    private fun finish(mixer: FaceMixer) {
        val e = eyeDistance
        facing = smooth(0.1f, 0.55f, forward[2])
        val surface = skinForward; val symbol = maxOf(SYMBOL_FORWARD, surface + 0.5f)
        for (d in decals) {
            d.weight = mixer.decalWeight(d.kind) * facing
            if (d.weight <= 0.003f) continue
            val m = d.matrix; val eye = if (d.side < 0f) eyeViewerLeft else eyeViewerRight
            val forwardBy: Float; val upBy: Float; val outBy: Float
            when (d.kind) {
                FaceMixer.FX_BLUSH -> { upBy = -0.62f; outBy = 0f; forwardBy = surface }
                FaceMixer.FX_TEARS -> { upBy = -0.9f; outBy = 0f; forwardBy = surface }     // quad centre, so the stream starts near the lower lid
                FaceMixer.FX_SWEAT -> { upBy = 1.05f; outBy = 1.3f; forwardBy = symbol }
                else -> { upBy = 1.35f; outBy = 1.1f; forwardBy = symbol }
            }
            // Symbols hang off the viewer's right temple, measured from the middle of the eyes; cheeks and tears from their own eye.
            val ox: Float; val oy: Float; val oz: Float
            if (d.surface) { ox = eye[0]; oy = eye[1]; oz = eye[2] }
            else { ox = (eyeViewerLeft[0] + eyeViewerRight[0]) * 0.5f; oy = (eyeViewerLeft[1] + eyeViewerRight[1]) * 0.5f; oz = (eyeViewerLeft[2] + eyeViewerRight[2]) * 0.5f }
            val px = ox + right[0] * outBy * e + up[0] * upBy * e + forward[0] * forwardBy * e
            val py = oy + right[1] * outBy * e + up[1] * upBy * e + forward[1] * forwardBy * e
            val pz = oz + right[2] * outBy * e + up[2] * upBy * e + forward[2] * forwardBy * e
            val sx = d.halfW * e; val sy = d.halfH * e
            m[0] = right[0] * sx; m[1] = right[1] * sx; m[2] = right[2] * sx; m[3] = 0f
            m[4] = up[0] * sy; m[5] = up[1] * sy; m[6] = up[2] * sy; m[7] = 0f
            m[8] = forward[0]; m[9] = forward[1]; m[10] = forward[2]; m[11] = 0f
            m[12] = px; m[13] = py; m[14] = pz; m[15] = 1f
        }
    }

    /**
     * Looks at the model's vertices in the rest pose around the spot below each eye and returns how far the frontmost one is in front of the eye
     * (eye distances) plus [SKIN_MARGIN]. Eye bones sit inside the eyeball and every model puts the skin a different distance in front of them, so a
     * fixed offset buries the decal inside the face on some models. Falls back to [SKIN_FORWARD] when no vertices are found.
     */
    private fun measureSkinForward(): Float {
        val rest = VrmParser.restWorldMatrices(rig.asset.nodes)
        fun at(node: Int, k: Int) = rest[node * 16 + 12 + k]
        val bones = sqrt((at(eyeA, 0) - at(eyeB, 0)).let { it * it } + (at(eyeA, 1) - at(eyeB, 1)).let { it * it } + (at(eyeA, 2) - at(eyeB, 2)).let { it * it })
        if (bones < 0.01f || bones > 0.2f) return SKIN_FORWARD
        val sign = if (rig.asset.isVrm0) -1f else 1f
        val e = bones * spacingScale
        val cx = (at(eyeA, 0) + at(eyeB, 0)) * 0.5f; val cy = (at(eyeA, 1) + at(eyeB, 1)) * 0.5f; val cz = (at(eyeA, 2) + at(eyeB, 2)) * 0.5f * sign
        var best = Float.NEGATIVE_INFINITY; val out = FloatArray(3); val sides = floatArrayOf(-1f, 1f)
        // Primitives of one mesh usually share a single vertex array (VRoid splits a mesh by material): scan each array once, bulk-read.
        val seen = java.util.IdentityHashMap<java.nio.ByteBuffer, Boolean>(); var xyz = FloatArray(0)
        for (p in rig.asset.primitives) {
            if (p.skinned && seen.put(p.positions, true) != null) continue
            val n = p.vertexCount * 3; if (xyz.size < n) xyz = FloatArray(n)
            p.positions.duplicate().order(java.nio.ByteOrder.LITTLE_ENDIAN).also { it.position(0) }.asFloatBuffer().get(xyz, 0, n)
            for (v in 0 until p.vertexCount) {
                var x = xyz[v * 3]; var y = xyz[v * 3 + 1]; var z = xyz[v * 3 + 2]
                if (!p.skinned) { Mat4.transformPoint(rest, p.node * 16, x, y, z, out); x = out[0]; y = out[1]; z = out[2] }
                for (side in sides) {
                    val ex = cx + side * 0.5f * e
                    if (abs(x - ex) > 0.3f * e || abs(y - (cy - 0.62f * e)) > 0.2f * e) continue
                    val f = z * sign; if (f < cz - 0.5f * e || f > cz + 1.5f * e) continue
                    if (f > best) best = f
                }
            }
        }
        if (best == Float.NEGATIVE_INFINITY) return SKIN_FORWARD
        return ((best - cz) / e + SKIN_MARGIN).coerceIn(0.05f, 1.2f)
    }

    /** How far in front of the eyes skin decals are placed (eye distances); see [measureSkinForward]. */
    val skinForwardForTests: Float get() = skinForward

    private fun dist(a: FloatArray, b: FloatArray): Float { val x = a[0] - b[0]; val y = a[1] - b[1]; val z = a[2] - b[2]; return sqrt(x * x + y * y + z * z) }
    private fun smooth(e0: Float, e1: Float, x: Float): Float { val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f); return t * t * (3f - 2f * t) }

    /** One line for logs: eye distance, measured skin depth, facing and where each visible decal is. */
    fun debugLine(): String = "eyes=${"%.3f".format(eyeDistance)} hasEyes=$hasEyes skin=${"%.2f".format(skinForward)} facing=${"%.2f".format(facing)} " +
        decals.joinToString(" ") { d -> "k${d.kind}${if (d.side < 0) "L" else "R"}:w=${"%.2f".format(d.weight)} p=(${"%.3f".format(d.matrix[12])},${"%.3f".format(d.matrix[13])},${"%.3f".format(d.matrix[14])})" }

    /** Used by tests to see the decal for a kind and side. */
    fun decal(kind: Int, side: Float): Decal? = decals.firstOrNull { it.kind == kind && abs(it.side - side) < 0.5f }
}
