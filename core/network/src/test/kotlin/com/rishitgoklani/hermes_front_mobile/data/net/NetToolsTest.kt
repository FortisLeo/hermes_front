package com.rishitgoklani.hermes_front_mobile.data.net

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.net.URL

class NetToolsTest {
    @get:Rule val tmp = TemporaryFolder()
    private val servers = mutableListOf<MockWebServer>()
    @After fun down() = servers.forEach { runCatching { it.shutdown() } }

    @Test fun magicPacketLayout() {
        val p = NetTools.magicPacket("AA:bb:cc:dd:ee:ff")!!; assertEquals(102, p.size); assertTrue(p.take(6).all { it == 0xFF.toByte() }); assertEquals(0xAA.toByte(), p[6]); assertEquals(0xFF.toByte(), p[101])
        assertNotNull(NetTools.magicPacket("aa-bb-cc-dd-ee-ff")); assertNotNull(NetTools.magicPacket("aabbccddeeff")); assertNull(NetTools.magicPacket("zz:bb:cc:dd:ee:ff")); assertNull(NetTools.magicPacket("aa:bb"))
    }
    @Test fun hostsInCidr() {
        assertEquals(254, NetTools.hostsIn("192.168.0.0/24")!!.size); assertEquals("192.168.0.1", NetTools.hostsIn("192.168.0.77/24")!!.first()); assertEquals(2, NetTools.hostsIn("10.0.0.0/30")!!.size)
        assertNull(NetTools.hostsIn("10.0.0.0/8")); assertNull(NetTools.hostsIn("nonsense")); assertNull(NetTools.hostsIn("300.1.1.1/24"))
    }
    @Test fun scanFindsOnlyOpenPorts() {
        ServerSocket(0).use { s -> val open = s.localPort
            val r = NetTools.scan(listOf("127.0.0.1"), listOf(open, 1), timeoutMs = 300); assertEquals(mapOf("127.0.0.1" to listOf(open)), r) }
    }
    @Test fun mqttPacketsEncodeCorrectly() {
        val c = NetTools.mqttConnect("id1"); assertEquals(0x10.toByte(), c[0]); assertEquals("MQTT", String(c, 4, 4)); assertEquals(4.toByte(), c[8])
        val p = NetTools.mqttPublish("a/b", "hi".toByteArray()); assertEquals(0x30.toByte(), p[0]); assertEquals(2 + 3 + 2, p[1].toInt()); assertEquals(0x31.toByte(), NetTools.mqttPublish("t", ByteArray(0), retain = true)[0])
        assertArrayEquals(byteArrayOf(0x80.toByte(), 0x01), NetTools.remainingLength(128)); assertArrayEquals(byteArrayOf(0x7F), NetTools.remainingLength(127))
    }
    @Test fun publishAgainstFakeBroker() {
        val got = java.util.concurrent.atomic.AtomicReference<String>()
        ServerSocket(0).use { b -> val t = Thread { b.accept().use { s -> val i = s.getInputStream(); val first = ByteArray(64); i.read(first); s.getOutputStream().write(byteArrayOf(0x20, 2, 0, 0)); s.getOutputStream().flush(); Thread.sleep(150); val rest = ByteArray(64); val n = i.read(rest); got.set(String(rest, 0, maxOf(n, 0))) } }.also { it.start() }
            assertNull(NetTools.publishMqtt("127.0.0.1", b.localPort, "home/lamp", "ON", null, null, false)); t.join(3000) }
        assertTrue(got.get().contains("home/lamp") && got.get().contains("ON"))
    }
    @Test fun publishReportsRefusedAndUnreachable() {
        ServerSocket(0).use { b -> Thread { b.accept().use { s -> s.getInputStream().read(ByteArray(64)); s.getOutputStream().write(byteArrayOf(0x20, 2, 0, 5)); s.getOutputStream().flush() } }.start()
            assertTrue(NetTools.publishMqtt("127.0.0.1", b.localPort, "t", "m", null, null, false)!!.contains("refused")) }
        val dead = ServerSocket(0).use { it.localPort }; assertNotNull(NetTools.publishMqtt("127.0.0.1", dead, "t", "m", null, null, false, 500))
    }

