package com.rishitgoklani.hermes_front_mobile.testing

import com.rishitgoklani.hermes_front_mobile.domain.media.MediaSessionPort
import com.rishitgoklani.hermes_front_mobile.domain.media.NowPlaying

class FakeMediaSessionPort(var sessions: List<NowPlaying> = emptyList(), var denied: Boolean = false) : MediaSessionPort {
    override suspend fun active(): List<NowPlaying> { if (denied) error("Notification access not enabled"); return sessions }
}
