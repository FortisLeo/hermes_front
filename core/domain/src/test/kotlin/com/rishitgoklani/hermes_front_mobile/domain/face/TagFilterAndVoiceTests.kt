package com.rishitgoklani.hermes_front_mobile.domain.face

import com.rishitgoklani.hermes_front_mobile.domain.RunVoiceTurn
import com.rishitgoklani.hermes_front_mobile.domain.SttEvent
import com.rishitgoklani.hermes_front_mobile.domain.VoiceEvent
import com.rishitgoklani.hermes_front_mobile.testing.FakeHermesGateway
import com.rishitgoklani.hermes_front_mobile.testing.FakeSpeechSynthesizer
import com.rishitgoklani.hermes_front_mobile.testing.FakeSpeechToText
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TagFilterAndVoiceTests {
    private fun run(vararg deltas: String): Pair<List<TagFilter.Segment>, TagFilter> { val f = TagFilter(); return (deltas.flatMap { f.feed(it) } + f.flush()) to f }
    private fun text(segs: List<TagFilter.Segment>) = segs.filterIsInstance<TagFilter.Segment.Text>().joinToString("") { it.text }

    @Test fun tagWithinOneDelta() { val (s, _) = run("Hi <expr happy 0.8/> there"); assertEquals("Hi  there", text(s)); assertEquals(Expr.MoodExpr(Mood.Happy, 0.8f), (s.filterIsInstance<TagFilter.Segment.Tag>().single()).expr) }
    @Test fun tagSplitAcrossDeltas() { val (s, _) = run("Hi <ex", "pr wink", "/> ok"); assertEquals("Hi  ok", text(s)); assertEquals(1, s.filterIsInstance<TagFilter.Segment.Tag>().size) }
    @Test fun loneLessThanPassesThrough() { val (s, _) = run("a < b and 2<3 "); assertEquals("a < b and 2<3 ", text(s)) }
    @Test fun unknownTagDropped() { val (s, _) = run("x <expr banana/> y"); assertEquals("x  y", text(s)); assertTrue(s.none { it is TagFilter.Segment.Tag }) }
    @Test fun otherHtmlLeftAlone() { val (s, _) = run("use <b>bold</b>"); assertEquals("use <b>bold</b>", text(s)) }
    @Test fun incompleteTagAtEndFlushedAsText() { val (s, _) = run("hello <expr hap"); assertEquals("hello <expr hap", text(s)) }

    @Test fun voiceTurnStripsTagsFromSpeechAndEmitsExpressionBeforeSentence() = runBlocking {
        val tts = FakeSpeechSynthesizer()
        val events = RunVoiceTurn(FakeSpeechToText(listOf(SttEvent.Final("hi"))), FakeHermesGateway(listOf("<expr happy 0.9/>Hello there. ", "Bye <expr wink/>now.")), tts)().toList()
        assertEquals(listOf("Hello there.", "Bye now."), tts.spoken)
        val firstExpr = events.indexOfFirst { it is VoiceEvent.Expression }; val firstSpeak = events.indexOfFirst { it is VoiceEvent.State && it.state == com.rishitgoklani.hermes_front_mobile.domain.BotState.Speaking }
        assertTrue(firstExpr in 0..firstSpeak + 1)
        assertEquals(2, events.filterIsInstance<VoiceEvent.Expression>().size)
        assertTrue(events.filterIsInstance<VoiceEvent.ReplyDelta>().none { it.text.contains("<expr") })
    }
    @Test fun trailingTagStillEmitted() = runBlocking {
        val events = RunVoiceTurn(FakeSpeechToText(listOf(SttEvent.Final("hi"))), FakeHermesGateway(listOf("Done. <expr celebrate/>")), FakeSpeechSynthesizer())().toList()
        assertEquals(1, events.filterIsInstance<VoiceEvent.Expression>().size)
    }
}
