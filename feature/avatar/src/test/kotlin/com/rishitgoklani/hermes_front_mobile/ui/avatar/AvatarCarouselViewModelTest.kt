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
import com.rishitgoklani.hermes_front_mobile.testing.FakeNativeGlSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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

    private class Rig(entries: List<AvatarEntry>, slot: AvatarSlot = AvatarSlot.Eyes, lowMem: Boolean = false, snap: AvatarSnapshots? = null, val gl: FakeNativeGlSwitch = FakeNativeGlSwitch()) {
        val lib = FakeAvatarLibrary(entries); val active = FakeActiveSlotStore(slot); val files = FakeAvatarFiles().also { f -> kotlinx.coroutines.runBlocking { entries.forEach { f.write(it.id, byteArrayOf(1)) } } }
        val proven = mutableListOf<String>()
        val vm = AvatarCarouselViewModel(lib, active, files, CycleAvatar(lib, active, SelectAvatar(lib, active) { 5 }), { proven += it }, snap, { lowMem }, thumbDelayMs = 0, nativeGl = gl)
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

    @Test fun fatalMarksTheShownModelBrokenFallsBackToEyesAndPersistsTheFlag() = runTest {
        val r = Rig(listOf(e("a", 1), e("b", 2)), AvatarSlot.Model("a")); advanceUntilIdle(); r.vm.onShown("a", 1, false); advanceUntilIdle()
        r.vm.onFatal("shader failed"); advanceUntilIdle()
        assertEquals(AvatarStatus.BROKEN, r.lib.get("a")!!.status); assertEquals(AvatarSlot.Eyes, r.active.current()); assertTrue(r.gl.value)
        val ui = r.vm.ui.value; assertTrue(ui.nativeDisabled); assertNull(ui.active); assertNull(ui.staged); assertTrue(!ui.ready)
    }
    @Test fun aPersistedFlagMeansNothingIsRequestedFromTheGpuOnTheNextLaunch() = runTest {
        val r = Rig(listOf(e("a", 1)), AvatarSlot.Model("a"), gl = FakeNativeGlSwitch(true)); advanceUntilIdle()
        assertTrue(r.vm.ui.value.nativeDisabled); assertNull(r.vm.ui.value.active)
    }
    @Test fun nothingIsRequestedUntilTheFlagHasBeenRead() = runTest {
        val r = Rig(listOf(e("a", 1)), AvatarSlot.Model("a")); assertNull(r.vm.ui.value.active); advanceUntilIdle(); assertEquals("a", r.vm.ui.value.active!!.key)
    }
    @Test fun retryReenablesNativeAndRestoresTheModelThatFailed() = runTest {
        val r = Rig(listOf(e("a", 1)), AvatarSlot.Model("a")); advanceUntilIdle(); r.vm.onShown("a", 1, false); advanceUntilIdle()
        r.vm.onFatal("egl"); advanceUntilIdle(); r.vm.retryNative(); advanceUntilIdle()
        assertTrue(!r.gl.value); assertTrue(!r.vm.ui.value.nativeDisabled); assertEquals(AvatarStatus.OK, r.lib.get("a")!!.status)
        r.vm.swipeNext(); advanceUntilIdle(); assertEquals(AvatarSlot.Model("a"), r.vm.ui.value.slot); assertEquals("a", r.vm.ui.value.active!!.key)
    }
    @Test fun repeatedErrorsWithoutASuccessSwitchNativeOff() = runTest {
        val r = Rig(listOf(e("a", 1), e("b", 2), e("c", 3)), AvatarSlot.Model("a")); advanceUntilIdle()
        r.vm.onError("a", "x"); r.vm.onError("b", "x"); advanceUntilIdle(); assertTrue(!r.gl.value); assertTrue(!r.vm.ui.value.nativeDisabled)
        r.vm.onError("c", "x"); advanceUntilIdle(); assertTrue(r.gl.value); assertTrue(r.vm.ui.value.nativeDisabled)
    }
    @Test fun aSuccessfulShowResetsTheErrorCount() = runTest {
        val r = Rig(listOf(e("a", 1), e("b", 2), e("c", 3)), AvatarSlot.Model("c")); advanceUntilIdle()
        r.vm.onError("a", "x"); r.vm.onError("b", "x"); r.vm.onShown("c", 1, false); r.vm.onError("a", "x"); r.vm.onError("b", "x"); advanceUntilIdle(); assertTrue(!r.gl.value)
    }

    @Test fun nothingIsDrawnUntilTheSavedSlotAndTheNativeFlagAreRead() = runTest {
        val r = Rig(listOf(e("a", 1)), AvatarSlot.Model("a"))
        assertTrue("before anything ran the default Eyes must not be shown", !r.vm.ui.value.loaded)
        advanceUntilIdle(); assertTrue(r.vm.ui.value.loaded); assertEquals(AvatarSlot.Model("a"), r.vm.ui.value.slot)
    }

    @Test fun swipeNextAndAwaitReturnsOnlyOnceTheNewAvatarIsOnScreen() = runTest {
        val r = Rig(listOf(e("a", 1), e("b", 2)), AvatarSlot.Model("a")); advanceUntilIdle(); r.vm.onShown("a", 1, false); advanceUntilIdle()
        val j = launch { r.vm.swipeNextAndAwait(10_000) }; runCurrent()
        assertTrue("still waiting: b has not reported it is shown", j.isActive); assertEquals(AvatarSlot.Model("b"), r.vm.ui.value.slot)
        r.vm.onShown("b", 1, true); advanceUntilIdle(); assertTrue(j.isCompleted)
    }

    @Test fun swipeNextAndAwaitDoesNotWaitWhenTheNextStopIsEyes() = runTest {
        val r = Rig(listOf(e("a", 1)), AvatarSlot.Model("a")); advanceUntilIdle(); r.vm.onShown("a", 1, false); advanceUntilIdle()
        val j = launch { r.vm.swipeNextAndAwait(10_000) }; advanceUntilIdle(); assertTrue(j.isCompleted); assertEquals(AvatarSlot.Eyes, r.vm.ui.value.slot)
    }

    @Test fun swipeNextAndAwaitGivesUpAfterTheTimeoutWhenTheModelNeverLoads() = runTest {
        val r = Rig(listOf(e("a", 1), e("b", 2)), AvatarSlot.Model("a")); advanceUntilIdle(); r.vm.onShown("a", 1, false); advanceUntilIdle()
        val j = launch { r.vm.swipeNextAndAwait(1_500) }; advanceTimeBy(1_000); assertTrue(j.isActive); advanceTimeBy(1_000); advanceUntilIdle(); assertTrue(j.isCompleted)
    }
}
