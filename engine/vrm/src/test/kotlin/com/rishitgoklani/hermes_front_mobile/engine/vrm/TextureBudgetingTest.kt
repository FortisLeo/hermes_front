package com.rishitgoklani.hermes_front_mobile.engine.vrm

import com.rishitgoklani.hermes_front_mobile.engine.vrm.gltf.Glb
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer

class TextureBudgetingTest {
    private val mb = 1024L * 1024
    @Test fun budgetIsAnEightiethOfRamClampedAndHalvedOnLowRam() {
        assertEquals(24 * mb, TextureBudgeting.deviceBudgetBytes(1024 * mb, false))     // 1 GB / 80 = 12.8 MB -> floor
        assertEquals(3400 * mb / 80, TextureBudgeting.deviceBudgetBytes(3400 * mb, false))
        assertEquals(96 * mb, TextureBudgeting.deviceBudgetBytes(16384 * mb, false))
        assertEquals(12 * mb, TextureBudgeting.deviceBudgetBytes(1024 * mb, true)); assertEquals(48 * mb, TextureBudgeting.deviceBudgetBytes(16384 * mb, true))
    }
    @Test fun capShrinksUntilTheImagesFit() {
        val imgs = List(4) { ImageInfo(4096, 4096, 4) }
        assertEquals(2048, TextureBudgeting.capFor(imgs, 4096, 96 * mb)); assertEquals(1024, TextureBudgeting.capFor(imgs, 4096, 24 * mb))
        assertEquals(256, TextureBudgeting.capFor(imgs, 4096, 1 * mb))
    }
    @Test fun sixteenBitImagesGetTwiceTheSizeForTheSameBudget() {
        val rgba = List(4) { ImageInfo(2048, 2048, 4) }; val rgb = List(4) { ImageInfo(2048, 2048, 2) }
        assertEquals(1024, TextureBudgeting.capFor(rgba, 2048, 64 * mb)); assertEquals(2048, TextureBudgeting.capFor(rgb, 2048, 64 * mb))
    }
    @Test fun requestedCapIsNeverExceeded() { assertEquals(512, TextureBudgeting.capFor(listOf(ImageInfo(4096, 4096, 2)), 512, 96 * mb)) }
    @Test fun availableBudgetSharesWithResidentModelsButKeepsHalf() {
        assertEquals(30L, TextureBudgeting.availableBytes(40, 10)); assertEquals(20L, TextureBudgeting.availableBytes(40, 35))
    }
    @Test fun streamReadsASliceWithoutCopyingAndSupportsReset() {
        val s = ByteBufferInputStream(ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4, 5)).also { it.position(1); it.limit(4) }.slice())
        assertEquals(3, s.available()); assertEquals(2, s.read()); s.mark(10); val b = ByteArray(4); assertEquals(2, s.read(b, 0, 4)); assertArrayEquals(byteArrayOf(3, 4, 0, 0), b); assertEquals(-1, s.read())
        s.reset(); assertEquals(3, s.read()); assertEquals(1, s.skip(5)); assertEquals(-1, s.read(b, 0, 1))
    }
    @Test fun imageSlicesPointAtTheEmbeddedBytesAndMissingOnesAreNull() {
        val glb = Glb.parse(MiniVrm.build()); val asset = VrmParser.parse(glb)
        asset.images.indices.firstOrNull()?.let { i -> val s = GlbImageSlices.slice(glb, asset, i); assertNotNull(s); assertArrayEquals(asset.imageBytes(i), ByteArray(s!!.remaining()).also { s.get(it) }) }
        assertNull(GlbImageSlices.slice(glb, asset, 99))
    }
}
