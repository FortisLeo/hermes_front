package com.rishitgoklani.hermes_front_mobile.testing

import com.rishitgoklani.hermes_front_mobile.domain.bridge.shell.AuditLog
import com.rishitgoklani.hermes_front_mobile.domain.bridge.shell.CommandResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.shell.CommandRunner

class FakeCommandRunner(private val result: CommandResult = CommandResult(0, "ok")) : CommandRunner {
    val calls = mutableListOf<Pair<String, Boolean>>()
    override suspend fun run(command: String, root: Boolean): CommandResult { calls += command to root; return result }
}

class FakeAuditLog : AuditLog {
    val entries = mutableListOf<Triple<String, String, String>>()
    override fun record(tool: String, args: String, status: String) { entries += Triple(tool, args, status) }
}
