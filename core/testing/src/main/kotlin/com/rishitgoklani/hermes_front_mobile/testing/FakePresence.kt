package com.rishitgoklani.hermes_front_mobile.testing

import com.rishitgoklani.hermes_front_mobile.domain.presence.AmbientSense
import com.rishitgoklani.hermes_front_mobile.domain.presence.AmbientSnapshot
import com.rishitgoklani.hermes_front_mobile.domain.presence.AuditSource
import com.rishitgoklani.hermes_front_mobile.domain.presence.Cue
import com.rishitgoklani.hermes_front_mobile.domain.presence.CueSink
import com.rishitgoklani.hermes_front_mobile.domain.presence.QuietContext
import com.rishitgoklani.hermes_front_mobile.domain.presence.QuietContextPort
import com.rishitgoklani.hermes_front_mobile.domain.presence.UpcomingEvent
import com.rishitgoklani.hermes_front_mobile.domain.presence.UpcomingEvents

class FakeQuietContext(var context: QuietContext = QuietContext()) : QuietContextPort {
    override suspend fun current() = context
}

class FakeCueSink : CueSink {
    val cues = mutableListOf<Cue>()
    val spoken get() = cues.filterIsInstance<Cue.Speak>().map { it.text }
    override suspend fun apply(cue: Cue) { cues += cue }
}

class FakeUpcomingEvents(var event: UpcomingEvent? = null) : UpcomingEvents {
    override suspend fun next(withinMs: Long) = event?.takeIf { it.startMs >= 0 }
}

class FakeAmbientSense(var snapshot: AmbientSnapshot = AmbientSnapshot()) : AmbientSense {
    override suspend fun read() = snapshot
}

class FakeAuditSource(var lines: List<String> = emptyList()) : AuditSource {
    override fun lines() = lines
}
