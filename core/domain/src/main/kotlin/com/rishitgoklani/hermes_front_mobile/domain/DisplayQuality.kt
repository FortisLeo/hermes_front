package com.rishitgoklani.hermes_front_mobile.domain

/** How much the avatar renderer may spend on looks. Auto lets the renderer decide from the device. */
enum class DisplayQuality(val label: String) {
    Auto("Auto"), Low("Low"), High("High");

    companion object { fun parse(raw: String?): DisplayQuality = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: Auto }
}
