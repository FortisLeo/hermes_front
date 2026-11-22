package com.rishitgoklani.hermes_front_mobile.data.shell

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ShellTests {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun runsCommandAndCapturesOutput() = runBlocking {
        val r = ProcessCommandRunner().run("echo hi", root = false)
        assertEquals(0, r.exitCode); assertEquals("hi", r.stdout.trim())
    }
    @Test fun nonZeroExitAndStderr() = runBlocking {
        val r = ProcessCommandRunner().run("echo bad >&2; exit 3", root = false)
        assertEquals(3, r.exitCode); assertEquals("bad", r.stderr.trim())
    }
    @Test fun timeoutKillsProcess() = runBlocking {
        val r = ProcessCommandRunner(timeoutSeconds = 1).run("sleep 5", root = false)
        assertEquals(124, r.exitCode)
    }
    @Test fun missingSuBinaryReports127() = runBlocking {
        val r = ProcessCommandRunner(su = listOf("/no/such/su", "-c")).run("id", root = true)
        assertEquals(127, r.exitCode)
    }
    @Test fun auditLogAppendsAndRotates() {
        val f = tmp.newFile("audit.log")
        val log = FileAuditLog(f, maxBytes = 50, clock = { 1L })
        log.record("shell", """{"cmd":"a"}""", "completed")
        log.record("shell", """{"cmd":"b"}""", "denied")
        log.record("shell", """{"cmd":"c"}""", "failed")
        assertTrue(java.io.File(f.path + ".1").exists())
        assertTrue(f.readText().contains("failed"))
    }
}
