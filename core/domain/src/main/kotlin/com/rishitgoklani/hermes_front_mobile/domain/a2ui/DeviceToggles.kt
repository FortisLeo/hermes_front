package com.rishitgoklani.hermes_front_mobile.domain.a2ui

/** Maps a DeviceToggle's `device` + desired state to a bridge action string. Only tools that work without root are offered. */
object DeviceToggles {
    val supported = listOf("torch", "ambient", "wake", "dim", "mute")

    fun action(device: String, on: Boolean): String? = when (device.lowercase()) {
        "torch" -> """tool:torch {"on":$on}"""
        "ambient" -> """tool:ambient_mode {"action":"${if (on) "on" else "off"}"}"""
        "wake" -> """tool:wake_control {"action":"${if (on) "on" else "off"}"}"""
        "dim" -> """tool:screen_dim {"percent":${if (on) 50 else 0}}"""
        "mute" -> """tool:stream_volume {"stream":"music","mute":$on}"""
        else -> null
    }
}
