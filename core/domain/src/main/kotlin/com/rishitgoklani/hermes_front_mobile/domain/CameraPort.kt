package com.rishitgoklani.hermes_front_mobile.domain

enum class CameraFacing { Front, Back }

/**
 * Port: grab one still frame as JPEG bytes, already shrunk to fit [maxBytes].
 * [longSidePx] overrides the adapter's default long edge (OCR and document scans want more pixels than a chat thumbnail).
 */
interface CameraPort {
    suspend fun capture(facing: CameraFacing, maxBytes: Int, longSidePx: Int? = null): ByteArray
}
