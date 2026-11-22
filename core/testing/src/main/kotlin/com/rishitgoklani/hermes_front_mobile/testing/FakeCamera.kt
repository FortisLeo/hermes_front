package com.rishitgoklani.hermes_front_mobile.testing

import com.rishitgoklani.hermes_front_mobile.domain.CameraFacing
import com.rishitgoklani.hermes_front_mobile.domain.CameraPort

class FakeCamera(private val bytes: ByteArray = byteArrayOf(1, 2, 3), private val failure: Throwable? = null) : CameraPort {
    val requests = mutableListOf<Pair<CameraFacing, Int>>()
    override suspend fun capture(facing: CameraFacing, maxBytes: Int): ByteArray {
        requests += facing to maxBytes; failure?.let { throw it }; return bytes
    }
}