    private fun get(url: String): Pair<Int, String> { val c = URL(url).openConnection() as java.net.HttpURLConnection; return c.responseCode to (runCatching { (if (c.responseCode < 400) c.inputStream else c.errorStream).bufferedReader().readText() }.getOrDefault("")) }
    @Test fun folderServerServesFilesListsAndBlocksTraversalAndWrites() {
        val root = tmp.newFolder("srv"); File(root, "a.txt").writeText("hello"); File(root, "sub").mkdirs(); File(tmp.root, "secret.txt").writeText("no")
        val s = FolderServer(root, bindAddress = java.net.InetAddress.getLoopbackAddress()); val port = s.start()
        try {
            assertEquals(200 to "hello", get("http://127.0.0.1:$port/a.txt")); assertTrue(get("http://127.0.0.1:$port/").second.contains("a.txt")); assertEquals(404, get("http://127.0.0.1:$port/zz").first)
            Socket("127.0.0.1", port).use { sock -> sock.getOutputStream().write("GET /../secret.txt HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray()); assertTrue(sock.getInputStream().bufferedReader().readLine().contains("403")) }
            Socket("127.0.0.1", port).use { sock -> sock.getOutputStream().write("POST /a.txt HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray()); assertTrue(sock.getInputStream().bufferedReader().readLine().contains("405")) }
        } finally { s.stop() }
    }
    @Test fun folderServerTokenRequired() {
        val root = tmp.newFolder("srv2"); File(root, "a.txt").writeText("x"); val s = FolderServer(root, "tok", java.net.InetAddress.getLoopbackAddress()); val port = s.start()
        try { assertEquals(401, get("http://127.0.0.1:$port/a.txt").first); assertEquals(200, get("http://127.0.0.1:$port/a.txt?token=tok").first) } finally { s.stop() }
    }

    private fun mock(vararg r: MockResponse) = MockWebServer().also { servers += it; r.forEach { x -> it.enqueue(x) }; it.start() }
    @Test fun downloadFreshAndChecksum() {
        val s = mock(MockResponse().setBody("abcdef")); val f = File(tmp.root, "d/x.bin"); val r = Downloads.fetch(OkHttpClient(), s.url("/x").toString(), f)
        assertEquals(6, r.bytes); assertFalse(r.resumed); assertEquals(Downloads.sha256(f), r.sha256); assertEquals("abcdef", f.readText())
    }
    @Test fun downloadResumesWithRange() {
        val s = mock(MockResponse().setResponseCode(206).setBody("def")); val f = File(tmp.root, "p.bin"); f.writeText("abc")
        val r = Downloads.fetch(OkHttpClient(), s.url("/x").toString(), f); assertTrue(r.resumed); assertEquals("abcdef", f.readText()); assertEquals("bytes=3-", s.takeRequest().getHeader("Range"))
    }
    @Test fun downloadRestartsWhenServerIgnoresRange() {
        val s = mock(MockResponse().setBody("whole")); val f = File(tmp.root, "q.bin"); f.writeText("junk"); val r = Downloads.fetch(OkHttpClient(), s.url("/x").toString(), f); assertFalse(r.resumed); assertEquals("whole", f.readText())
    }
    @Test fun downloadChecksumMismatchDeletesFile() {
        val s = mock(MockResponse().setBody("abc")); val f = File(tmp.root, "c.bin")
        assertThrows(java.io.IOException::class.java) { Downloads.fetch(OkHttpClient(), s.url("/x").toString(), f, "0".repeat(64)) }; assertFalse(f.exists())
        val s2 = mock(MockResponse().setBody("abc")); assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Downloads.fetch(OkHttpClient(), s2.url("/x").toString(), File(tmp.root, "ok.bin"), "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD").sha256)
    }
    @Test fun downloadRejectsBadSchemeAndHttpError() {
        assertThrows(java.io.IOException::class.java) { Downloads.fetch(OkHttpClient(), "file:///etc/passwd", File(tmp.root, "z")) }
        val s = mock(MockResponse().setResponseCode(404)); assertThrows(java.io.IOException::class.java) { Downloads.fetch(OkHttpClient(), s.url("/x").toString(), File(tmp.root, "z2")) }
    }
}
