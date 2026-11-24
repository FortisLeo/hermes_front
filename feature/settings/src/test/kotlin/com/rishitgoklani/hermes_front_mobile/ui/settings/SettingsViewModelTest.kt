package com.rishitgoklani.hermes_front_mobile.ui.settings

import com.rishitgoklani.hermes_front_mobile.domain.DisplayQuality
import com.rishitgoklani.hermes_front_mobile.domain.HermesConfig
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
class SettingsViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun loadsCurrentConfig() {
        val vm = SettingsViewModel(FakeSettingsRepository(HermesConfig(baseUrl = "http://x", apiKey = "k")))
        assertEquals("http://x", vm.state.value.baseUrl)
        assertEquals("k", vm.state.value.apiKey)
    }

    @Test fun savePersistsTrimmedValuesAndMarksSaved() = runBlocking {
        val repo = FakeSettingsRepository()
        val vm = SettingsViewModel(repo)
        vm.onBaseUrl(" http://y "); vm.onApiKey(" secret ")
        assertFalse(vm.state.value.saved)
        vm.save()
        assertTrue(vm.state.value.saved)
        assertEquals("secret", repo.config.first().apiKey)
        assertEquals("http://y", repo.config.first().baseUrl)
    }

    @Test fun displayQualityDefaultsToAuto() { assertEquals(DisplayQuality.Auto, SettingsViewModel(FakeSettingsRepository()).state.value.displayQuality) }

    @Test fun displayQualityIsPersistedAndExposed() = runBlocking {
        val repo = FakeSettingsRepository(); val vm = SettingsViewModel(repo)
        vm.onDisplayQuality(DisplayQuality.Low)
        assertEquals(DisplayQuality.Low, vm.state.value.displayQuality)
        assertEquals(DisplayQuality.Low, repo.displayQuality.first())
    }

    @Test fun displayQualityLoadsStoredChoice() = runBlocking {
        val repo = FakeSettingsRepository(); repo.setDisplayQuality(DisplayQuality.High)
        assertEquals(DisplayQuality.High, SettingsViewModel(repo).state.value.displayQuality)
    }

    @Test fun parseFallsBackToAuto() { assertEquals(DisplayQuality.Auto, DisplayQuality.parse("nonsense")); assertEquals(DisplayQuality.High, DisplayQuality.parse("high")) }

    @Test fun aboutInfoIsExposed() { assertEquals("1.2", SettingsViewModel(FakeSettingsRepository(), AboutInfo("1.2", "debug")).state.value.about.versionName) }

    @Test fun editingClearsSavedFlag() {
        val vm = SettingsViewModel(FakeSettingsRepository())
        vm.save(); vm.onApiKey("z")
        assertFalse(vm.state.value.saved)
    }
}
