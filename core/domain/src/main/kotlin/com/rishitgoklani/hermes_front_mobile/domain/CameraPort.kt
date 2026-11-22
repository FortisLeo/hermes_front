package com.rishitgoklani.hermes_front_mobile.domain

enum class CameraFacing { Front, Back }

/** Port: grab one still frame as JPEG bytes, already shrunk to fit [maxBytes]. */
interface CameraPort {
    suspend fun capture(facing: CameraFacing, maxBytes: Int): ByteArray
}
