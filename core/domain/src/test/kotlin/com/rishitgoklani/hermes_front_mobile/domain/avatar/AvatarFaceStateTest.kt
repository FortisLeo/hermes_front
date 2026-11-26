package com.rishitgoklani.hermes_front_mobile.domain.avatar

import com.rishitgoklani.hermes_front_mobile.domain.face.Mood
import com.rishitgoklani.hermes_front_mobile.domain.face.Reaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AvatarFaceStateTest {
    @Test fun setFaceCarriesMoodReactionAndTheirIntensities() {
        val c = AvatarController(); c.setFace(Mood.Angry, 0.7f, Reaction.Startle, 0.5f)
        val s = c.state.value; assertEquals(Mood.Angry, s.mood); assertEquals(0.7f, s.moodIntensity, 0f); assertEquals(Reaction.Startle, s.reaction); assertEquals(0.5f, s.reactionIntensity, 0f)
    }

    @Test fun clearingTheReactionLeavesTheMood() {
        val c = AvatarController(); c.setFace(Mood.Happy, 1f, Reaction.Laugh, 1f); c.setFace(Mood.Happy, 1f, null, 1f)
        assertNull(c.state.value.reaction); assertEquals(Mood.Happy, c.state.value.mood)
    }

    @Test fun setFaceKeepsMouthGazeAndPose() {
        val c = AvatarController(); c.setMouth(0.4f); c.look(0.2f, -0.1f); c.setFace(Mood.Sad, 1f, null, 1f)
        assertEquals(0.4f, c.state.value.mouth, 0f); assertEquals(0.2f, c.state.value.gazeX, 0f)
    }
}
