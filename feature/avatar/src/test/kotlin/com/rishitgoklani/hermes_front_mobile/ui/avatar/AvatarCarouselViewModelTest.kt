package com.rishitgoklani.hermes_front_mobile.ui.avatar

import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarEntry
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarSlot
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarSource
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.AvatarStatus
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.CycleAvatar
import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.SelectAvatar
import com.rishitgoklani.hermes_front_mobile.testing.FakeActiveSlotStore
import com.rishitgoklani.hermes_front_mobile.testing.FakeAvatarFiles
import com.rishitgoklani.hermes_front_mobile.testing.FakeAvatarLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AvatarCarouselViewModelTest {
    @Before fun up() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun down() = Dispatchers.resetMain()

    private fun e(id: String, added: Long, tex: Long = 10_000_000, status: AvatarStatus = AvatarStatus.OK, thumb: String? = null) =
        AvatarEntry(id, id, AvatarSource.UPLOAD, "CC0", "a", 1, 1, 512, tex, null, thumb, added, added, status)

    private class Rig(entries: List<AvatarEntry>, slot: AvatarSlot = AvatarSlot.Eyes, lowMem: Boolean = false, snap: AvatarSnapshots? = null) {
        val lib = FakeAvatarLibrary(entries); val active = FakeActiveSlotStore(slot); val files = FakeAvatarFiles().also { f -> kotlinx.coroutines.runBlocking { entries.forEach { f.write(it.id, byteArrayOf(1)) } } }
        val proven = mutableListOf<String>()
        val vm = AvatarCarouselViewModel(lib, active, files, CycleAvatar(lib, active, SelectAvatar(lib, active) { 5 }), { proven += it }, snap, { lowMem }, thumbDelayMs = 0)
    }

    @Test fun eyesSlotRequestsNothing() = runTest {
        val r = Rig(listOf(e("a", 1))); advanceUntilIdle(); val ui = r.vm.ui.value; assertNull(ui.active); assertNull(ui.staged); assertTrue(!ui.ready); assertEquals(2, ui.slotCount)
    }
    @Test fun activeModelIsRequestedFirstAndTheNextIsStagedOnlyAfterItIsShown() = runTest {
        val r = Rig(listOf(e("a", 1), e("b", 2)), AvatarSlot.Model("a")); advanceUntilIdle()
        assertEquals("a", r.vm.ui.value.active!!.key); assertNull(r.vm.ui.value.staged); assertTrue(r.vm.ui.value.active!!.url.endsWith("/library/a.vrm"))
        r.vm.onShown("a", 120, false); advanceUntilIdle()
        assertEquals("b", r.vm.ui.value.staged!!.key); assertTrue(r.vm.ui.value.ready); assertEquals(listOf("a"), r.proven)
    }
    @Test fun nothingIsStagedWhenTheNextStopIsEyesOrMemoryIsLow() = runTest {
        val last = Rig(listOf(e("a", 1)), AvatarSlot.Model("a")); last.vm.onShown("a", 1, false); advanceUntilIdle(); assertNull(last.vm.ui.value.staged)
        val low = Rig(listOf(e("a", 1), e("b", 2)), AvatarSlot.Model("a"), lowMem = true); low.vm.onShown("a", 1, false); advanceUntilIdle(); assertNull(low.vm.ui.value.staged)
        val heavy = Rig(listOf(e("a", 1, tex = 60_000_000), e("b", 2, tex = 60_000_000)), AvatarSlot.Model("a")); heavy.vm.onShown("a", 1, false); advanceUntilIdle(); assertNull(heavy.vm.ui.value.staged)
    }
    @Test fun swipeWalksTheRingAndLeavingAnAvatarClearsReady() = runTest {
        val r = Rig(listOf(e("a", 1))); advanceUntilIdle()
        r.vm.swipeNext(); advanceUntilIdle(); assertEquals(AvatarSlot.Model("a"), r.vm.ui.value.slot); r.vm.onShown("a", 1, false); advanceUntilIdle(); assertTrue(r.vm.ui.value.ready)
        r.vm.swipeNext(); advanceUntilIdle(); assertEquals(AvatarSlot.Eyes, r.vm.ui.value.slot); assertTrue(!r.vm.ui.value.ready); assertNull(r.vm.ui.value.active)
    }
    @Test fun brokenOrMissingFileMeansNoActiveRequest() = runTest {
        val r = Rig(listOf(e("a", 1, status = AvatarStatus.BROKEN)), AvatarSlot.Model("a")); advanceUntilIdle(); assertNull(r.vm.ui.value.active)
        val gone = Rig(listOf(e("b", 1)), AvatarSlot.Model("b")); gone.files.delete("b"); advanceUntilIdle(); assertNull(gone.vm.ui.value.active)
    }
    @Test fun viewerErrorMarksBrokenAndFallsBackToEyes() = runTest {
        val r = Rig(listOf(e("a", 1)), AvatarSlot.Model("a")); advanceUntilIdle(); r.vm.onError("a", "bad glb"); advanceUntilIdle()
        assertEquals(AvatarStatus.BROKEN, r.lib.get("a")!!.status); assertEquals(AvatarSlot.Eyes, r.active.current())
    }
    @Test fun firstShowTakesAThumbnailOnce() = runTest {
        val snaps = AvatarSnapshots().also { it.capture = { done -> done(byteArrayOf(9, 9)) } }
        val r = Rig(listOf(e("a", 1)), AvatarSlot.Model("a"), snap = snaps); advanceUntilIdle(); r.vm.onShown("a", 1, false); advanceUntilIdle()
        assertEquals("/fake/thumbs/a.png", r.lib.get("a")!!.thumbPath); assertEquals(1, r.files.thumbs.size)
        r.files.thumbs.clear(); r.vm.onShown("a", 1, true); advanceUntilIdle(); assertTrue(r.files.thumbs.isEmpty())
    }
}
