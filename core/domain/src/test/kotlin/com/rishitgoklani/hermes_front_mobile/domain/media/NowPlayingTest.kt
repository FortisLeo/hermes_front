package com.rishitgoklani.hermes_front_mobile.domain.media

import com.rishitgoklani.hermes_front_mobile.testing.FakeMediaSessionPort
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingTest {
    private fun np(title: String, artist: String = "A", playing: Boolean = true, app: String = "Spotify") = NowPlaying("p.$app", app, title, artist, "", playing)

    @Test fun prefersPlayingSessionAndIgnoresUntitled() = runTest {
        val r = IdentifyNowPlaying(FakeMediaSessionPort(listOf(np("Paused", playing = false), np(""), np("Live")))).invoke()
        assertEquals("Live", r.track?.title); assertEquals(1, r.others.size)
    }

    @Test fun speakableAndSearchQuery() = runTest {
        val r = IdentifyNowPlaying(FakeMediaSessionPort(listOf(np("Song", "Band")))).invoke()
        assertEquals("Song by Band", r.speakable()); assertEquals("Song Band", r.searchQuery)
        val noArtist = IdentifyNowPlaying(FakeMediaSessionPort(listOf(np("Podcast", "")))).invoke()
        assertEquals("Podcast on Spotify", noArtist.speakable()); assertEquals("Podcast", noArtist.searchQuery)
    }

    @Test fun nothingPlaying() = runTest {
        val r = IdentifyNowPlaying(FakeMediaSessionPort()).invoke()
        assertNull(r.track); assertEquals("Nothing is playing", r.speakable()); assertNull(r.searchQuery)
    }

    @Test fun toolReportsTrackAndSearchQuery() = runTest {
        val res = nowPlayingCapability(IdentifyNowPlaying(FakeMediaSessionPort(listOf(np("Song", "Band"))))).executor.execute(JsonObject(emptyMap()))
        assertEquals("completed", res.status); assertEquals("Song Band", res.data!!["search_query"]!!.jsonPrimitive.content)
    }

    @Test fun toolFailsWithoutNotificationAccess() = runTest {
        val res = nowPlayingCapability(IdentifyNowPlaying(FakeMediaSessionPort(denied = true))).executor.execute(JsonObject(emptyMap()))
        assertEquals("failed", res.status); assertTrue(res.message.contains("access"))
    }

    @Test fun toolCompletesWhenIdle() = runTest {
        val res = nowPlayingCapability(IdentifyNowPlaying(FakeMediaSessionPort())).executor.execute(JsonObject(emptyMap()))
        assertEquals("completed", res.status); assertEquals("false", res.data!!["playing"]!!.jsonPrimitive.content)
    }
}
