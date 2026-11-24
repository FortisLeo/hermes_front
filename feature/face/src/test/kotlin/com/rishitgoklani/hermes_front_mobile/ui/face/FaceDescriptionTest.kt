package com.rishitgoklani.hermes_front_mobile.ui.face

import com.rishitgoklani.hermes_front_mobile.domain.face.FaceState
import com.rishitgoklani.hermes_front_mobile.domain.face.Mood
import com.rishitgoklani.hermes_front_mobile.domain.face.Reaction
import org.junit.Assert.assertEquals
import org.junit.Test

class FaceDescriptionTest {
    @Test fun describesMood() { assertEquals("Deskbot face, mood happy", FaceDescription.of(FaceState(mood = Mood.Happy))) }
    @Test fun includesReactionWhenPresent() { assertEquals("Deskbot face, mood neutral, reacting giggle", FaceDescription.of(FaceState(reaction = Reaction.Giggle))) }
}
