package com.rishitgoklani.hermes_front_mobile.domain.behavior

import com.rishitgoklani.hermes_front_mobile.domain.HermesGateway
import com.rishitgoklani.hermes_front_mobile.testing.FakeHermesGateway
import com.rishitgoklani.hermes_front_mobile.testing.FakeSpeechSynthesizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HandleShareTest {
    private class Rec : HermesGateway { var text = ""; var image: String? = null; override fun send(text: String, imageJpegBase64: String?): Flow<String> = flow { this@Rec.text = text; image = imageJpegBase64; emit("A recipe link. Want me to save it? Sure.") } }

    @Test fun textShareSendsPromptWithBodyAndSpeaksTwoSentences() = runBlocking {
        val g = Rec(); val tts = FakeSpeechSynthesizer(); val r = HandleShare(g, tts)("https://example.com/pasta", null, "Pasta")
        assertTrue(g.text.contains("this text") && g.text.contains("https://example.com/pasta") && g.text.contains("Pasta")); assertEquals(listOf("A recipe link.", "Want me to save it?"), tts.spoken); assertTrue(r.startsWith("A recipe"))
    }
    @Test fun imageShareAttachesFrame() = runBlocking {
        val g = Rec(); HandleShare(g, FakeSpeechSynthesizer())(null, "B64"); assertEquals("B64", g.image); assertTrue(g.text.contains("an image"))
    }
    @Test fun emptyShareDoesNothing() = runBlocking {
        val g = FakeHermesGateway(listOf("x")); assertEquals("nothing to share", HandleShare(g, FakeSpeechSynthesizer())("  ", null)); assertTrue(g.sent.isEmpty())
    }
    @Test fun veryLongTextIsTruncated() = runBlocking { val g = Rec(); HandleShare(g, FakeSpeechSynthesizer())("a".repeat(50_000), null); assertTrue(g.text.length < 8_300) }
    @Test fun expressionTagsNeverSpoken() = runBlocking {
        val tts = FakeSpeechSynthesizer(); HandleShare(FakeHermesGateway(listOf("<expr happy/>Nice find.")), tts)("link", null); assertEquals(listOf("Nice find."), tts.spoken)
    }
}
