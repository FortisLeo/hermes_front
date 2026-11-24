package com.rishitgoklani.hermes_front_mobile.domain.presence

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Room readings. Either may be null when the sensor is unavailable. [noiseDbfs] is 0 (loud) down to about -90 (silent). */
data class AmbientSnapshot(val lux: Float? = null, val noiseDbfs: Int? = null)

/** Port: one reading of the room (light sensor + mic level). */
fun interface AmbientSense { suspend fun read(): AmbientSnapshot }

enum class RoomProfile(val volumePercent: Int, val dimPercent: Int) {
    DarkQuiet(volumePercent = 25, dimPercent = 60),
    Normal(volumePercent = 55, dimPercent = 0),
    Noisy(volumePercent = 85, dimPercent = 0),
}

/** Pure mapping with hysteresis so the room does not flap between profiles. */
object RoomClassifier {
    fun classify(s: AmbientSnapshot, previous: RoomProfile): RoomProfile {
        val noisyAt = if (previous == RoomProfile.Noisy) -32 else -25
        val darkAt = if (previous == RoomProfile.DarkQuiet) 15f else 8f
        val quietBelow = -55
        if (s.noiseDbfs != null && s.noiseDbfs >= noisyAt) return RoomProfile.Noisy
        val dark = s.lux != null && s.lux < darkAt
        val quiet = s.noiseDbfs == null || s.noiseDbfs <= quietBelow
        return if (dark && quiet) RoomProfile.DarkQuiet else RoomProfile.Normal
    }
}

/** #337 Ambient awareness: a dark quiet room gets a dim screen and a soft voice, a noisy one a louder voice. Acts only on change. */
class AmbientAwareness(private val sense: AmbientSense, private val everyMs: Long = 60_000, private val clock: () -> Long = System::currentTimeMillis) : Behavior {
    override val id = "ambient-awareness"
    var profile = RoomProfile.Normal; private set
    private var lastRead = Long.MIN_VALUE / 2

    override suspend fun react(input: Input): List<Cue> {
        if (input !is Input.Tick) return emptyList()
        val now = clock()
        if (now - lastRead < everyMs) return emptyList()
        lastRead = now
        val next = RoomClassifier.classify(sense.read(), profile)
        if (next == profile) return emptyList()
        profile = next
        return listOf(
            Cue.Call("volume", buildJsonObject { put("level", next.volumePercent) }),
            Cue.Call("screen_dim", buildJsonObject { put("percent", next.dimPercent) }),
        )
    }
}
