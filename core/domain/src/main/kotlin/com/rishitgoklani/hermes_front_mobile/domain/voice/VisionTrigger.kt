package com.rishitgoklani.hermes_front_mobile.domain.voice

import com.rishitgoklani.hermes_front_mobile.domain.CameraFacing

/** Decides when a spoken request needs a camera frame, and which camera. */
object VisionTrigger {
    private val triggers = listOf(
        "what is this", "what's this", "what is that", "what's that", "what am i holding", "what's in my hand", "what is in my hand", "in my hand",
        "look at", "take a look", "what do you see", "what can you see", "can you see", "read this", "read the label", "how many", "describe what", "who is there", "is anyone there",
    )
    private val backWords = listOf("behind", "room", "around you", "back camera", "rear camera", "the door", "window")

    fun needsVision(text: String): Boolean { val t = text.lowercase(); return triggers.any { t.contains(it) } }
    fun facing(text: String): CameraFacing { val t = text.lowercase(); return if (backWords.any { t.contains(it) }) CameraFacing.Back else CameraFacing.Front }
}
