package com.rishitgoklani.hermes_front_mobile.domain.sense

/** Face centre in the camera frame, each axis 0..1 (0,0 = top-left of the unmirrored image). */
data class FacePoint(val x: Float, val y: Float)

/** Port: where is the (first) face in a frame. Position only, no identity. */
fun interface FaceLocator { suspend fun locate(jpeg: ByteArray): FacePoint? }

/** Pure mapping from a front-camera face position to eye gaze (-1..1). The front image is mirrored, so x is flipped. */
object GazeMapper {
    fun toGaze(p: FacePoint): Pair<Float, Float> = (1f - 2f * p.x).coerceIn(-1f, 1f) to (2f * p.y - 1f).coerceIn(-1f, 1f)
}
