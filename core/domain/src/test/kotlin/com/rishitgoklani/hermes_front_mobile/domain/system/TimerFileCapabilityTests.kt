package com.rishitgoklani.hermes_front_mobile.domain.system

import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.TimerScheduler
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.fileCapabilities
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.timerCapabilities
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TimerFileCapabilityTests {
    @get:Rule val tmp = TemporaryFolder()
    private fun a(vararg p: Pair<String, Any>) = JsonObject(p.associate { (k, v) -> k to (if (v is Boolean) JsonPrimitive(v) else if (v is Int) JsonPrimitive(v) else JsonPrimitive(v.toString())) })

    private class FakeScheduler : TimerScheduler {
        val pending = mutableListOf<Pair<Long, () -> Unit>>(); var cancelled = 0
        override fun schedule(delayMs: Long, action: () -> Unit): () -> Unit { pending += delayMs to action; return { cancelled++ } }
    }

    @Test fun timerStartFiresCallbackWhenScheduledActionRuns() = runBlocking {
        val sch = FakeScheduler(); val fired = mutableListOf<String>()
        val caps = timerCapabilities(TimerBook(), sch) { fired += it }.associateBy { it.name }
        val r = caps["timer_start"]!!.executor.execute(a("name" to "Tea", "duration" to "3 min"))
        assertEquals("completed", r.status); assertEquals(180_000L, sch.pending.single().first)
        sch.pending.single().second(); assertEquals(listOf("Tea"), fired)
        assertEquals("No timers", caps["timer_list"]!!.executor.execute(a()).message)
    }
    @Test fun timerBadDurationFails() = runBlocking { assertEquals("failed", timerCapabilities(TimerBook(), FakeScheduler()) {}.first().executor.execute(a("duration" to "soon")).status) }
    @Test fun restartingSameNameCancelsPreviousSchedule() = runBlocking {
        val sch = FakeScheduler(); val t = timerCapabilities(TimerBook(), sch) {}.first()
        t.executor.execute(a("name" to "x", "duration" to "1m")); t.executor.execute(a("name" to "x", "duration" to "2m")); assertEquals(1, sch.cancelled)
    }
    @Test fun cancelStopsSchedule() = runBlocking {
        val sch = FakeScheduler(); val caps = timerCapabilities(TimerBook(), sch) {}.associateBy { it.name }
        caps["timer_start"]!!.executor.execute(a("name" to "x", "duration" to "1m"))
        assertEquals("completed", caps["timer_cancel"]!!.executor.execute(a("name" to "x")).status); assertEquals(1, sch.cancelled)
        assertEquals("failed", caps["timer_cancel"]!!.executor.execute(a("name" to "x")).status)
    }
    @Test fun stopwatchStartStop() = runBlocking {
        val caps = timerCapabilities(TimerBook(), FakeScheduler()) {}.associateBy { it.name }
        assertEquals("failed", caps["stopwatch"]!!.executor.execute(a("action" to "stop")).status)
        caps["stopwatch"]!!.executor.execute(a("action" to "start")); assertEquals("completed", caps["stopwatch"]!!.executor.execute(a("action" to "stop")).status)
    }

    private fun files(): Pair<File, Map<String, com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability>> {
        val root = tmp.newFolder("sandbox"); return root to fileCapabilities(FileSandbox(listOf(root))).associateBy { it.name }
    }
    @Test fun writeReadAppendListDelete() = runBlocking {
        val (root, c) = files(); val p = File(root, "sub/n.txt").path
        c["file_write"]!!.executor.execute(a("path" to p, "content" to "hello")); c["file_write"]!!.executor.execute(a("path" to p, "content" to " world", "append" to true))
        assertEquals("hello world", c["file_read"]!!.executor.execute(a("path" to p)).message)
        assertTrue(c["file_list"]!!.executor.execute(a("path" to File(root, "sub").path)).message.contains("n.txt"))
        assertEquals("completed", c["file_delete"]!!.executor.execute(a("path" to p)).status); assertEquals("failed", c["file_delete"]!!.executor.execute(a("path" to p)).status)
    }
    @Test fun outsideSandboxDenied() = runBlocking {
        val (_, c) = files(); assertEquals("denied", c["file_read"]!!.executor.execute(a("path" to "/etc/hosts")).status); assertEquals("denied", c["file_write"]!!.executor.execute(a("path" to "/tmp/x_deskbot", "content" to "x")).status)
    }
    @Test fun searchByNameAndText() = runBlocking {
        val (root, c) = files(); File(root, "a.log").writeText("needle here"); File(root, "b.txt").writeText("nothing")
        assertTrue(c["file_search"]!!.executor.execute(a("path" to root.path, "name" to ".log")).message.endsWith("a.log"))
        assertTrue(c["file_search"]!!.executor.execute(a("path" to root.path, "text" to "NEEDLE")).message.endsWith("a.log"))
        assertEquals("No matches", c["file_search"]!!.executor.execute(a("path" to root.path, "name" to "zzz")).message)
    }
    @Test fun moveWithinSandboxOnly() = runBlocking {
        val (root, c) = files(); File(root, "m.txt").writeText("x")
        assertEquals("completed", c["file_move"]!!.executor.execute(a("from" to File(root, "m.txt").path, "to" to File(root, "moved/m2.txt").path)).status)
        assertEquals("denied", c["file_move"]!!.executor.execute(a("from" to File(root, "moved/m2.txt").path, "to" to "/tmp/escape_deskbot")).status)
    }
    @Test fun readOffsetAndNonFile() = runBlocking {
        val (root, c) = files(); File(root, "o.txt").writeText("abcdef")
        assertEquals("def", c["file_read"]!!.executor.execute(a("path" to File(root, "o.txt").path, "offset" to 3)).message)
        assertEquals("failed", c["file_read"]!!.executor.execute(a("path" to root.path)).status)
    }
}
