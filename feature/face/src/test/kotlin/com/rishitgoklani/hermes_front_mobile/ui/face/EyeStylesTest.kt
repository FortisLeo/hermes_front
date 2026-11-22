package com.rishitgoklani.hermes_front_mobile.ui.face

import com.rishitgoklani.hermes_front_mobile.domain.BotState
import com.rishitgoklani.hermes_front_mobile.domain.face.Effect
import com.rishitgoklani.hermes_front_mobile.domain.face.EyeTweaks
import com.rishitgoklani.hermes_front_mobile.domain.face.FaceState
import com.rishitgoklani.hermes_front_mobile.domain.face.Mood
import com.rishitgoklani.hermes_front_mobile.domain.face.Reaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EyeStylesTest {
    private fun s(bot: BotState = BotState.Idle, f: FaceState = FaceState()) = EyeStyles.compute(bot, f)

    @Test fun everyStateHasAStyle() = BotState.entries.forEach { EyeStyles.forState(it) }
    @Test fun errorIsCrossedAndNeverBlinks() = EyeStyles.forState(BotState.Error).let { assertTrue(it.crossed); assertFalse(it.blinkEnabled) }
    @Test fun errorIgnoresMoodAndReaction() = assertTrue(s(BotState.Error, FaceState(mood = Mood.Happy, reaction = Reaction.Wink)).let { it.crossed && !it.winkRight })
    @Test fun listeningIsWiderThanIdle() = assertTrue(EyeStyles.forState(BotState.Listening).openness > EyeStyles.forState(BotState.Idle).openness)
    @Test fun thinkingLooksUpEvenWithMood() = assertTrue(s(BotState.Thinking, FaceState(mood = Mood.Sad)).lookY < 0)
    @Test fun everyMoodAndReactionComputes() { Mood.entries.forEach { s(f = FaceState(mood = it)) }; Reaction.entries.forEach { s(f = FaceState(reaction = it)) } }
    @Test fun happyUsesArcSadDroops() { assertEquals(EyeShape.HappyArc, s(f = FaceState(mood = Mood.Happy)).shape); assertTrue(s(f = FaceState(mood = Mood.Sad)).lidSlant < 0) }
    @Test fun angryTintsRedAndSlantsDown() = s(f = FaceState(mood = Mood.Angry)).let { assertTrue(it.lidSlant > 0); assertEquals(0xFFFF6B6B, it.colorHex) }
    @Test fun lowIntensityMoodBarelyChangesShape() = assertEquals(EyeShape.Round, s(f = FaceState(mood = Mood.Happy, moodIntensity = 0.2f)).shape)
    @Test fun loveGivesHeartPupilsAndHearts() = s(f = FaceState(mood = Mood.Love)).let { assertEquals(PupilShape.Heart, it.pupil); assertTrue(Effect.Hearts in it.effects) }
    @Test fun cryAddsTearsAndSadLook() = s(f = FaceState(reaction = Reaction.Cry)).let { assertTrue(Effect.Tears in it.effects); assertTrue(it.lidSlant < 0) }
    @Test fun winkClosesOneEye() = assertTrue(s(f = FaceState(reaction = Reaction.Wink)).winkRight)
    @Test fun dizzyAndStarPupils() { assertEquals(PupilShape.Spiral, s(f = FaceState(reaction = Reaction.Dizzy)).pupil); assertEquals(PupilShape.Star, s(f = FaceState(reaction = Reaction.StarEyes)).pupil) }
    @Test fun laughClosesArcsAndBounces() = s(f = FaceState(reaction = Reaction.Laugh)).let { assertEquals(EyeShape.ClosedArc, it.shape); assertEquals(Motion.Bounce, it.motion) }
    @Test fun explicitLookOverrides() = s(f = FaceState(lookX = 0.9f, lookY = -0.3f)).let { assertEquals(0.9f, it.lookX, 0f); assertEquals(-0.3f, it.lookY, 0f) }
    @Test fun tweaksOverrideColorSizeBlink() = s(f = FaceState(tweaks = EyeTweaks(colorHex = 0xFF00FF00, sizeScale = 1.4f, blinkRateSec = 2f, pupilScale = 0.5f))).let {
        assertEquals(0xFF00FF00, it.colorHex); assertEquals(1.4f, it.sizeScale, 0f); assertEquals(2f, it.blinkRateSec, 0f); assertEquals(0.5f, it.pupilScale, 0f) }
    @Test fun sleepyIsHalfClosed() = assertTrue(s(f = FaceState(mood = Mood.Sleepy)).openness < 0.5f)
    @Test fun confusedIsAsymmetricWithQuestionMark() = s(f = FaceState(mood = Mood.Confused)).let { assertTrue(it.asymmetry > 0); assertTrue(Effect.QuestionMark in it.effects) }
}

class ReactionOverridesMoodShapeTest {
    @Test fun cryReplacesHappyArcsWithRoundEyes() = EyeStyles.compute(BotState.Idle, FaceState(mood = Mood.Happy, reaction = Reaction.Cry)).let {
        assertEquals(EyeShape.Round, it.shape); assertTrue(Effect.Tears in it.effects)
    }
}
