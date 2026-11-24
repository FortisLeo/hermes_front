package com.rishitgoklani.hermes_front_mobile.engine.vrm

import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.Glb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderPlanTest {
    private val asset = VrmParser.parse(Glb.parse(MiniVrm.build()))

    @Test fun opaqueModelDrawsOpaqueThenOutline() {
        val plan = RenderPlan.build(asset, outlines = true)
        assertEquals(listOf(Pass.OPAQUE, Pass.OUTLINE), plan.map { it.pass })
    }

    @Test fun outlinesCanBeSwitchedOffForLowEndDevices() = assertEquals(listOf(Pass.OPAQUE), RenderPlan.build(asset, outlines = false).map { it.pass })

    @Test fun framingCentersOnTheModelAndBacksOffEnoughToFitIt() {
        val vrm0 = Rig(asset).modelMatrix; val f = Framing.of(asset, vrm0)
        assertEquals(0f, f.targetX, 1f)                                  // centred within one metre
        assertEquals(0.5f, f.targetY, 1e-5f)
        assertTrue("camera is in front of a model that faces +Z", f.eyeZ > f.targetZ)
        assertTrue(f.eyeZ - f.targetZ > 0.5f)
    }

    @Test fun textureSizingHalvesUntilItFits() {
        assertEquals(1, TextureSizing.sampleSize(512, 512, 1024)); assertEquals(4, TextureSizing.sampleSize(4096, 2048, 1024)); assertEquals(2, TextureSizing.sampleSize(2048, 1024, 1024))
        assertEquals(1024L * 1024 * 2 * 4 / 3, TextureSizing.decodedBytes(4096, 4096, 1024, 2))
    }

    @Test fun onlyAlphaMaterialsAskForAnAlphaChannel() = assertTrue(ImageFormats.needsAlpha(asset).none { it })
}
