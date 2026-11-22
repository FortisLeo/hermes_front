package com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.AuditingExecutor
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.DenyListExecutor
import com.rishitgoklani.hermes_front_mobile.domain.bridge.shell.AuditLog
import com.rishitgoklani.hermes_front_mobile.domain.bridge.shell.CommandRunner
import com.rishitgoklani.hermes_front_mobile.domain.bridge.shell.DenyList
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val MAX_OUTPUT = 8_000

/** Escape hatch: run a shell command (root by default), guarded by the deny-list and audited. */
fun shellCapability(runner: CommandRunner, denyList: DenyList, audit: AuditLog): Capability {
    val raw = com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionExecutor { args ->
        val cmd = args["cmd"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (cmd.isEmpty()) return@ActionExecutor ActionResult.failed("cmd is required")
        val root = args["root"]?.jsonPrimitive?.booleanOrNull ?: true
        val r = runner.run(cmd, root)
        val out = r.stdout.take(MAX_OUTPUT)
        ActionResult(
            if (r.exitCode == 0) ActionResult.COMPLETED else ActionResult.FAILED,
            out.ifEmpty { r.stderr.take(MAX_OUTPUT) },
            buildJsonObject { put("exit_code", r.exitCode); put("stdout", out); put("stderr", r.stderr.take(MAX_OUTPUT)) },
        )
    }
    return Capability(
        name = "shell",
        description = "Run a shell command on the phone (root by default). Destructive commands are blocked. Prefer specific tools when one exists.",
        inputSchemaJson = """{"type":"object","properties":{"cmd":{"type":"string"},"root":{"type":"boolean","description":"Run as root (default true)"}},"required":["cmd"]}""",
        executor = AuditingExecutor("shell", DenyListExecutor(raw, denyList), audit),
    )
}
