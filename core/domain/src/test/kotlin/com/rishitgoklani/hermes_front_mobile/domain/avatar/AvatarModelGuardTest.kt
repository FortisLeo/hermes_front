package com.rishitgoklani.hermes_front_mobile.domain.avatar

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarModelGuardTest {
    @Test fun smallModelsPassAndHugeOnesAreRefusedWithAReason() {
        assertNull(AvatarModelGuard.sizeProblem(5 * 1024 * 1024)); assertTrue(AvatarModelGuard.sizeProblem(20 * 1024 * 1024)!!.contains("up to 12 MB"))
    }
    @Test fun rollbackOnlyWhenTheNewModelNeverProvedItself() {
        assertEquals(AvatarModelGuard.Action.None, AvatarModelGuard.onStart(false, true))
        assertEquals(AvatarModelGuard.Action.RestorePrevious, AvatarModelGuard.onStart(true, true))
        assertEquals(AvatarModelGuard.Action.Remove, AvatarModelGuard.onStart(true, false))
    }
    @Test fun installerRefusesOversizedModelsBeforeInstalling() = runBlocking {
        var n = 0; val big = ByteArray(13 * 1024 * 1024)
        assertTrue(AvatarInstaller { n++ }.install(big, enforceLicense = false) is InstallResult.Rejected); assertEquals(0, n)
    }
    @Test fun importToolAlsoRefusesOversizedModels() = runBlocking {
        var n = 0
        val r = avatarImportCapability({ ByteArray(13 * 1024 * 1024) }) { n++ }.executor.execute(kotlinx.serialization.json.buildJsonObject { put("path", kotlinx.serialization.json.JsonPrimitive("/x.vrm")) })
        assertEquals("failed", r.status); assertEquals(0, n)
    }
}
