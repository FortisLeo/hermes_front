package com.rishitgoklani.hermes_front_mobile.ui.avatar

import com.rishitgoklani.hermes_front_mobile.domain.avatar.AvatarAnimation
import com.rishitgoklani.hermes_front_mobile.domain.avatar.AvatarKeyframe
import com.rishitgoklani.hermes_front_mobile.domain.avatar.AvatarPose
import com.rishitgoklani.hermes_front_mobile.domain.avatar.AvatarState
import com.rishitgoklani.hermes_front_mobile.domain.avatar.Vec3
import com.rishitgoklani.hermes_front_mobile.domain.face.Mood
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EngineInputMapperTest {
    @Test fun mapsPoseBonesExpressionsHipsAndOffsets() {
        val p = EngineInputMapper.pose(AvatarPose(bones = mapOf("head" to Vec3(1f, 2f, 3f)), expressions = mapOf("happy" to 0.5f), hips = Vec3(0f, .1f, 0f), offsets = mapOf("hair" to Vec3(0f, 0f, .2f))))
        assertArrayEquals(floatArrayOf(1f, 2f, 3f), p.bones["head"]!!, 0f); assertEquals(0.5f, p.expressions["happy"]!!, 0f)
        assertArrayEquals(floatArrayOf(0f, .1f, 0f), p.hips!!, 0f); assertArrayEquals(floatArrayOf(0f, 0f, .2f), p.offsets["hair"]!!, 0f)
    }

    @Test fun mapsStateWithLowercaseMoodAndGaze() {
        val i = EngineInputMapper.input(AvatarState(mood = Mood.Happy, mouth = 0.4f, gazeX = 0.1f, gazeY = -0.2f, blendMs = 100))
        assertEquals("happy", i.mood); assertEquals(0.4f, i.mouth, 0f); assertEquals(0.1f, i.gazeX, 0f); assertEquals(-0.2f, i.gazeY, 0f); assertEquals(100, i.blendMs)
        assertNull(i.animation)
    }

    @Test fun mapsAnimationKeyframesAndLoop() {
        val a = EngineInputMapper.animation(AvatarAnimation(listOf(AvatarKeyframe(0, AvatarPose()), AvatarKeyframe(800, AvatarPose(bones = mapOf("spine" to Vec3(0f, 0f, 1f))))), loop = true))!!
        assertEquals(2, a.keyframes.size); assertEquals(800L, a.keyframes[1].tMs); assertEquals(true, a.loop)
        assertArrayEquals(floatArrayOf(0f, 0f, 1f), a.keyframes[1].pose.bones["spine"]!!, 0f)
    }

    @Test fun displayQualityMapsToTheRendererLevels() {
        assertEquals(com.rishitgoklani.hermes_front_mobile.engine.vrmgl.QualityLevel.Low, renderLevel(com.rishitgoklani.hermes_front_mobile.domain.DisplayQuality.Low))
        assertEquals(com.rishitgoklani.hermes_front_mobile.engine.vrmgl.QualityLevel.High, renderLevel(com.rishitgoklani.hermes_front_mobile.domain.DisplayQuality.High))
        assertEquals(com.rishitgoklani.hermes_front_mobile.engine.vrmgl.QualityLevel.Auto, renderLevel(com.rishitgoklani.hermes_front_mobile.domain.DisplayQuality.Auto))
    }
}
