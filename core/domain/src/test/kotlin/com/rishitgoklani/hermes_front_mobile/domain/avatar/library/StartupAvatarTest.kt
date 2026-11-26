package com.rishitgoklani.hermes_front_mobile.domain.avatar.library

import com.rishitgoklani.hermes_front_mobile.testing.FakeActiveSlotStore
import com.rishitgoklani.hermes_front_mobile.testing.FakeAvatarFiles
import com.rishitgoklani.hermes_front_mobile.testing.FakeAvatarLibrary
import com.rishitgoklani.hermes_front_mobile.testing.FakeNativeGlSwitch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StartupAvatarTest {
    private fun e(id: String, status: AvatarStatus = AvatarStatus.OK) = AvatarEntry(id, id, AvatarSource.UPLOAD, "CC0", "a", 1, 1, 1024, 1, null, null, 1, 1, status)
    private fun pick(slot: AvatarSlot, entries: List<AvatarEntry>, nativeOff: Boolean = false, withFile: Boolean = true) = runBlocking {
        val files = FakeAvatarFiles().also { f -> if (withFile) entries.forEach { f.write(it.id, byteArrayOf(1)) } }
        StartupAvatar(FakeAvatarLibrary(entries), FakeActiveSlotStore(slot), files, FakeNativeGlSwitch(nativeOff))()
    }

    @Test fun theSavedAvatarIsPickedWithItsTextureCap() { assertEquals(StartupAvatar.Pick("a", 1024), pick(AvatarSlot.Model("a"), listOf(e("a")))) }
    @Test fun eyesMeansNothingToPreload() { assertNull(pick(AvatarSlot.Eyes, listOf(e("a")))) }
    @Test fun nothingWhenNative3dIsSwitchedOff() { assertNull(pick(AvatarSlot.Model("a"), listOf(e("a")), nativeOff = true)) }
    @Test fun nothingForABrokenUnknownOrMissingModel() {
        assertNull(pick(AvatarSlot.Model("a"), listOf(e("a", AvatarStatus.BROKEN))))
        assertNull(pick(AvatarSlot.Model("zz"), listOf(e("a"))))
        assertNull(pick(AvatarSlot.Model("a"), listOf(e("a")), withFile = false))
    }
    @Test fun aPendingModelIsStillPreloadedLikeTheCarouselShowsIt() { assertEquals("a", pick(AvatarSlot.Model("a"), listOf(e("a", AvatarStatus.PENDING)))?.id) }
}
