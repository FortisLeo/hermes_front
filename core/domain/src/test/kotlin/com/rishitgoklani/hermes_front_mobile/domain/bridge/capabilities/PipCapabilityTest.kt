package com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class PipCapabilityTest {
    @Test fun reportsSuccessAndFailure() = runBlocking {
        assertEquals("completed", pipFaceCapability { true }.executor.execute(JsonObject(emptyMap())).status)
        assertEquals("failed", pipFaceCapability { false }.executor.execute(JsonObject(emptyMap())).status)
    }
}
