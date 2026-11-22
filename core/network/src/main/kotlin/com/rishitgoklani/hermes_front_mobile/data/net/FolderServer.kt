package com.rishitgoklani.hermes_front_mobile.data.net

import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/** Tiny read-only HTTP file server for one folder (GET only, no listing outside the root, optional shared token). Stop it when done. */
class FolderServer(private val root: File, private val token: String? = null, private val bindAddress: InetAddress = InetAddress.getByAddress(byteArrayOf(0, 0, 0, 0))) {
    private var server: ServerSocket? = null
    private var thread: Thread? = null
    val port get() = server?.localPort ?: -1

    fun start(port: Int = 0): Int {
        check(server == null) { "already running" }
        val s = ServerSocket(port, 16, bindAddress); server = s
        thread = Thread { while (!s.isClosed) { val c = try { s.accept() } catch (e: Exception) { break }; Thread { runCatching { handle(c) } }.start() } }.also { it.isDaemon = true; it.start() }
        return s.localPort
    }
    fun stop() { server?.close(); server = null }

    private fun handle(c: Socket) = c.use {
        it.soTimeout = 5000
        val reader = it.getInputStream().bufferedReader(); val first = reader.readLine() ?: return
        val headers = generateSequence { reader.readLine() }.takeWhile { l -> l.isNotEmpty() }.toList()
        val out = it.getOutputStream()
        fun reply(code: Int, text: String, body: ByteArray = text.toByteArray(), type: String = "text/plain") { out.write("HTTP/1.1 $code $text\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray()); out.write(body); out.flush() }
        val parts = first.split(" "); if (parts.size < 2 || parts[0] != "GET") return reply(405, "Method Not Allowed")
        val (rawPath, query) = parts[1].split("?", limit = 2).let { p -> p[0] to p.getOrElse(1) { "" } }
        if (token != null && !(query.split("&").contains("token=$token") || headers.any { h -> h.equals("Authorization: Bearer $token", true) })) return reply(401, "Unauthorized")
        val rel = URLDecoder.decode(rawPath, "UTF-8").trimStart('/')
        val f = File(root, rel).canonicalFile; val base = root.canonicalFile
        if (f != base && !f.path.startsWith(base.path + File.separator)) return reply(403, "Forbidden")
        when {
            f.isDirectory -> reply(200, "OK", f.listFiles().orEmpty().sortedBy { n -> n.name }.joinToString("\n") { n -> n.name + if (n.isDirectory) "/" else "" }.toByteArray())
            f.isFile -> reply(200, "OK", f.readBytes(), "application/octet-stream")
            else -> reply(404, "Not Found")
        }
    }
}
