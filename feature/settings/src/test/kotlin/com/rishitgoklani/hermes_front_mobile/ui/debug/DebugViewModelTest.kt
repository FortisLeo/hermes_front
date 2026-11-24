package com.rishitgoklani.hermes_front_mobile.ui.settings

import com.rishitgoklani.hermes_front_mobile.domain.behavior.DebugSnapshot
import com.rishitgoklani.hermes_front_mobile.domain.behavior.DebugSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DebugViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun loadsSnapshotOnCreation() {
        val vm = DebugViewModel(DebugSource { DebugSnapshot(true, "0.23", 70, listOf("a")) })
        assertEquals(70, vm.state.value.snapshot!!.bridgeTools); assertNull(vm.state.value.error)
    }
    @Test fun showsLastCrashAndClearsIt() {
        val store = com.rishitgoklani.hermes_front_mobile.testing.FakeCrashStore()
        val log = com.rishitgoklani.hermes_front_mobile.domain.diagnostics.CrashLog(store)
        log.record("main", RuntimeException("kaboom"), "1.0", 1L)
        val vm = DebugViewModel(DebugSource { DebugSnapshot(true, "v", 1, emptyList()) }, log)
        assertTrue(vm.state.value.lastCrash!!.contains("kaboom"))
        vm.clearCrash(); assertNull(vm.state.value.lastCrash); assertNull(store.text)
    }
    @Test fun noCrashMeansNullLastCrash() { assertNull(DebugViewModel(DebugSource { DebugSnapshot(true, "v", 1, emptyList()) }).state.value.lastCrash) }
    @Test fun failureShowsError() { val vm = DebugViewModel(DebugSource { error("boom") }); assertEquals("boom", vm.state.value.error); assertNull(vm.state.value.snapshot) }
    @Test fun refreshReloads() {
        var n = 0; val vm = DebugViewModel(DebugSource { DebugSnapshot(true, "v", ++n, emptyList()) }); vm.refresh()
        assertEquals(2, vm.state.value.snapshot!!.bridgeTools); assertTrue(!vm.state.value.loading)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DebugViewModelApiLogTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()
    private val snap = DebugSource { DebugSnapshot(true, "v", 1, emptyList()) }
    private fun log() = com.rishitgoklani.hermes_front_mobile.domain.diagnostics.InMemoryApiLog()
    private fun com.rishitgoklani.hermes_front_mobile.domain.diagnostics.InMemoryApiLog.add(src: com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiSource, label: String) =
        record(src, label, "req", "res", com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiStatus.Ok)

    @Test fun showsEveryCallNewestFirstAndKeepsUpWithNewOnes() {
        val l = log(); l.add(com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiSource.Hermes, "a")
        val vm = DebugViewModel(snap, apiLog = l)
        l.add(com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiSource.Bridge, "b")
        assertEquals(listOf("b", "a"), vm.state.value.apiCalls.map { it.label })
    }

    @Test fun theSourceFilterNarrowsWhatIsShown() {
        val l = log(); l.add(com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiSource.Hermes, "h"); l.add(com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiSource.GeminiLive, "g")
        val vm = DebugViewModel(snap, apiLog = l)
        vm.setApiFilter(com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiSource.GeminiLive)
        assertEquals(listOf("g"), vm.state.value.shownApiCalls.map { it.label }); assertEquals(2, vm.state.value.apiCalls.size)
        vm.setApiFilter(null); assertEquals(2, vm.state.value.shownApiCalls.size)
    }

    @Test fun clearingEmptiesTheLog() {
        val l = log(); l.add(com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiSource.Hermes, "h"); val vm = DebugViewModel(snap, apiLog = l)
        vm.clearApiLog(); assertTrue(vm.state.value.apiCalls.isEmpty())
    }

    @Test fun copyTextCoversOnlyTheFilteredCalls() {
        val l = log(); l.add(com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiSource.Hermes, "h-call"); l.add(com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiSource.Bridge, "b-call")
        val vm = DebugViewModel(snap, apiLog = l); vm.setApiFilter(com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiSource.Bridge)
        val t = vm.apiLogText { "T" }
        assertTrue("b-call" in t && "h-call" !in t)
    }

    @Test fun refreshingKeepsTheCallsAndTheFilter() {
        val l = log(); l.add(com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiSource.Hermes, "h"); val vm = DebugViewModel(snap, apiLog = l)
        vm.setApiFilter(com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiSource.Hermes); vm.refresh()
        assertEquals(1, vm.state.value.apiCalls.size); assertEquals(com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiSource.Hermes, vm.state.value.apiFilter)
    }

    @Test fun withoutALogTheSectionIsJustEmpty() = assertTrue(DebugViewModel(snap).state.value.apiCalls.isEmpty())
}
