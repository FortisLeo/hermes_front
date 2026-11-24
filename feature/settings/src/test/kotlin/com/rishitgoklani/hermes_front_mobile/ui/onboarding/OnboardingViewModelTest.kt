package com.rishitgoklani.hermes_front_mobile.ui.onboarding

import com.rishitgoklani.hermes_front_mobile.domain.behavior.DebugSnapshot
import com.rishitgoklani.hermes_front_mobile.domain.behavior.DebugSource
import com.rishitgoklani.hermes_front_mobile.testing.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val healthy = DebugSource { DebugSnapshot(true, "0.23", 70, emptyList()) }

    @Test fun progressWalksStepsInOrderThenFinishes() {
        var p = OnboardingProgress()
        assertEquals(OnboardingStep.Welcome, p.step); assertTrue(p.isFirst)
        p = p.next(); assertEquals(OnboardingStep.Permissions, p.step)
        p = p.next(); assertEquals(OnboardingStep.Connection, p.step); assertTrue(p.isLast)
        p = p.next(); assertTrue(p.finished)
        assertEquals(p, p.next())
    }

    @Test fun backStopsAtFirstStep() {
        assertEquals(OnboardingStep.Welcome, OnboardingProgress().back().step)
        assertEquals(OnboardingStep.Welcome, OnboardingProgress().next().back().step)
    }

    @Test fun skipFinishesFromAnyStep() { assertTrue(OnboardingProgress().next().skip().finished) }

    @Test fun showsOnFirstRunOnly() = runBlocking {
        val repo = FakeSettingsRepository()
        assertTrue(OnboardingViewModel(repo, healthy).state.value.visible)
        repo.setOnboardingDone(true)
        assertFalse(OnboardingViewModel(repo, healthy).state.value.visible)
    }

    @Test fun finishingPersistsDoneAndHides() = runBlocking {
        val repo = FakeSettingsRepository(); val vm = OnboardingViewModel(repo, healthy)
        vm.next(); vm.next(); vm.next()
        assertFalse(vm.state.value.visible); assertTrue(repo.onboardingDone.first())
    }

    @Test fun skippingPersistsDone() = runBlocking {
        val repo = FakeSettingsRepository(); val vm = OnboardingViewModel(repo, healthy)
        vm.skip()
        assertFalse(vm.state.value.visible); assertTrue(repo.onboardingDone.first())
    }

    @Test fun replayShowsFromTheStartAgain() = runBlocking {
        val repo = FakeSettingsRepository(); val vm = OnboardingViewModel(repo, healthy)
        vm.next(); vm.skip(); vm.replay()
        assertTrue(vm.state.value.visible); assertEquals(OnboardingStep.Welcome, vm.state.value.progress.step)
    }

    @Test fun connectionTestReportsOnline() {
        val vm = OnboardingViewModel(FakeSettingsRepository(), healthy); vm.testConnection()
        assertEquals(ConnectionStatus.Ok("0.23"), vm.state.value.connection)
    }

    @Test fun connectionTestReportsFailureReason() {
        val vm = OnboardingViewModel(FakeSettingsRepository(), DebugSource { error("refused") }); vm.testConnection()
        assertEquals(ConnectionStatus.Failed("refused"), vm.state.value.connection)
    }

    @Test fun unhealthySnapshotIsAFailure() {
        val vm = OnboardingViewModel(FakeSettingsRepository(), DebugSource { DebugSnapshot(false, "", 0, emptyList(), "Wrong API key") }); vm.testConnection()
        assertEquals(ConnectionStatus.Failed("Wrong API key"), vm.state.value.connection)
    }

    @Test fun permissionStateIsStoredAndSurvivesReplay() {
        val vm = OnboardingViewModel(FakeSettingsRepository(), healthy)
        vm.setPermissions(mapOf(PermissionGroup.Microphone to true)); vm.replay()
        assertEquals(true, vm.state.value.permissions[PermissionGroup.Microphone])
    }
}
