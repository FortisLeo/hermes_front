package com.rishitgoklani.hermes_front_mobile.domain.avatar.library

import com.rishitgoklani.hermes_front_mobile.domain.avatar.GlbImages
import com.rishitgoklani.hermes_front_mobile.domain.avatar.ImageDim
import com.rishitgoklani.hermes_front_mobile.domain.avatar.TestGlb
import com.rishitgoklani.hermes_front_mobile.testing.FakeActiveSlotStore
import com.rishitgoklani.hermes_front_mobile.testing.FakeAvatarFiles
import com.rishitgoklani.hermes_front_mobile.testing.FakeAvatarLibrary
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A GLB with a mesh bufferView (non-image data that must survive) followed by images. Image bytes are padded so shrinking really saves space. */
private fun glbWith(meshBytes: ByteArray, images: List<ByteArray>, license: String = "CC0"): ByteArray {
    val parts = listOf(meshBytes) + images; var off = 0
    val views = parts.joinToString(",") { val s = """{"buffer":0,"byteOffset":$off,"byteLength":${it.size}}"""; off += (it.size + 3) / 4 * 4; s }
    val imgs = images.indices.joinToString(",") { """{"bufferView":${it + 1},"mimeType":"image/png"}""" }
    val bin = java.io.ByteArrayOutputStream(); parts.forEach { while (bin.size() % 4 != 0) bin.write(0); bin.write(it) }
    return Glb.write(kotlinx.serialization.json.Json.parseToJsonElement("""{"buffers":[{"byteLength":${bin.size()}}],"bufferViews":[$views],"images":[$imgs],"extensions":{"VRM":{"meta":{"title":"T","author":"A","licenseName":"$license"}}}}""") as kotlinx.serialization.json.JsonObject, bin.toByteArray())
}
private fun bigPng(w: Int, h: Int) = TestGlb.png(w, h) + ByteArray(5000) { 7 }
private val halving = ImageTranscoder { data, _, max -> val d = com.rishitgoklani.hermes_front_mobile.domain.avatar.ImageDims.of(data.copyOf(32))!!.fit(max); TestGlb.png(d.w, d.h) }

private fun entry(id: String, added: Long, tex: Long = 10_000_000, status: AvatarStatus = AvatarStatus.OK) =
    AvatarEntry(id, id, AvatarSource.UPLOAD, "CC0", "a", 1, 1, 512, tex, null, null, added, added, status)

class GlbOptimizerTest {
    @Test fun shrinksOversizedImagesAndKeepsOtherData() {
        val mesh = ByteArray(40) { it.toByte() }
        val glb = glbWith(mesh, listOf(bigPng(4096, 4096), bigPng(256, 256)))
        val r = GlbOptimizer(halving).optimize(glb, 1024)
        assertEquals(1, r.shrunkImages); assertTrue(r.savedBytes > 4000); assertTrue(r.bytes.size < glb.size)
        assertEquals(listOf(ImageDim(1024, 1024), ImageDim(256, 256)), GlbImages.read(r.bytes))
        val back = Glb.parse(r.bytes)!!; val v0 = back.json["bufferViews"] as kotlinx.serialization.json.JsonArray
        val o = (v0[0] as kotlinx.serialization.json.JsonObject)["byteOffset"].toString().toInt(); assertEquals(mesh.toList(), back.bin.copyOfRange(o, o + 40).toList())
    }
    @Test fun returnsTheOriginalWhenNothingIsTooBigOrInputIsNotAGlb() {
        val glb = glbWith(ByteArray(8), listOf(bigPng(512, 512))); assertTrue(GlbOptimizer(halving).optimize(glb, 1024).bytes === glb)
        val junk = byteArrayOf(1, 2, 3); assertTrue(GlbOptimizer(halving).optimize(junk, 512).bytes === junk)
    }
    @Test fun keepsTheImageWhenTheTranscoderFails() { val glb = glbWith(ByteArray(8), listOf(bigPng(4096, 4096))); assertEquals(0, GlbOptimizer { _, _, _ -> null }.optimize(glb, 512).shrunkImages) }
    @Test fun reportsProgressPerImage() { val seen = mutableListOf<Int>(); GlbOptimizer(halving).optimize(glbWith(ByteArray(8), listOf(bigPng(2048, 2048), bigPng(2048, 2048))), 512) { d, _ -> seen += d }; assertEquals(listOf(1, 2), seen) }
}

