package com.rishitgoklani.hermes_front_mobile.domain.tools

import com.rishitgoklani.hermes_front_mobile.domain.behavior.RunCapability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.CapabilityRegistry
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.healthScoreCapability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.toolboxCapabilities
import com.rishitgoklani.hermes_front_mobile.domain.macro.MacroRecorder
import com.rishitgoklani.hermes_front_mobile.domain.macro.macroCapabilities
import com.rishitgoklani.hermes_front_mobile.domain.macro.recorded
import com.rishitgoklani.hermes_front_mobile.domain.system.Archives
import com.rishitgoklani.hermes_front_mobile.domain.system.FileSandbox
import com.rishitgoklani.hermes_front_mobile.domain.system.HealthScore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ToolboxTests {
    @get:Rule val tmp = TemporaryFolder()
    private fun j(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject
    private fun tools(root: File, notes: File = File(root, "notes.md")) = toolboxCapabilities(FileSandbox(listOf(root)), notes) { f, a -> java.security.MessageDigest.getInstance(a).digest(f.readBytes()).joinToString("") { "%02x".format(it) } }.associateBy { it.name }

    @Test fun zipThenUnzipRoundTrip() = runBlocking {
        val root = tmp.newFolder("r"); File(root, "d/sub").mkdirs(); File(root, "d/a.txt").writeText("A"); File(root, "d/sub/b.txt").writeText("B"); val t = tools(root)
        assertEquals("completed", t["archive_zip"]!!.executor.execute(j("""{"source":"${root.path}/d","dest":"${root.path}/out/d.zip"}""")).status)
        assertEquals("completed", t["archive_unzip"]!!.executor.execute(j("""{"zip":"${root.path}/out/d.zip","dest":"${root.path}/x"}""")).status); assertEquals("B", File(root, "x/sub/b.txt").readText())
    }
    @Test fun unzipRejectsZipSlipAndBombs() {
        val z = File(tmp.root, "evil.zip"); ZipOutputStream(z.outputStream()).use { o -> o.putNextEntry(ZipEntry("../evil.txt")); o.write(1); o.closeEntry() }
        assertThrows(SecurityException::class.java) { Archives.unzip(z, File(tmp.root, "dest")) }; assertFalse(File(tmp.root, "evil.txt").exists())
        val big = File(tmp.root, "big.zip"); ZipOutputStream(big.outputStream()).use { o -> o.putNextEntry(ZipEntry("a")); o.write(ByteArray(10_000)); o.closeEntry() }
        assertThrows(SecurityException::class.java) { Archives.unzip(big, File(tmp.root, "d2"), maxBytes = 1000) }
    }
    @Test fun archiveToolsRefuseOutsideSandboxAndMissing() = runBlocking {
        val root = tmp.newFolder("r2"); val t = tools(root)
        assertEquals("denied", t["archive_zip"]!!.executor.execute(j("""{"source":"/etc","dest":"${root.path}/x.zip"}""")).status); assertEquals("failed", t["archive_unzip"]!!.executor.execute(j("""{"zip":"${root.path}/none.zip","dest":"${root.path}/x"}""")).status)
        val evil = File(root, "e.zip"); ZipOutputStream(evil.outputStream()).use { o -> o.putNextEntry(ZipEntry("../../x")); o.write(1); o.closeEntry() }
        assertEquals("denied", t["archive_unzip"]!!.executor.execute(j("""{"zip":"${evil.path}","dest":"${root.path}/y"}""")).status)
    }
    @Test fun hashAlgorithms() = runBlocking {
        val root = tmp.newFolder("r3"); File(root, "f").writeText("abc"); val t = tools(root)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", t["file_hash"]!!.executor.execute(j("""{"path":"${root.path}/f"}""")).message)
        assertEquals("900150983cd24fb0d6963f7d28e17f72", t["file_hash"]!!.executor.execute(j("""{"path":"${root.path}/f","algo":"md5"}""")).message); assertEquals("failed", t["file_hash"]!!.executor.execute(j("""{"path":"${root.path}/f","algo":"crc"}""")).status)
    }
    @Test fun notesAppendAndSearch() = runBlocking {
        val root = tmp.newFolder("r4"); val t = tools(root); t["quick_note"]!!.executor.execute(j("""{"text":"Buy milk","tag":"todo"}""")); t["quick_note"]!!.executor.execute(j("""{"text":"Call mom"}"""))
        assertTrue(t["notes_search"]!!.executor.execute(j("""{"query":"milk"}""")).message.contains("[todo] Buy milk")); assertEquals("No notes", t["notes_search"]!!.executor.execute(j("""{"query":"zzz"}""")).message); assertEquals("failed", t["quick_note"]!!.executor.execute(j("""{"text":" "}""")).status)
    }

    @Test fun healthScoreScenarios() {
        assertEquals(100, HealthScore.compute(HealthScore.Input(80, false, 30.0, 50, true, 0)).score)
        val bad = HealthScore.compute(HealthScore.Input(10, false, 46.0, 3, false, 12)); assertTrue(bad.score <= 10); assertTrue(bad.issue.contains("hot"))
        assertEquals(60, HealthScore.compute(HealthScore.Input(80, false, 30.0, 50, true, 0, killActive = true)).score); assertEquals("kill switch active", HealthScore.compute(HealthScore.Input(80, false, 30.0, 50, true, 0, true)).issue)
        assertEquals(100, HealthScore.compute(HealthScore.Input(10, true, 30.0, 50, true, 0)).score)
    }
    @Test fun healthToolFormats() = runBlocking { assertTrue(healthScoreCapability { HealthScore.Input(80, false, 41.0, 50, true, 0) }.executor.execute(JsonObject(emptyMap())).message.startsWith("90/100")) }

    @Test fun macroRecordAndPlayback() = runBlocking {
        val rec = MacroRecorder(); val calls = mutableListOf<String>()
        val hello = Capability("hello", "h") { a -> calls += a.toString(); ActionResult.completed("ok") }.recorded(rec)
        val fail = Capability("bad", "b") { ActionResult.failed("no") }.recorded(rec)
        lateinit var run: RunCapability; val caps = macroCapabilities(rec) { run }.associateBy { it.name }; run = RunCapability(CapabilityRegistry(listOf(hello, fail) + caps.values))
        hello.executor.execute(j("""{"x":1}""")); assertTrue(calls.size == 1 && rec.names().isEmpty())
        caps["macro_record"]!!.executor.execute(j("""{"action":"start","name":"m1"}""")); hello.executor.execute(j("""{"x":2}""")); fail.executor.execute(j("{}")); hello.executor.execute(j("""{"x":3}"""))
        assertEquals("completed", caps["macro_record"]!!.executor.execute(j("""{"action":"stop"}""")).status); assertEquals(2, rec.get("m1")!!.size)
        calls.clear(); assertEquals("Played 2 steps", caps["macro_play"]!!.executor.execute(j("""{"name":"m1"}""")).message); assertEquals(2, calls.size)
        assertTrue(caps["macro_list"]!!.executor.execute(j("{}")).message.contains("m1 (2 steps)")); assertEquals("completed", caps["macro_delete"]!!.executor.execute(j("""{"name":"m1"}""")).status); assertEquals("failed", caps["macro_play"]!!.executor.execute(j("""{"name":"m1"}""")).status)
    }
    @Test fun macroValidation() = runBlocking {
        val rec = MacroRecorder(); lateinit var run: RunCapability; val caps = macroCapabilities(rec) { run }.associateBy { it.name }; run = RunCapability(CapabilityRegistry(caps.values.toList()))
        assertEquals("failed", caps["macro_record"]!!.executor.execute(j("""{"action":"start","name":" "}""")).status); assertEquals("failed", caps["macro_record"]!!.executor.execute(j("""{"action":"stop"}""")).status); assertEquals("failed", caps["macro_record"]!!.executor.execute(j("""{"action":"x"}""")).status)
    }
}
