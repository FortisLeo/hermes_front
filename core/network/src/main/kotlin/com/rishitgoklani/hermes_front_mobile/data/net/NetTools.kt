package com.rishitgoklani.hermes_front_mobile.data.net

import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/** Pure/JVM building blocks behind the network tools (kept separate from the Capability wrappers so they are easy to test). */
object NetTools {
    /** 102-byte Wake-on-LAN magic packet: 6x0xFF + MAC x16. Accepts aa:bb:cc:dd:ee:ff, aa-bb-..., or aabbccddeeff. */
    fun magicPacket(mac: String): ByteArray? {
        val hex = mac.replace(Regex("[:\\-.]"), "").lowercase()
        if (!Regex("^[0-9a-f]{12}$").matches(hex)) return null
        val m = ByteArray(6) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        return ByteArray(6) { 0xFF.toByte() } + ByteArray(96) { m[it % 6] }
    }

    fun sendWol(mac: String, broadcast: String = "255.255.255.255", port: Int = 9): Boolean {
        val p = magicPacket(mac) ?: return false
        DatagramSocket().use { s -> s.broadcast = true; s.send(DatagramPacket(p, p.size, InetAddress.getByName(broadcast), port)) }
        return true
    }

    /** Hosts for a CIDR like 192.168.0.0/24 (excludes network and broadcast). Max /22 (1022 hosts) to avoid accidental huge scans. */
    fun hostsIn(cidr: String): List<String>? {
        val parts = cidr.trim().split("/"); if (parts.size != 2) return null
        val bits = parts[1].toIntOrNull() ?: return null; if (bits !in 22..30) return null
        val o = parts[0].split(".").map { it.toIntOrNull() ?: return null }; if (o.size != 4 || o.any { it !in 0..255 }) return null
        val base = (o[0].toLong() shl 24) or (o[1].toLong() shl 16) or (o[2].toLong() shl 8) or o[3].toLong()
        val mask = (0xFFFFFFFFL shl (32 - bits)) and 0xFFFFFFFFL
        val net = base and mask; val count = (1L shl (32 - bits)) - 2
        return (1..count).map { val v = net + it; "${(v shr 24) and 255}.${(v shr 16) and 255}.${(v shr 8) and 255}.${v and 255}" }
    }

    fun tcpOpen(host: String, port: Int, timeoutMs: Int = 400): Boolean = try { Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs); true } } catch (e: Exception) { false }

    /** Concurrent TCP connect sweep; returns host -> open ports. Uses a bounded thread pool. */
    fun scan(hosts: List<String>, ports: List<Int>, timeoutMs: Int = 400, threads: Int = 48): Map<String, List<Int>> {
        val pool = java.util.concurrent.Executors.newFixedThreadPool(threads); val out = java.util.concurrent.ConcurrentHashMap<String, MutableList<Int>>()
        try {
            hosts.flatMap { h -> ports.map { p -> pool.submit { if (tcpOpen(h, p, timeoutMs)) out.getOrPut(h) { java.util.Collections.synchronizedList(mutableListOf()) }.add(p) } } }.forEach { runCatching { it.get() } }
        } finally { pool.shutdownNow() }
        return out.mapValues { it.value.sorted() }.toSortedMap(compareBy { h -> h.split(".").map { it.toInt() }.fold(0L) { a, b -> a * 256 + b } })
    }

    /** MQTT 3.1.1 CONNECT packet (clean session, keepalive 30). */
    fun mqttConnect(clientId: String, user: String? = null, pass: String? = null): ByteArray {
        val flags = (0x02 or (if (user != null) 0x80 else 0) or (if (pass != null) 0x40 else 0))
        val payload = ByteArrayOutputStream().also { it.write(str(clientId)); user?.let { u -> it.write(str(u)) }; pass?.let { p -> it.write(str(p)) } }.toByteArray()
        val vh = byteArrayOf(0, 4, 'M'.code.toByte(), 'Q'.code.toByte(), 'T'.code.toByte(), 'T'.code.toByte(), 4, flags.toByte(), 0, 30)
        return byteArrayOf(0x10) + remainingLength(vh.size + payload.size) + vh + payload
    }
    /** MQTT PUBLISH, QoS 0. */
    fun mqttPublish(topic: String, payload: ByteArray, retain: Boolean = false): ByteArray {
        val body = str(topic) + payload
        return byteArrayOf((0x30 or (if (retain) 1 else 0)).toByte()) + remainingLength(body.size) + body
    }
    private fun str(s: String): ByteArray { val b = s.toByteArray(); return byteArrayOf((b.size shr 8).toByte(), b.size.toByte()) + b }
    internal fun remainingLength(n: Int): ByteArray { var x = n; val out = ByteArrayOutputStream(); do { var d = x % 128; x /= 128; if (x > 0) d = d or 128; out.write(d) } while (x > 0); return out.toByteArray() }

    /** Connects, publishes one message, disconnects. Returns null on success, else an error message. */
    fun publishMqtt(host: String, port: Int, topic: String, message: String, user: String?, pass: String?, retain: Boolean, timeoutMs: Int = 4000): String? = try {
        Socket().use { s ->
            s.soTimeout = timeoutMs; s.connect(InetSocketAddress(host, port), timeoutMs)
            val out = s.getOutputStream(); val inp = s.getInputStream()
            out.write(mqttConnect("deskbot-${System.nanoTime() % 100000}", user, pass)); out.flush()
            val ack = ByteArray(4); var n = 0; while (n < 4) { val r = inp.read(ack, n, 4 - n); if (r < 0) break; n += r }
            if (n < 4 || ack[0].toInt() != 0x20 || ack[3].toInt() != 0) "broker refused connection (code ${if (n == 4) ack[3].toInt() else -1})"
            else { out.write(mqttPublish(topic, message.toByteArray(), retain)); out.write(byteArrayOf(0xE0.toByte(), 0)); out.flush(); null }
        }
    } catch (e: Exception) { e.message ?: e::class.simpleName }
}
