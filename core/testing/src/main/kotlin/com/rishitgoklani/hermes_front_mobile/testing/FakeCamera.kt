package com.rishitgoklani.hermes_front_mobile.testing

import com.rishitgoklani.hermes_front_mobile.domain.CameraFacing
import com.rishitgoklani.hermes_front_mobile.domain.CameraPort

/** Returns [bytes], or successive entries of [frames] (the last one repeats) when given. */
class FakeCamera(private val bytes: ByteArray = byteArrayOf(1, 2, 3), private val failure: Throwable? = null, private val frames: List<ByteArray> = emptyList()) : CameraPort {
    val requests = mutableListOf<Pair<CameraFacing, Int>>()
    val longSides = mutableListOf<Int?>()
    override suspend fun capture(facing: CameraFacing, maxBytes: Int, longSidePx: Int?): ByteArray {
        requests += facing to maxBytes; longSides += longSidePx; failure?.let { throw it }
        return if (frames.isEmpty()) bytes else frames[minOf(requests.size - 1, frames.lastIndex)]
    }
}
