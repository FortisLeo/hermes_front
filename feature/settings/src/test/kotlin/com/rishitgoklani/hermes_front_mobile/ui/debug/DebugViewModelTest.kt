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
    @Test fun failureShowsError() { val vm = DebugViewModel(DebugSource { error("boom") }); assertEquals("boom", vm.state.value.error); assertNull(vm.state.value.snapshot) }
    @Test fun refreshReloads() {
        var n = 0; val vm = DebugViewModel(DebugSource { DebugSnapshot(true, "v", ++n, emptyList()) }); vm.refresh()
        assertEquals(2, vm.state.value.snapshot!!.bridgeTools); assertTrue(!vm.state.value.loading)
    }
}
