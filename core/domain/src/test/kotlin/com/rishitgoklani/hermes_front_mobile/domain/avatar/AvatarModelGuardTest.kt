package com.rishitgoklani.hermes_front_mobile.domain.avatar

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarModelGuardTest {
    private fun big() = ByteArray(51 * 1024 * 1024)

    @Test fun hugeFilesAreRefusedWithAReason() { assertTrue(AvatarModelGuard.problem(big())!!.contains("limit is 50 MB")) }
    @Test fun modelsWithBigTexturesAreAcceptedBecauseTheViewerShrinksThem() {
        val glb = TestGlb.build(List(6) { TestGlb.png(4096, 4096) }); assertNull(AvatarModelGuard.problem(glb)); assertEquals(1024, AvatarModelGuard.textureCap(glb))
    }
    @Test fun installerRefusesOversizedModelsBeforeInstalling() = runBlocking {
        var n = 0
        assertTrue(AvatarInstaller { n++ }.install(big(), enforceLicense = false) is InstallResult.Rejected); assertEquals(0, n)
    }
    @Test fun importToolAlsoRefusesOversizedModels() = runBlocking {
        var n = 0
        val r = avatarImportCapability({ big() }) { n++ }.executor.execute(buildJsonObject { put("path", JsonPrimitive("/x.vrm")) })
        assertEquals("failed", r.status); assertEquals(0, n)
    }
}
