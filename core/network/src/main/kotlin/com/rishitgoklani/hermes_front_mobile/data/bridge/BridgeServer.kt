package com.rishitgoklani.hermes_front_mobile.data.bridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/** Loopback-only TCP server; one request line in, one response line out per connection. */
class BridgeServer(private val protocol: BridgeProtocol, private val requestedPort: Int = 8643) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: ServerSocket? = null
    private var acceptJob: Job? = null

    /** The bound port (useful when started with port 0 in tests). */
    val port: Int get() = server?.localPort ?: -1

    fun start() {
        check(server == null) { "already started" }
        val s = ServerSocket(requestedPort, 16, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
        server = s
        acceptJob = scope.launch {
            while (!s.isClosed) {
                val client = try { s.accept() } catch (e: java.io.IOException) { break }
                scope.launch { serve(client) }
            }
        }
    }

    fun stop() {
        server?.close(); server = null
        acceptJob?.cancel(); scope.cancel()
    }

    private suspend fun serve(client: Socket) {
        client.use {
            client.soTimeout = 10_000
            val reader = BufferedReader(client.getInputStream().reader())
            val line = runCatching { reader.readLine() }.getOrNull()
            if (line == null || line.length > MAX_LINE) return
            val response = protocol.handle(line)
            client.getOutputStream().apply { write((response + "\n").toByteArray()); flush() }
        }
    }

    private companion object { const val MAX_LINE = 256 * 1024 }
}
