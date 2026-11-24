package com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.shell.CommandRunner
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pure part of the diagnostics tools: which shell command answers a question, and how to turn the raw output
 * into a short speakable line. One tool call replaces a skill recipe of several shell round trips.
 */
object Diagnostics {
    val SECTIONS = listOf("memory", "storage", "uptime", "thermal", "cpu", "kernel")
    val DEFAULT_SECTIONS = listOf("memory", "storage", "uptime", "thermal")

    fun command(section: String): String? = when (section) {
        "memory" -> "grep -E '^(MemTotal|MemAvailable|SwapTotal|SwapFree):' /proc/meminfo"
        "storage" -> "df -k /data | tail -1"
        "uptime" -> "cat /proc/uptime"
        "thermal" -> "cat /sys/class/thermal/thermal_zone0/temp"
        "cpu" -> "top -b -n 1 -m 5 | tail -6"
        "kernel" -> "uname -r; getprop ro.build.fingerprint"
        else -> null
    }

    /** One shell script for all sections, each preceded by a `##name` marker line. */
    fun script(sections: List<String>): String = sections.mapNotNull { s -> command(s)?.let { "echo '##$s'; $it" } }.joinToString("; ")

    fun split(raw: String): Map<String, String> {
        val out = linkedMapOf<String, StringBuilder>(); var cur: StringBuilder? = null
        for (line in raw.lines()) {
            if (line.startsWith("##")) { cur = StringBuilder().also { out[line.removePrefix("##").trim()] = it } } else cur?.appendLine(line)
        }
        return out.mapValues { it.value.toString().trim() }
    }

    private fun gb(kb: Long) = "%.1f GB".format(kb / 1_048_576.0)

    fun summarize(section: String, raw: String): String = when (section) {
        "memory" -> {
            val m = raw.lines().mapNotNull { l -> l.split(":").takeIf { it.size == 2 }?.let { it[0].trim() to (it[1].trim().split(" ").firstOrNull()?.toLongOrNull() ?: 0L) } }.toMap()
            val total = m["MemTotal"] ?: 0; val avail = m["MemAvailable"] ?: 0
            if (total == 0L) "memory: unknown" else "memory: ${gb(avail)} free of ${gb(total)}" + (m["SwapTotal"]?.takeIf { it > 0 }?.let { ", swap ${gb(m["SwapFree"] ?: 0)} free of ${gb(it)}" } ?: "")
        }
        "storage" -> raw.trim().split(Regex("\\s+")).let { f ->
            if (f.size >= 5) "storage: ${gb(f[3].toLongOrNull() ?: 0)} free of ${gb(f[1].toLongOrNull() ?: 0)} (${f[4]} used)" else "storage: unknown" }
        "uptime" -> raw.trim().split(" ").firstOrNull()?.toDoubleOrNull()?.let { s ->
            val h = (s / 3600).toInt(); "uptime: ${h / 24}d ${h % 24}h ${((s % 3600) / 60).toInt()}m" } ?: "uptime: unknown"
        "thermal" -> raw.trim().toLongOrNull()?.let { t -> "temperature: %.1f C".format(if (t > 1000) t / 1000.0 else t.toDouble()) } ?: "temperature: unknown"
        "cpu" -> "cpu (top):\n" + raw.trim().lines().take(6).joinToString("\n") { it.trim().take(100) }
        "kernel" -> "kernel: " + raw.trim().lines().joinToString(" | ")
        else -> "$section: $raw"
    }

    private val PKG = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
    fun validPackage(p: String?) = p != null && PKG.matches(p)

    fun packageScript(pkg: String) = listOf(
        "echo '##path'; pm path $pkg | head -1", "echo '##version'; dumpsys package $pkg | grep -m1 versionName",
        "echo '##granted'; dumpsys package $pkg | grep -c 'granted=true'", "echo '##bucket'; am get-standby-bucket $pkg",
        "echo '##disabled'; pm list packages -d | grep -c ':$pkg\$'",
    ).joinToString("; ")

    fun summarizePackage(pkg: String, raw: String): String? {
        val m = split(raw)
        if (m["path"].isNullOrBlank()) return null
        val version = m["version"].orEmpty().substringAfter("=", "?").trim()
        val bucket = m["bucket"].orEmpty().trim().ifBlank { "?" }
        return "$pkg $version, ${if (m["disabled"]?.trim() == "0") "enabled" else "disabled"}, ${m["granted"]?.trim() ?: "?"} permissions granted, standby bucket $bucket"
    }

