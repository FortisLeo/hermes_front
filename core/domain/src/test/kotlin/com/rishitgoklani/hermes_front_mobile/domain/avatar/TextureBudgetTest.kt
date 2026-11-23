package com.rishitgoklani.hermes_front_mobile.domain.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Builds a minimal GLB whose images live in the binary chunk, like a real VRM. */
object TestGlb {
    fun png(w: Int, h: Int): ByteArray { val b = ByteArray(32); byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a).copyInto(b); fun put(o: Int, v: Int) { b[o] = (v shr 24).toByte(); b[o + 1] = (v shr 16).toByte(); b[o + 2] = (v shr 8).toByte(); b[o + 3] = v.toByte() }; put(16, w); put(20, h); return b }
    fun jpeg(w: Int, h: Int): ByteArray = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte(), 0, 4, 0, 0, 0xff.toByte(), 0xc0.toByte(), 0, 11, 8, (h shr 8).toByte(), h.toByte(), (w shr 8).toByte(), w.toByte(), 0, 0, 0, 0, 0)
    fun build(images: List<ByteArray>): ByteArray {
        var off = 0; val views = images.joinToString(",") { val s = """{"buffer":0,"byteOffset":$off,"byteLength":${it.size}}"""; off += it.size; s }
        val imgs = images.indices.joinToString(",") { """{"bufferView":$it,"mimeType":"image/png"}""" }
        var j = """{"bufferViews":[$views],"images":[$imgs],"extensions":{"VRM":{"meta":{"licenseName":"CC0"}}}}""".toByteArray(); while (j.size % 4 != 0) j += ' '.code.toByte()
        val bin = images.fold(ByteArray(0)) { a, b -> a + b }.let { it + ByteArray((4 - it.size % 4) % 4) }
        fun le(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
        return "glTF".toByteArray() + le(2) + le(12 + 8 + j.size + 8 + bin.size) + le(j.size) + "JSON".toByteArray() + j + le(bin.size) + "BIN\u0000".toByteArray() + bin
    }
}

class TextureBudgetTest {
    @Test fun readsPngAndJpegSizesAndRejectsGarbage() {
        assertEquals(ImageDim(4096, 2048), ImageDims.of(TestGlb.png(4096, 2048))); assertEquals(ImageDim(1024, 512), ImageDims.of(TestGlb.jpeg(1024, 512)))
        assertNull(ImageDims.of(byteArrayOf(1, 2, 3))); assertNull(ImageDims.of(ByteArray(40)))
    }
    @Test fun fitKeepsAspectAndOnlyShrinks() {
        assertEquals(ImageDim(512, 512), ImageDim(512, 512).fit(1024)); assertEquals(ImageDim(1024, 512), ImageDim(4096, 2048).fit(1024)); assertEquals(ImageDim(25, 1000), ImageDim(100, 4000).fit(1000))
    }
    @Test fun readsImageSizesFromAGlb() { assertEquals(listOf(ImageDim(2048, 2048), ImageDim(512, 256)), GlbImages.read(TestGlb.build(listOf(TestGlb.png(2048, 2048), TestGlb.jpeg(512, 256))))) }
    @Test fun unreadableImagesCountAsUnknownAndNonGlbHasNone() {
        assertEquals(listOf(GlbImages.UNKNOWN), GlbImages.read(TestGlb.build(listOf(ByteArray(40))))); assertTrue(GlbImages.read(byteArrayOf(1, 2, 3)).isEmpty())
    }
    @Test fun memoryEstimateUsesTheCap() {
        val one = listOf(ImageDim(4096, 4096)); assertTrue(TextureMemory.estimate(one, 4096) > TextureMemory.estimate(one, 1024) * 15); assertEquals(1024L * 1024 * 4 * 4 / 3, TextureMemory.estimate(one, 1024))
    }
    @Test fun policyPicksTheLargestCapThatFitsTheBudget() {
        val p = MemoryBudgetPolicy(budgetBytes = 32L * 1024 * 1024)
        assertEquals(2048, p.cap(listOf(ImageDim(2048, 2048)))); assertEquals(1024, p.cap(List(4) { ImageDim(4096, 4096) })); assertEquals(256, p.cap(List(400) { ImageDim(4096, 4096) }))
    }
    @Test fun planReportsSavings() {
        val plan = TextureBudget().plan(TestGlb.build(List(6) { TestGlb.png(4096, 4096) })); assertTrue(plan.cap < 4096); assertEquals(6, plan.images); assertTrue(plan.estimatedBytes < plan.originalBytes / 4)
    }
    @Test fun refusesOnlyModelsThatCannotFitEvenWhenTiny() {
        assertNull(TextureBudget().problem(TestGlb.build(List(20) { TestGlb.png(8192, 8192) }))); assertNotNull(TextureBudget().problem(TestGlb.build(List(1500) { TestGlb.png(512, 512) })))
    }
    @Test fun noteOnlyWhenTexturesWereShrunk() {
        val big = TextureBudget().plan(TestGlb.build(List(6) { TestGlb.png(4096, 4096) })); assertTrue(big.note()!!.startsWith("Textures shrunk to 1024 px"))
        assertNull(TextureBudget().plan(TestGlb.build(listOf(TestGlb.png(512, 512)))).note()); assertNull(TextureBudget().plan(TestGlb.build(emptyList())).note())
    }
}
