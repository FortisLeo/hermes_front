package com.rishitgoklani.hermes_front_mobile.domain.bridge

import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.Diagnostics
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.diagnosticsCapabilities
import com.rishitgoklani.hermes_front_mobile.domain.bridge.shell.CommandResult
import com.rishitgoklani.hermes_front_mobile.testing.FakeCommandRunner
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTest {
    private fun args(vararg p: Pair<String, String>) = JsonObject(p.associate { it.first to JsonPrimitive(it.second) })
    private fun tool(runner: FakeCommandRunner, name: String) = diagnosticsCapabilities(runner).first { it.name == name }

    @Test fun summarizesMemoryStorageUptimeThermal() {
        assertEquals("memory: 1.0 GB free of 3.0 GB", Diagnostics.summarize("memory", "MemTotal: 3145728 kB\nMemAvailable: 1048576 kB"))
        assertEquals("storage: 1.0 GB free of 4.0 GB (75% used)", Diagnostics.summarize("storage", "/dev/x 4194304 3145728 1048576 75% /data"))
        assertEquals("uptime: 1d 2h 3m", Diagnostics.summarize("uptime", "93780.55 1234.5"))
        assertEquals("temperature: 36.5 C", Diagnostics.summarize("thermal", "36500"))
    }

    @Test fun unknownOutputDoesNotCrash() {
        assertEquals("memory: unknown", Diagnostics.summarize("memory", ""))
        assertEquals("temperature: unknown", Diagnostics.summarize("thermal", "n/a"))
    }

    @Test fun scriptHasOneMarkerPerKnownSectionAndSplitRoundTrips() {
        val s = Diagnostics.script(listOf("uptime", "bogus", "thermal"))
        assertTrue(s.contains("##uptime") && s.contains("##thermal") && !s.contains("bogus"))
        assertEquals(mapOf("a" to "1\n2", "b" to "3"), Diagnostics.split("##a\n1\n2\n##b\n3\n"))
    }

    @Test fun systemReportRunsOnceAsRoot() = runBlocking {
        val runner = FakeCommandRunner(CommandResult(0, "##uptime\n3600.0 1\n##thermal\n40000"))
        val r = tool(runner, "system_report").executor.execute(args())
        assertEquals(1, runner.calls.size); assertTrue(runner.calls.single().second)
        assertEquals("completed", r.status); assertTrue(r.message.contains("uptime: 0d 1h 0m"))
    }

    @Test fun packageInfoRejectsInjectionAndMissingApps() = runBlocking {
        val runner = FakeCommandRunner(CommandResult(0, ""))
        assertEquals("failed", tool(runner, "package_info").executor.execute(args("package" to "x; rm -rf /")).status)
        assertTrue(runner.calls.isEmpty())
        assertEquals("failed", tool(runner, "package_info").executor.execute(args("package" to "com.nope.app")).status)
    }

    @Test fun packageInfoSummarizes() {
        val raw = "##path\npackage:/x/base.apk\n##version\n  versionName=1.2.3\n##granted\n7\n##bucket\n10\n##disabled\n0"
        assertEquals("com.a.b 1.2.3, enabled, 7 permissions granted, standby bucket 10", Diagnostics.summarizePackage("com.a.b", raw))
        assertNull(Diagnostics.summarizePackage("com.a.b", "##path\n\n"))
    }

    @Test fun logCommandsAreBoundedAndKnown() {
        assertTrue(Diagnostics.logCommand("crash", 5000)!!.endsWith("-t 100"))
        assertNull(Diagnostics.logCommand("rm", 5))
    }

    @Test fun radioReadsStateWithoutSwitching() = runBlocking {
        val runner = FakeCommandRunner(CommandResult(0, "1\n"))
        val r = tool(runner, "radio").executor.execute(args("name" to "wifi", "state" to "get"))
        assertEquals("wifi is on", r.message); assertEquals(1, runner.calls.size)
    }

    @Test fun radioRejectsBadInput() = runBlocking {
        val runner = FakeCommandRunner()
        assertEquals("failed", tool(runner, "radio").executor.execute(args("name" to "nfc", "state" to "on")).status)
        assertEquals("failed", tool(runner, "radio").executor.execute(args("name" to "wifi", "state" to "maybe")).status)
        assertTrue(runner.calls.isEmpty())
    }
}
