package com.rishitgoklani.hermes_front_mobile.domain.avatar

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VrmLicenseTest {
    private fun glb(json: String): ByteArray {
        var j = json.toByteArray(); while (j.size % 4 != 0) j += ' '.code.toByte()
        fun le(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
        return "glTF".toByteArray() + le(2) + le(20 + j.size) + le(j.size) + "JSON".toByteArray() + j
    }
    private fun v0(lic: String) = glb("""{"extensions":{"VRM":{"meta":{"title":"T","author":"A","licenseName":"$lic"}}}}""")

    @Test fun cc0IsAccepted() { val r = VrmLicense.check(v0("CC0")); assertTrue(r is VrmCheck.Ok); assertEquals("T", (r as VrmCheck.Ok).info.title) }
    @Test fun nonCommercialIsRejected() { assertTrue(VrmLicense.check(v0("CC_BY_NC")) is VrmCheck.Rejected); assertTrue(VrmLicense.check(v0("Redistribution_Prohibited")) is VrmCheck.Rejected) }
    @Test fun vrm1EveryoneIsAccepted() { assertTrue(VrmLicense.check(glb("""{"extensions":{"VRMC_vrm":{"meta":{"name":"N","authors":["Z"],"avatarPermission":"everyone","licenseUrl":"https://vrm.dev/licenses/1.0/"}}}}""")) is VrmCheck.Ok) }
    @Test fun vrm1OnlyAuthorIsRejected() { assertTrue(VrmLicense.check(glb("""{"extensions":{"VRMC_vrm":{"meta":{"name":"N","authors":["Z"],"avatarPermission":"onlyAuthor","licenseUrl":"https://vrm.dev/licenses/1.0/"}}}}""")) is VrmCheck.Rejected) }
    @Test fun plainGltfAndGarbageAreRejected() { assertTrue(VrmLicense.check(glb("{}")) is VrmCheck.Rejected); assertTrue(VrmLicense.check(byteArrayOf(1, 2, 3)) is VrmCheck.Rejected) }
    @Test fun toolInstallsOnlyAcceptedModels() = runBlocking {
        var installed = 0
        val good = avatarImportCapability({ v0("CC0") }) { installed++ }; val bad = avatarImportCapability({ v0("Other") }) { installed++ }
        assertEquals("completed", good.executor.execute(buildJsonObject { put("path", "/x.vrm") }).status); assertEquals("failed", bad.executor.execute(buildJsonObject { put("path", "/x.vrm") }).status); assertEquals(1, installed)
        assertEquals("failed", avatarImportCapability({ null }) { }.executor.execute(buildJsonObject { put("path", "/missing") }).status)
    }
}
