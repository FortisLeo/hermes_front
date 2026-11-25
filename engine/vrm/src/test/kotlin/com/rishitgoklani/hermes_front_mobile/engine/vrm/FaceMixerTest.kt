package com.rishitgoklani.hermes_front_mobile.engine.vrm

import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.Glb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceMixerTest {
    // Target indexes: happy 0, sad 1, blink 2, Blush 3, surprised 4.
    private fun bind(i: Int) = """{"morphTargetBinds":[{"node":6,"index":$i,"weight":1}]}"""
    private fun expressions(blush: Boolean) = """"expressions":{"preset":{"happy":${bind(0)},"sad":${bind(1)},"blink":${bind(2)},"surprised":${bind(4)}},"custom":{${if (blush) "\"Blush\":${bind(3)}" else ""}}}"""
    private fun rig(blush: Boolean = true, vrm1: Boolean = true) =
        Rig(VrmParser.parse(Glb.parse(MiniVrm.build(vrm1 = vrm1, arms = true, expressions1 = expressions(blush), targets = 5))))
    private fun w(r: Rig, i: Int) = r.meshWeights.getValue(0)[i]
    private val HAPPY = 0; private val SAD = 1; private val BLINK = 2; private val BLUSH = 3; private val SURPRISED = 4

    /** Runs the face layer the way the driver does: clear, mix, fold into morph weights. */
    private fun run(m: FaceMixer, r: Rig, input: DriverInput, seconds: Float = 3f) {
        var t = 0f; while (t < seconds) { r.clearExpressions(); m.update(1f / 30f, input); m.apply(); r.applyExpressions(); t += 1f / 30f }
    }

    @Test fun everyMoodHasARecipeAndEveryRecipeUsesKnownChannels() {
        val moods = listOf("happy", "sad", "angry", "surprised", "worried", "scared", "confused", "bored", "sleepy", "shy", "proud", "love", "focused", "disgusted")
        for (m in moods) assertTrue("mood $m", FaceRecipes.moods.containsKey(m))
        assertNull("neutral stays plain", FaceRecipes.moods["neutral"])
        val all = FaceRecipes.moods.values + FaceRecipes.reactions.values + FaceRecipes.activities.values
        for (rec in all) for ((name, v) in rec.expr) { assertTrue("unknown channel $name", FaceChannels.index(name) >= 0); assertTrue(v in 0f..1f) }
        for (n in listOf("cry", "laugh", "giggle", "wink", "nod", "shakeno", "eyeroll", "startle", "sigh", "yawn", "celebrate", "dizzy", "hearteyes", "stareyes", "mindblown")) assertTrue("reaction $n", FaceRecipes.reactions.containsKey(n))
        for (n in listOf("thinking", "listening", "working", "error")) assertTrue("activity $n", FaceRecipes.activities.containsKey(n))
    }

    @Test fun aMoodEasesInInsteadOfSnapping() {
        val r = rig(); val m = FaceMixer(r)
        run(m, r, DriverInput(mood = "happy"), seconds = 0.05f); val early = w(r, HAPPY)
        run(m, r, DriverInput(mood = "happy"), seconds = 3f)
        assertTrue("starts below the target, got $early", early < 0.4f); assertEquals(0.8f, w(r, HAPPY), 0.02f)
    }

    @Test fun aRichMoodBlendsSeveralPresets() {
        val r = rig(); run(FaceMixer(r), r, DriverInput(mood = "worried"))
        assertTrue(w(r, SAD) > 0.4f); assertTrue(w(r, SURPRISED) > 0.1f); assertEquals(0f, w(r, HAPPY), 0f)
    }

    @Test fun moodIntensityScalesTheWeights() {
        val r = rig(); run(FaceMixer(r), r, DriverInput(mood = "sad", moodIntensity = 0.5f))
        assertEquals(0.425f, w(r, SAD), 0.02f)
    }

    @Test fun agentExpressionsSurviveTheMoodLayer() {
        val r = rig(); val m = FaceMixer(r); val inp = DriverInput(mood = "sad")
        var t = 0f; while (t < 2f) { r.clearExpressions(); r.setExpression("happy", 1f); m.update(1f / 30f, inp); m.apply(); r.applyExpressions(); t += 1f / 30f }
        assertEquals("agent's happy is not zeroed by the sad mood", 1f, w(r, HAPPY), 0.01f); assertTrue(w(r, SAD) > 0.7f)
    }

    @Test fun aReactionLayersOnTheMoodAndFadesBackOut() {
        val r = rig(); val m = FaceMixer(r)
        run(m, r, DriverInput(mood = "happy", reaction = "cry"), seconds = 2f); assertTrue("cry adds sad", w(r, SAD) > 0.9f); assertEquals(0.8f, w(r, HAPPY), 0.03f)
        run(m, r, DriverInput(mood = "happy"), seconds = 3f); assertTrue("sad fades once the reaction is gone", w(r, SAD) < 0.02f)
    }

    @Test fun headAnglesFollowTheNamedDirections() {
        val r = rig(); val m = FaceMixer(r)
        run(m, r, DriverInput(mood = "shy")); assertTrue("shy looks down: chin-down is negative x in the agent frame", m.headX < -0.1f); assertTrue(m.headY > 0.05f)
        run(m, r, DriverInput(mood = "proud")); assertTrue("proud lifts the chin", m.headX > 0.05f)
    }

    @Test fun thinkingLooksUpAndAside() {
        val r = rig(); val m = FaceMixer(r); run(m, r, DriverInput(activity = "thinking"))
        assertTrue(m.gazeX > 0.3f); assertTrue(m.gazeY > 0.4f); assertTrue(m.headZ > 0.05f)
    }

    @Test fun nodAndLaughWobbleTheHeadAndMouth() {
        val r = rig(); val m = FaceMixer(r); var lo = 9f; var hi = -9f
        for (i in 0 until 90) { m.update(1f / 30f, DriverInput(reaction = "nod")); if (i > 30) { lo = minOf(lo, m.headX); hi = maxOf(hi, m.headX) } }
        assertTrue("nod swings the head, range ${hi - lo}", hi - lo > 0.2f)
    }

    @Test fun laughFlutterMovesTheMouthChannel() {
        val aa = """"expressions":{"preset":{"aa":${bind(0)}}}"""
        val r = Rig(VrmParser.parse(Glb.parse(MiniVrm.build(vrm1 = true, arms = true, expressions1 = aa, targets = 5)))); val m = FaceMixer(r)
        var lo = 9f; var hi = -9f
        for (i in 0 until 120) { r.clearExpressions(); m.update(1f / 30f, DriverInput(reaction = "laugh")); m.apply(); r.applyExpressions(); if (i > 40) { val v = w(r, 0); lo = minOf(lo, v); hi = maxOf(hi, v) } }
        assertTrue("mouth flutters between $lo and $hi", hi - lo > 0.1f); assertTrue(lo >= 0.45f - 0.02f)
    }

    @Test fun ownBlushExpressionIsDrivenAndTheDecalStaysOff() {
        val r = rig(blush = true); val m = FaceMixer(r); assertTrue(m.hasOwnBlush)
        run(m, r, DriverInput(effects = setOf("blush")))
        assertTrue("model's blush shows, got ${w(r, BLUSH)}", w(r, BLUSH) > 0.8f); assertEquals(0f, m.decalWeight(FaceMixer.FX_BLUSH), 0f)
    }

    @Test fun withoutAnOwnBlushTheDecalFadesIn() {
        val r = rig(blush = false); val m = FaceMixer(r); assertFalse(m.hasOwnBlush)
        run(m, r, DriverInput(effects = setOf("blush", "tears")))
        assertTrue(m.decalWeight(FaceMixer.FX_BLUSH) > 0.9f); assertTrue(m.decalWeight(FaceMixer.FX_TEARS) > 0.9f); assertEquals(0f, m.decalWeight(FaceMixer.FX_ANGER), 0.01f)
        run(m, r, DriverInput(), seconds = 3f); assertEquals(0f, m.decalWeight(FaceMixer.FX_BLUSH), 0.02f)
    }

    @Test fun busyUntilEverythingHasSettled() {
        val r = rig(); val m = FaceMixer(r)
        m.update(1f / 30f, DriverInput(mood = "angry")); assertTrue(m.busy)
        run(m, r, DriverInput(mood = "angry"), seconds = 4f); m.update(1f / 30f, DriverInput(mood = "angry")); assertFalse("a still held mood lets the renderer rest", m.busy)
        m.update(1f / 30f, DriverInput(mood = "angry", effects = setOf("tears"))); assertTrue("an animated effect keeps frames coming", m.busy)
    }

    @Test fun blushNamesMatchRealWorldNamingButNotCheekShapes() {
        for (n in listOf("Blush", "blush_L", "Fcl_ALL_Blush", "cheek_red", "照れ", "頬染め", "Flushed")) assertTrue(n, BlushNames.matches(n))
        for (n in listOf("cheekPuff", "cheekSquintLeft", "mouthSmile", "Fcl_ALL_Joy", "interested")) assertFalse(n, BlushNames.matches(n))
    }
}