class FaceCarouselTest {
    @Test fun ringIsEyesThenModelsInAddedOrderAndWraps() {
        val c = FaceCarousel(listOf(entry("b", 2), entry("a", 1), entry("x", 3, status = AvatarStatus.BROKEN)))
        assertEquals(listOf(AvatarSlot.Eyes, AvatarSlot.Model("a"), AvatarSlot.Model("b")), c.slots)
        assertEquals(AvatarSlot.Model("a"), c.next(AvatarSlot.Eyes)); assertEquals(AvatarSlot.Model("b"), c.next(AvatarSlot.Model("a"))); assertEquals(AvatarSlot.Eyes, c.next(AvatarSlot.Model("b")))
    }
    @Test fun unknownSlotCountsAsEyesAndNoModelsStaysOnEyes() {
        assertEquals(AvatarSlot.Model("a"), FaceCarousel(listOf(entry("a", 1))).next(AvatarSlot.Model("gone"))); assertEquals(AvatarSlot.Eyes, FaceCarousel(emptyList()).next(AvatarSlot.Eyes))
    }
    @Test fun stageTargetIsTheNextModelOrNullBeforeEyes() {
        val c = FaceCarousel(listOf(entry("a", 1), entry("b", 2)))
        assertEquals("a", c.stageTarget(AvatarSlot.Eyes)!!.id); assertEquals("b", c.stageTarget(AvatarSlot.Model("a"))!!.id); assertNull(c.stageTarget(AvatarSlot.Model("b")))
    }
    @Test fun stagingNeedsBudgetAndEnoughMemory() {
        val big = entry("big", 1, tex = 60_000_000)
        assertTrue(StagingPolicy.shouldStage(entry("a", 1, tex = 30_000_000), big, lowMemory = false)); assertTrue(!StagingPolicy.shouldStage(big, big, false)); assertTrue(!StagingPolicy.shouldStage(null, entry("s", 1), true))
        assertTrue(!StagingPolicy.shouldStage(null, entry("b", 1, status = AvatarStatus.BROKEN), false))
    }
}

class AvatarUseCasesTest {
    private val lib = FakeAvatarLibrary(); private val files = FakeAvatarFiles(); private val active = FakeActiveSlotStore()
    private var ids = 0; private var clock = 100L
    private val import = ImportAvatar(lib, files, GlbOptimizer(halving), { "id${++ids}" }, { clock++ })
    private fun req(glb: ByteArray, enforce: Boolean = false) = ImportRequest(glb, "Fern", AvatarSource.UPLOAD, enforce)

    @Test fun importOptimizesStoresAndAddsAPendingEntry() = runBlocking {
        val glb = glbWith(ByteArray(8), listOf(bigPng(4096, 4096), bigPng(4096, 4096), bigPng(4096, 4096), bigPng(4096, 4096), bigPng(4096, 4096), bigPng(4096, 4096)))
        val steps = mutableListOf<String>()
        val r = import(req(glb)) { steps += it.stage } as ImportResult.Imported
        assertEquals(AvatarStatus.PENDING, r.entry.status); assertTrue(r.entry.storedBytes < r.entry.originalBytes); assertTrue(r.entry.note!!.startsWith("Optimized"))
        assertEquals(r.entry.storedBytes.toInt(), files.models["id1"]!!.size); assertEquals(listOf("id1"), lib.all().map { it.id }); assertTrue("Saving" in steps && "Optimizing" in steps)
    }
    @Test fun importRefusesBadFilesAndEnforcesLicenseOnlyWhenAsked() = runBlocking {
        assertTrue(import(req(byteArrayOf(1, 2))) is ImportResult.Rejected)
        val nc = glbWith(ByteArray(8), listOf(bigPng(64, 64)), license = "CC_BY_NC")
        assertTrue(import(req(nc, enforce = true)) is ImportResult.Rejected); assertTrue(import(req(nc, enforce = false)) is ImportResult.Imported); assertEquals(1, files.models.size)
    }
    @Test fun provenMarksOkAndReconcileBreaksUnprovenAndFallsBackToEyes() = runBlocking {
        val a = (import(req(glbWith(ByteArray(8), listOf(bigPng(64, 64))))) as ImportResult.Imported).entry; val b = (import(req(glbWith(ByteArray(8), listOf(bigPng(64, 64))))) as ImportResult.Imported).entry
        MarkProven(lib)(a.id); active.set(AvatarSlot.Model(b.id))
        val broken = ReconcilePending(lib, active)()
        assertEquals(listOf(b.id), broken.map { it.id }); assertEquals(AvatarStatus.OK, lib.get(a.id)!!.status); assertEquals(AvatarStatus.BROKEN, lib.get(b.id)!!.status); assertEquals(AvatarSlot.Eyes, active.current())
    }
    @Test fun selectTouchesAndRefusesBrokenOrUnknown() = runBlocking {
        lib.add(entry("a", 1)); lib.add(entry("bad", 2, status = AvatarStatus.BROKEN)); val select = SelectAvatar(lib, active) { 999 }
        select(AvatarSlot.Model("a")); assertEquals(AvatarSlot.Model("a"), active.current()); assertEquals(999L, lib.get("a")!!.lastUsedAt)
        select(AvatarSlot.Model("bad")); select(AvatarSlot.Model("nope")); assertEquals(AvatarSlot.Model("a"), active.current()); select(AvatarSlot.Eyes); assertEquals(AvatarSlot.Eyes, active.current())
    }
    @Test fun cycleWalksTheRing() = runBlocking {
        lib.add(entry("a", 1)); lib.add(entry("b", 2)); val cycle = CycleAvatar(lib, active, SelectAvatar(lib, active) { 5 })
        assertEquals(listOf<AvatarSlot>(AvatarSlot.Model("a"), AvatarSlot.Model("b"), AvatarSlot.Eyes, AvatarSlot.Model("a")), List(4) { cycle() })
    }
    @Test fun removeDeletesFilesAndLeavesEyesIfActive() = runBlocking {
        files.write("a", byteArrayOf(1)); files.writeThumb("a", byteArrayOf(2)); lib.add(entry("a", 1)); active.set(AvatarSlot.Model("a"))
        RemoveAvatar(lib, files, active)("a"); assertTrue(lib.all().isEmpty() && files.models.isEmpty() && files.thumbs.isEmpty()); assertEquals(AvatarSlot.Eyes, active.current())
    }
}
