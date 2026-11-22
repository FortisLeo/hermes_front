package com.rishitgoklani.hermes_front_mobile.domain.bridge.shell

data class CommandResult(val exitCode: Int, val stdout: String, val stderr: String = "")

/** Port: runs one shell command, optionally as root. */
interface CommandRunner {
    suspend fun run(command: String, root: Boolean): CommandResult
}

/** Port: append-only record of every tool call (tool, args, outcome). */
interface AuditLog {
    fun record(tool: String, args: String, status: String)
}
