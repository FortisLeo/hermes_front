package com.rishitgoklani.hermes_front_mobile.domain.avatar

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AvatarClipsTest {
    private fun glb(json: String): ByteArray {
        var j = json.toByteArray(); while (j.size % 4 != 0) j += ' '.code.toByte()
        fun le(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
        return "glTF".toByteArray() + le(2) + le(20 + j.size) + le(j.size) + "JSON".toByteArray() + j
    }
    private val vrma = glb("""{"extensions":{"VRMC_vrm_animation":{"specVersion":"1.0"}}}""")

    @Test fun acceptsVrmaAndRejectsOthers() { assertNull(VrmaFile.check(vrma)); assertNotNull(VrmaFile.check(glb("{}"))); assertNotNull(VrmaFile.check(byteArrayOf(1))) }
    @Test fun nameIsSanitised() { assertEquals("Hip_Hop-1", VrmaFile.nameFrom("/sdcard/Download/Hip Hop_1!.vrma".replace("Hip Hop", "Hip_Hop").replace("_1", "-1"))) }
    @Test fun importInstallsOnlyValidClips() = runBlocking {
        val got = mutableListOf<String>()
        val ok = avatarImportClipCapability({ vrma }) { n, _ -> got += n }; val bad = avatarImportClipCapability({ glb("{}") }) { n, _ -> got += n }
        assertEquals("completed", ok.executor.execute(buildJsonObject { put("path", "/x/dance one.vrma") }).status); assertEquals("failed", bad.executor.execute(buildJsonObject { put("path", "/x/y.vrma") }).status)
        assertEquals(listOf("danceone"), got)
    }
    @Test fun playRequiresKnownClipAndStopClears() = runBlocking {
        val c = AvatarController(); val cap = avatarClipCapability(c) { listOf("wave1") }
        assertEquals("failed", cap.executor.execute(buildJsonObject { put("action", "play"); put("name", "nope") }).status)
        assertEquals("completed", cap.executor.execute(buildJsonObject { put("action", "play"); put("name", "wave1") }).status); assertEquals("wave1", c.state.value.clip)
        cap.executor.execute(buildJsonObject { put("action", "stop") }); assertEquals("", c.state.value.clip)
    }
}
