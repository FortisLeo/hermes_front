package com.rishitgoklani.hermes_front_mobile.domain.media

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Metadata of one active media session. Only what the player publishes; no lyrics. */
data class NowPlaying(val pkg: String, val app: String, val title: String, val artist: String, val album: String, val playing: Boolean)

/** Port over MediaSessionManager. Throws [IllegalStateException] when notification access is missing. */
interface MediaSessionPort {
    suspend fun active(): List<NowPlaying>
}

/** Use case: what is playing right now? Playing sessions first, sessions without a title ignored. Song lookup stays with Hermes (web search). */
class IdentifyNowPlaying(private val port: MediaSessionPort) {
    data class Result(val track: NowPlaying?, val others: List<NowPlaying>) {
        val searchQuery: String? get() = track?.let { listOf(it.title, it.artist).filter { s -> s.isNotBlank() }.joinToString(" ") }
        fun speakable(): String = track?.let { t -> if (t.artist.isBlank()) "${t.title} on ${t.app}" else "${t.title} by ${t.artist}" } ?: "Nothing is playing"
    }

    suspend operator fun invoke(): Result {
        val sessions = port.active().filter { it.title.isNotBlank() }.sortedByDescending { it.playing }
        return Result(sessions.firstOrNull(), sessions.drop(1))
    }
}

fun nowPlayingCapability(identify: IdentifyNowPlaying) = Capability("now_playing",
    "What music/audio is playing: title, artist, album and the app, from active media sessions (needs notification access). Look the song up with web_search using search_query.") {
    val r = try { identify() } catch (e: IllegalStateException) { return@Capability ActionResult.failed(e.message ?: "Notification access not enabled") }
    val t = r.track ?: return@Capability ActionResult.completed("Nothing is playing", buildJsonObject { put("playing", false) })
    ActionResult.completed(r.speakable(), buildJsonObject {
        put("playing", t.playing); put("title", t.title); put("artist", t.artist); put("album", t.album); put("app", t.app); put("package", t.pkg); put("search_query", r.searchQuery.orEmpty())
        put("others", buildJsonArray { r.others.forEach { add(buildJsonObject { put("title", it.title); put("artist", it.artist); put("app", it.app); put("playing", it.playing) }) } })
    })
}
