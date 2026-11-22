package com.rishitgoklani.hermes_front_mobile.data.net

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.system.FileSandbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import java.net.InetAddress

private fun JsonObject.s(k: String) = this[k]?.jsonPrimitive?.contentOrNull
private fun JsonObject.i(k: String) = this[k]?.jsonPrimitive?.intOrNull
private val COMMON_PORTS = listOf(22, 53, 80, 443, 445, 554, 1883, 5555, 8000, 8080, 8123, 8443)

fun netCapabilities(sandbox: FileSandbox, localSubnet: () -> String?, client: OkHttpClient = OkHttpClient()): List<Capability> {
    var server: FolderServer? = null
    val denied = ActionResult(ActionResult.DENIED, "Path outside allowed folders")
    return listOf(
        Capability("wake_on_lan", "Send a Wake-on-LAN magic packet to a MAC address (optional broadcast address).", """{"type":"object","properties":{"mac":{"type":"string"},"broadcast":{"type":"string"}},"required":["mac"]}""") { a ->
            withContext(Dispatchers.IO) { if (NetTools.sendWol(a.s("mac").orEmpty(), a.s("broadcast") ?: "255.255.255.255")) ActionResult.completed("Magic packet sent") else ActionResult.failed("invalid MAC address") }
        },
        Capability("port_check", "Check which TCP ports are open on a host (default common ports).", """{"type":"object","properties":{"host":{"type":"string"},"ports":{"type":"array","items":{"type":"integer"}}},"required":["host"]}""") { a ->
            val host = a.s("host").orEmpty().ifBlank { return@Capability ActionResult.failed("host required") }
            val ports = (a["ports"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.intOrNull }?.filter { it in 1..65535 }?.take(100)?.ifEmpty { null } ?: COMMON_PORTS
            val open = withContext(Dispatchers.IO) { NetTools.scan(listOf(host), ports)[host].orEmpty() }
            ActionResult.completed(if (open.isEmpty()) "No open ports among ${ports.size}" else "open: ${open.joinToString()}", buildJsonObject { put("open", buildJsonArray { open.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }) })
        },
        Capability("lan_scan", "Scan your own LAN subnet for hosts with common open ports (subnet like 192.168.0.0/24, default: this phone's). Only scan networks you own.", """{"type":"object","properties":{"subnet":{"type":"string"},"ports":{"type":"array","items":{"type":"integer"}}}}""") { a ->
            val cidr = a.s("subnet") ?: localSubnet() ?: return@Capability ActionResult.failed("no subnet (not on Wi-Fi?)")
            val hosts = NetTools.hostsIn(cidr) ?: return@Capability ActionResult.failed("invalid subnet (use CIDR /22../30)")
            val ports = (a["ports"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.intOrNull }?.filter { it in 1..65535 }?.take(20)?.ifEmpty { null } ?: listOf(22, 53, 80, 443, 445, 8080)
            val found = withContext(Dispatchers.IO) { NetTools.scan(hosts, ports, 300, 64) }
            ActionResult.completed(if (found.isEmpty()) "No hosts answered on ${ports.joinToString()}" else found.entries.joinToString("\n") { "${it.key}: ${it.value.joinToString()}" }, buildJsonObject { put("hosts", found.size); put("subnet", cidr) })
        },
        Capability("dns_lookup", "Resolve a hostname to addresses.", """{"type":"object","properties":{"host":{"type":"string"}},"required":["host"]}""") { a ->
            try { val ips = withContext(Dispatchers.IO) { InetAddress.getAllByName(a.s("host").orEmpty()).map { it.hostAddress } }; ActionResult.completed(ips.joinToString()) } catch (e: Exception) { ActionResult.failed("lookup failed: ${e.message}") }
        },
        Capability("mqtt_publish", "Publish one MQTT message (QoS 0) to a broker, e.g. Home Assistant / smart plugs. host, port (1883), topic, message, optional user/pass/retain.",
            """{"type":"object","properties":{"host":{"type":"string"},"port":{"type":"integer"},"topic":{"type":"string"},"message":{"type":"string"},"user":{"type":"string"},"pass":{"type":"string"},"retain":{"type":"boolean"}},"required":["host","topic","message"]}""") { a ->
            val err = withContext(Dispatchers.IO) { NetTools.publishMqtt(a.s("host").orEmpty(), a.i("port") ?: 1883, a.s("topic").orEmpty(), a.s("message").orEmpty(), a.s("user"), a.s("pass"), a["retain"]?.jsonPrimitive?.contentOrNull == "true") }
            if (err == null) ActionResult.completed("Published to ${a.s("topic")}") else ActionResult.failed("MQTT: $err")
        },
        Capability("download_file", "Download an http(s) URL into an allowed folder with resume and optional sha256 verification.", """{"type":"object","properties":{"url":{"type":"string"},"dest":{"type":"string"},"sha256":{"type":"string"}},"required":["url","dest"]}""") { a ->
            val dest = sandbox.resolve(a.s("dest").orEmpty()) ?: return@Capability denied
            try { val r = withContext(Dispatchers.IO) { Downloads.fetch(client, a.s("url").orEmpty(), dest, a.s("sha256")) }; ActionResult.completed("${r.bytes / 1024} KB${if (r.resumed) " (resumed)" else ""} sha256=${r.sha256.take(12)}", buildJsonObject { put("bytes", r.bytes); put("sha256", r.sha256) }) }
            catch (e: Exception) { ActionResult.failed("download failed: ${e.message}") }
        },
        Capability("serve_folder", "Start/stop a read-only HTTP file server for a folder on the LAN (action=start|stop|status, optional token). Remember to stop it.", """{"type":"object","properties":{"action":{"type":"string","enum":["start","stop","status"]},"path":{"type":"string"},"port":{"type":"integer"},"token":{"type":"string"}},"required":["action"]}""") { a ->
            when (a.s("action")) {
                "start" -> { if (server != null) return@Capability ActionResult.failed("already running on ${server!!.port}"); val dir = sandbox.resolve(a.s("path").orEmpty()) ?: return@Capability denied
                    if (!dir.isDirectory) return@Capability ActionResult.failed("not a folder"); val s = FolderServer(dir, a.s("token")); val p = s.start(a.i("port") ?: 0); server = s; ActionResult.completed("Serving ${dir.name} on port $p", buildJsonObject { put("port", p) }) }
                "stop" -> { server?.stop(); server = null; ActionResult.completed("Stopped") }
                "status" -> ActionResult.completed(if (server != null) "running on ${server!!.port}" else "stopped")
                else -> ActionResult.failed("action must be start, stop or status")
            }
        },
    )
}
