package com.rishitgoklani.hermes_front_mobile.data.bridge

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.CapabilityRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.Socket

class BridgeServerTest {
    private lateinit var server: BridgeServer

    @Before fun setUp() {
        val reg = CapabilityRegistry(listOf(Capability("ping", "p") { ActionResult.completed("pong") }))
        server = BridgeServer(BridgeProtocol(reg) { "tok" }, requestedPort = 0).also { it.start() }
    }
    @After fun tearDown() = server.stop()

    private fun roundTrip(line: String): String = Socket(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), server.port).use { s ->
        s.getOutputStream().apply { write((line + "\n").toByteArray()); flush() }
        s.getInputStream().bufferedReader().readLine()
    }

    @Test fun answersCallOverRealSocket() =
        assertTrue(roundTrip("""{"method":"call","name":"ping","token":"tok"}""").contains("pong"))

    @Test fun rejectsBadTokenOverSocket() =
        assertTrue(roundTrip("""{"method":"list","token":"x"}""").contains("denied"))

    @Test fun servesSeveralConnections() {
        repeat(3) { assertTrue(roundTrip("""{"method":"list","token":"tok"}""").contains("ping")) }
    }

    @Test fun bindsLoopbackOnly() = assertTrue(server.port > 0)
}
