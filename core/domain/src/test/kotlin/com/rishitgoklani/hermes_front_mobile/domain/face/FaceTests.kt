package com.rishitgoklani.hermes_front_mobile.domain.face

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FaceTests {
    @Test fun parsesMoodReactionAndAliases() {
        assertEquals(Expr.MoodExpr(Mood.Happy, 0.8f), ExpressionNames.parse("happy", 0.8f))
        assertEquals(Expr.ReactionExpr(Reaction.ShakeNo, 1f), ExpressionNames.parse("shake no"))
        assertEquals(Expr.ReactionExpr(Reaction.ShakeNo, 1f), ExpressionNames.parse("shake_no"))
        assertEquals(Expr.ReactionExpr(Reaction.HeartEyes, 1f), ExpressionNames.parse("heart eyes"))
        assertEquals(Expr.ReactionExpr(Reaction.Glance, 1f), ExpressionNames.parse("glance at user"))
        assertNull(ExpressionNames.parse("flarb"))
    }
    @Test fun intensityClamped() = assertEquals(1f, (ExpressionNames.parse("sad", 7f) as Expr.MoodExpr).intensity, 0f)

    @Test fun stripsTagsAndKeepsSpeech() {
        val t = ExpressionTags.parse("Sure! <expr happy 0.8/> I can do that. <expr wink/>")
        assertEquals("Sure! I can do that.", t.text)
        assertEquals(listOf(Expr.MoodExpr(Mood.Happy, 0.8f), Expr.ReactionExpr(Reaction.Wink, 1f)), t.tags.map { it.second })
        assertEquals(6, t.tags.first().first)
    }
    @Test fun unknownTagsDroppedButStillStripped() {
        val t = ExpressionTags.parse("Hi <expr banana 1/> there"); assertEquals("Hi there", t.text); assertTrue(t.tags.isEmpty())
    }
    @Test fun noTagsUntouched() = assertEquals("Plain text.", ExpressionTags.parse("Plain text.").text)
    @Test fun malformedTagLeftAlone() = assertEquals("a <expr happy b", ExpressionTags.parse("a <expr happy b").text)

    @Test fun everyReactionAndMoodHasAFallback() {
        Reaction.entries.forEach { assertNotNull(ExpressionFallbacks.forReaction(it)) }
        Mood.entries.forEach { ExpressionFallbacks.forMood(it) }
        assertEquals(setOf(Effect.Tears), ExpressionFallbacks.forReaction(Reaction.Cry).effects); assertEquals(Mood.Sad, ExpressionFallbacks.forReaction(Reaction.Cry).mood)
        assertTrue(ExpressionFallbacks.forReaction(Reaction.Laugh).bounce)
    }

    @Test fun reactionAutoClearsMoodPersists() = runTest {
        val c = FaceController(backgroundScope, reactionMs = 1000)
        c.setMood(Mood.Happy, 0.7f); c.react(Reaction.Wink)
        assertEquals(Reaction.Wink, c.state.value.reaction)
        advanceTimeBy(1100); assertNull(c.state.value.reaction); assertEquals(Mood.Happy, c.state.value.mood)
    }
    @Test fun newReactionReplacesOldTimer() = runTest {
        val c = FaceController(backgroundScope, reactionMs = 1000)
        c.react(Reaction.Wink); advanceTimeBy(600); c.react(Reaction.Cry); advanceTimeBy(600)
        assertEquals(Reaction.Cry, c.state.value.reaction)
        advanceTimeBy(500); assertNull(c.state.value.reaction)
    }
    @Test fun tweaksAndLookAndReset() = runTest {
        val c = FaceController(backgroundScope)
        c.tweak { it.copy(colorHex = 0xFF00FF00, sizeScale = 1.3f) }; c.look(5f, -5f)
        assertEquals(0xFF00FF00, c.state.value.tweaks.colorHex); assertEquals(1f, c.state.value.lookX); assertEquals(-1f, c.state.value.lookY)
        c.resetTweaks(); assertNull(c.state.value.tweaks.colorHex)
    }
    @Test fun applyDispatchesByKind() = runTest {
        val c = FaceController(backgroundScope); c.apply(Expr.MoodExpr(Mood.Sad, 0.5f)); c.apply(Expr.ReactionExpr(Reaction.Yawn, 1f))
        assertEquals(Mood.Sad, c.state.value.mood); assertEquals(Reaction.Yawn, c.state.value.reaction)
    }
}
