package com.rishitgoklani.hermes_front_mobile.ui.settings

import com.rishitgoklani.hermes_front_mobile.domain.live.LiveSettings
import com.rishitgoklani.hermes_front_mobile.testing.FakeLiveSettings
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
class LiveSettingsViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun loadsTheStoredSettings() {
        val vm = LiveSettingsViewModel(FakeLiveSettings(LiveSettings(enabled = true, apiKey = "k", voice = "Puck")))
        assertEquals("Puck", vm.state.value.settings.voice); assertEquals("k", vm.state.value.settings.apiKey)
    }

    @Test fun switchesAndVoicePersistImmediately() = runBlocking {
        val repo = FakeLiveSettings(LiveSettings()); val vm = LiveSettingsViewModel(repo)
        vm.onEnabled(true); vm.onVoice("Fenrir"); vm.onHalfDuplex(false)
        val s = repo.settings.first()
        assertTrue(s.enabled); assertEquals("Fenrir", s.voice); assertFalse(s.halfDuplex)
        assertTrue(vm.state.value.saved)
    }

    @Test fun typingTheKeyDoesNotPersistUntilSave() = runBlocking {
        val repo = FakeLiveSettings(LiveSettings()); val vm = LiveSettingsViewModel(repo)
        vm.onApiKey("AIza123"); vm.onModel("gemini-x")
        assertEquals("", repo.settings.first().apiKey); assertFalse(vm.state.value.saved)
        vm.save()
        assertEquals("AIza123", repo.settings.first().apiKey); assertEquals("gemini-x", repo.settings.first().model)
    }

    @Test fun readyNeedsBothTheSwitchAndAKey() {
        assertFalse(LiveSettings(enabled = true, apiKey = " ").ready); assertFalse(LiveSettings(enabled = false, apiKey = "k").ready); assertTrue(LiveSettings(enabled = true, apiKey = "k").ready)
    }

    @Test fun allToolsIsOnByDefaultAndTheSwitchPersistsImmediately() = runBlocking {
        val repo = FakeLiveSettings(LiveSettings()); val vm = LiveSettingsViewModel(repo)
        assertTrue(vm.state.value.settings.allTools)
        vm.onAllTools(false); assertFalse(repo.settings.first().allTools)
    }
}
