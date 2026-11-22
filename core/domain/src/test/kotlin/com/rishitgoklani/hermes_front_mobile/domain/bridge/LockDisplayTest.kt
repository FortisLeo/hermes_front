package com.rishitgoklani.hermes_front_mobile.domain.bridge

import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.lockDisplayCapability
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LockDisplayTest {
    private fun a(v: String) = JsonObject(mapOf("action" to JsonPrimitive(v)))
    @Test fun onOffStatusPersistAndApply() = runBlocking {
        var enabled = false; var applied = 0; val cap = lockDisplayCapability({ enabled }, { enabled = it }, { applied++ })
        assertEquals("completed", cap.executor.execute(a("on")).status); assertTrue(enabled); assertEquals(1, applied)
        assertEquals("on", cap.executor.execute(a("status")).message); cap.executor.execute(a("off")); assertTrue(!enabled); assertEquals(2, applied); assertEquals("failed", cap.executor.execute(a("maybe")).status)
    }
}