    fun logCommand(kind: String, lines: Int): String? {
        val n = lines.coerceIn(1, 100)
        return when (kind) {
            "crash" -> "logcat -d -b crash -t $n"
            "errors" -> "logcat -d -t 400 '*:E' | tail -n $n"
            "recent" -> "logcat -d -t $n"
            "kernel" -> "dmesg | tail -n $n"
            else -> null
        }
    }

    data class Radio(val setCmd: (Boolean) -> String, val readCmd: String)
    val RADIOS = mapOf(
        "wifi" to Radio({ "svc wifi ${if (it) "enable" else "disable"}" }, "settings get global wifi_on"),
        "bluetooth" to Radio({ "svc bluetooth ${if (it) "enable" else "disable"}" }, "settings get global bluetooth_on"),
        "mobile_data" to Radio({ "svc data ${if (it) "enable" else "disable"}" }, "settings get global mobile_data"),
        "airplane" to Radio({ "cmd connectivity airplane-mode ${if (it) "enable" else "disable"}" }, "settings get global airplane_mode_on"),
    )
}

private fun JsonObject.text(k: String) = this[k]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }

fun diagnosticsCapabilities(runner: CommandRunner): List<Capability> = listOf(
    Capability("system_report", "One-call device health: memory, storage, uptime, thermal, cpu, kernel. `sections` is a list (default memory, storage, uptime, thermal).",
        """{"type":"object","properties":{"sections":{"type":"array","items":{"type":"string","enum":["memory","storage","uptime","thermal","cpu","kernel"]}}}}""") { a ->
        val want = (a["sections"] as? JsonArray)?.map { it.jsonPrimitive.content }?.filter { it in Diagnostics.SECTIONS }?.ifEmpty { null } ?: Diagnostics.DEFAULT_SECTIONS
        val r = runner.run(Diagnostics.script(want), root = true)
        if (r.exitCode != 0 && r.stdout.isBlank()) return@Capability ActionResult.failed("Report failed: ${r.stderr.take(120)}")
        val parts = Diagnostics.split(r.stdout)
        ActionResult.completed(want.joinToString("\n") { Diagnostics.summarize(it, parts[it].orEmpty()) })
    },
    Capability("package_info", "App status in one call: installed, version, enabled, granted permission count, standby bucket.",
        """{"type":"object","properties":{"package":{"type":"string"}},"required":["package"]}""") { a ->
        val pkg = a.text("package")
        if (!Diagnostics.validPackage(pkg)) return@Capability ActionResult.failed("A valid package name is required")
        val r = runner.run(Diagnostics.packageScript(pkg!!), root = true)
        Diagnostics.summarizePackage(pkg, r.stdout)?.let { ActionResult.completed(it) } ?: ActionResult.failed("$pkg is not installed")
    },
    Capability("device_logs", "Recent logs. kind: crash | errors | recent | kernel. `lines` up to 100 (default 20).",
        """{"type":"object","properties":{"kind":{"type":"string","enum":["crash","errors","recent","kernel"]},"lines":{"type":"integer"}},"required":["kind"]}""") { a ->
        val cmd = Diagnostics.logCommand(a.text("kind").orEmpty(), a.text("lines")?.toIntOrNull() ?: 20) ?: return@Capability ActionResult.failed("kind must be crash, errors, recent or kernel")
        val r = runner.run(cmd, root = true)
        ActionResult.completed(r.stdout.trim().ifBlank { "No log lines" }.take(6000))
    },
    Capability("radio", "Read or switch a radio. name: wifi | bluetooth | mobile_data | airplane; state: get | on | off.",
        """{"type":"object","properties":{"name":{"type":"string","enum":["wifi","bluetooth","mobile_data","airplane"]},"state":{"type":"string","enum":["get","on","off"]}},"required":["name","state"]}""") { a ->
        val radio = Diagnostics.RADIOS[a.text("name")] ?: return@Capability ActionResult.failed("name must be wifi, bluetooth, mobile_data or airplane")
        val state = a.text("state")
        if (state == "on" || state == "off") {
            val s = runner.run(radio.setCmd(state == "on"), root = true)
            if (s.exitCode != 0) return@Capability ActionResult.failed("Switch failed: ${s.stderr.take(120)}")
            kotlinx.coroutines.delay(1500)
        } else if (state != "get") return@Capability ActionResult.failed("state must be get, on or off")
        val now = runner.run(radio.readCmd, root = true).stdout.trim()
        ActionResult.completed("${a.text("name")} is ${if (now == "1") "on" else "off"}")
    },
)
