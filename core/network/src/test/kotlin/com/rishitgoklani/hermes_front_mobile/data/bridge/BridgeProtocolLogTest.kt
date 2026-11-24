package com.rishitgoklani.hermes_front_mobile.data.bridge

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.CapabilityRegistry
import com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiSource
import com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiStatus
import com.rishitgoklani.hermes_front_mobile.domain.diagnostics.InMemoryApiLog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeProtocolLogTest {
    private val log = InMemoryApiLog(secrets = { listOf("bridge-token-123") })
    private var killed = false
    private val protocol = BridgeProtocol(CapabilityRegistry(listOf(
        Capability("echo", "e") { ActionResult.completed("said ${it["text"]}") },
        Capability("boom", "b") { error("kaput") },
    )), { killed }, log) { "bridge-token-123" }
    private fun send(line: String) = runBlocking { protocol.handle(line) }
    private val newest get() = log.entries.value.first()

    @Test fun aCallRecordsItsArgumentsAndResult() {
        send("""{"method":"call","name":"echo","arguments":{"text":"hi"},"token":"bridge-token-123"}""")
        assertEquals(ApiSource.Bridge, newest.source); assertEquals("call echo", newest.label)
        assertTrue("hi" in newest.request); assertTrue("completed: said" in newest.response); assertEquals(ApiStatus.Ok, newest.status)
    }

    @Test fun aThrowingToolIsAFailedEntry() {
        send("""{"method":"call","name":"boom","token":"bridge-token-123"}""")
        assertEquals(ApiStatus.Error, newest.status); assertTrue("kaput" in newest.response)
    }

    @Test fun unknownToolsAndBadTokensAreFailures() {
        send("""{"method":"call","name":"nope","token":"bridge-token-123"}""")
        assertEquals(ApiStatus.Error, newest.status)
        send("""{"method":"call","name":"echo","token":"wrong"}""")
        assertEquals(ApiStatus.Error, newest.status); assertTrue("unauthorized" in newest.label)
    }

    @Test fun theTokenNeverReachesTheLog() {
        send("""{"method":"call","name":"echo","arguments":{"text":"bridge-token-123"},"token":"bridge-token-123"}""")
        assertTrue(log.entries.value.none { it.request.contains("bridge-token-123") || it.response.contains("bridge-token-123") })
    }

    @Test fun listAndKillSwitchAreRecorded() {
        send("""{"method":"list","token":"bridge-token-123"}"""); assertEquals("list", newest.label); assertTrue("2 tools" in newest.response)
        killed = true; send("""{"method":"call","name":"echo","token":"bridge-token-123"}""")
        assertEquals(ApiStatus.Error, newest.status); assertTrue("Kill switch" in newest.response)
    }
}
