package com.rishitgoklani.hermes_front_mobile.domain.bridge

import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.shellCapability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.shell.CommandResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.shell.DenyList
import com.rishitgoklani.hermes_front_mobile.testing.FakeAuditLog
import com.rishitgoklani.hermes_front_mobile.testing.FakeCommandRunner
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class ShellCapabilityTest {
    private fun args(vararg p: Pair<String, Any>) = JsonObject(p.associate { (k, v) ->
        k to (if (v is Boolean) JsonPrimitive(v) else JsonPrimitive(v.toString())) })

    @Test fun runsAsRootByDefaultAndAudits() = runBlocking {
        val runner = FakeCommandRunner(CommandResult(0, "hello")); val audit = FakeAuditLog()
        val r = shellCapability(runner, DenyList(), audit).executor.execute(args("cmd" to "echo hello"))
        assertEquals("completed", r.status); assertEquals("hello", r.message)
        assertEquals(listOf("echo hello" to true), runner.calls)
        assertEquals("completed", audit.entries.single().third)
    }
    @Test fun nonZeroExitIsFailed() = runBlocking {
        val r = shellCapability(FakeCommandRunner(CommandResult(1, "", "nope")), DenyList(), FakeAuditLog()).executor.execute(args("cmd" to "false"))
        assertEquals("failed", r.status); assertEquals("nope", r.message)
    }
    @Test fun deniedCommandNeverReachesRunnerButIsAudited() = runBlocking {
        val runner = FakeCommandRunner(); val audit = FakeAuditLog()
        val r = shellCapability(runner, DenyList(), audit).executor.execute(args("cmd" to "dd if=/dev/zero of=/dev/block/sda"))
        assertEquals("denied", r.status); assertEquals(emptyList<Pair<String, Boolean>>(), runner.calls)
        assertEquals("denied", audit.entries.single().third)
    }
    @Test fun missingCmdFails() = runBlocking {
        assertEquals("failed", shellCapability(FakeCommandRunner(), DenyList(), FakeAuditLog()).executor.execute(JsonObject(emptyMap())).status)
    }
    @Test fun rootFlagFalseIsHonoured() = runBlocking {
        val runner = FakeCommandRunner()
        shellCapability(runner, DenyList(), FakeAuditLog()).executor.execute(args("cmd" to "id", "root" to false))
        assertEquals(listOf("id" to false), runner.calls)
    }
    @Test fun denyListBlocksDestructiveAndAllowsNormal() {
        val d = DenyList()
        listOf("rm -rf /", "rm -rf /*", "mkfs.ext4 /dev/x", "fastboot flash boot x", "echo x > /dev/block/sda1",
            "reboot bootloader", "cmd recovery wipe", "ls; dd if=a of=b").forEach { assertNotNull(it, d.violation(it)) }
        listOf("ls /sdcard", "settings put system screen_brightness 100", "svc wifi enable", "rm -rf /sdcard/tmp",
            "am start -n a/b", "pm list packages", "cat /proc/cpuinfo", "reboot").forEach { assertNull(it, d.violation(it)) }
    }
}
