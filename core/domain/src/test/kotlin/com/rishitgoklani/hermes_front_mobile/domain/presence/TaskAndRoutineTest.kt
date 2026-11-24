package com.rishitgoklani.hermes_front_mobile.domain.presence

import app.cash.turbine.test
import com.rishitgoklani.hermes_front_mobile.domain.BotState
import com.rishitgoklani.hermes_front_mobile.domain.behavior.RunCapability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.CapabilityRegistry
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.alertCapability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.quietStatusCapability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.routineSuggestCapability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.taskCapability
import com.rishitgoklani.hermes_front_mobile.testing.FakeAuditSource
import com.rishitgoklani.hermes_front_mobile.testing.FakeCueSink
import com.rishitgoklani.hermes_front_mobile.testing.FakeQuietContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskSupervisorTest {
    @Test fun runsStepsToCompletion() = runTest {
        val t = TaskSupervisor(backgroundScope); val ran = mutableListOf<Int>()
        assertTrue(t.start("job", listOf({ ran += 1; null }, { ran += 2; null })))
        runCurrent(); assertEquals(listOf(1, 2), ran); assertEquals(TaskState.Finished("job"), t.state.value)
    }

    @Test fun pausesWhenTheUserSpeaksAndResumesAfterTheTurn() = runTest {
        val t = TaskSupervisor(backgroundScope); val ran = mutableListOf<Int>()
        t.start("job", listOf({ ran += 1; delay(1000); null }, { ran += 2; delay(1000); null }, { ran += 3; null }))
        runCurrent(); assertEquals(listOf(1), ran)
        val say = t.react(Input.State(BotState.Listening)); assertTrue(say.single() is Cue.Speak)
        advanceTimeBy(5000); runCurrent()
        assertEquals(listOf(1), ran); assertTrue(t.state.value is TaskState.Paused)
        t.react(Input.State(BotState.Idle)); advanceTimeBy(5000); runCurrent()
        assertEquals(listOf(1, 2, 3), ran); assertEquals(TaskState.Finished("job"), t.state.value)
    }

    @Test fun stateFlowReportsPauseAndResume() = runTest {
        val t = TaskSupervisor(backgroundScope)
        t.state.test {
            assertEquals(TaskState.None, awaitItem())
            t.start("j", listOf({ delay(1000); null }, { null }))
            assertEquals(TaskState.Running("j", 0, 2), awaitItem())
            runCurrent(); t.pause(); assertEquals(TaskState.Paused("j", 0, 2), awaitItem())
            t.resume(); assertEquals(TaskState.Running("j", 0, 2), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun aFailingStepAbortsTheTask() = runTest {
        val t = TaskSupervisor(backgroundScope); val ran = mutableListOf<Int>()
        t.start("j", listOf({ ran += 1; "oops" }, { ran += 2; null })); runCurrent()
        assertEquals(listOf(1), ran); assertEquals(TaskState.Finished("j", "oops"), t.state.value)
    }

    @Test fun anExceptionIsReportedNotThrown() = runTest {
        val t = TaskSupervisor(backgroundScope); t.start("j", listOf({ error("bad") })); runCurrent()
        assertEquals(TaskState.Finished("j", "bad"), t.state.value)
    }

    @Test fun onlyOneTaskAtATimeAndEmptyIsRefused() = runTest {
        val t = TaskSupervisor(backgroundScope)
        assertFalse(t.start("none", emptyList()))
        t.start("a", listOf({ delay(1000); null })); assertFalse(t.start("b", listOf({ null })))
    }

    @Test fun cancelStopsAnActiveTask() = runTest {
        val t = TaskSupervisor(backgroundScope); val ran = mutableListOf<Int>()
        t.start("j", listOf({ delay(1000); ran += 1; null }, { ran += 2; null })); runCurrent()
        assertTrue(t.cancel()); advanceTimeBy(5000); runCurrent()
        assertTrue(ran.isEmpty()); assertFalse(t.busy); assertFalse(t.cancel())
    }

    @Test fun pauseResumeWithoutTaskDoNothing() = runTest {
        val t = TaskSupervisor(backgroundScope); assertFalse(t.pause()); assertFalse(t.resume())
        assertTrue(t.react(Input.State(BotState.Listening)).isEmpty()); assertTrue(t.react(Input.State(BotState.Idle)).isEmpty()); assertTrue(t.react(Input.Tick).isEmpty())
    }
}

class RoutineMinerTest {
    private val day = 86_400_000L
    private fun line(dayN: Int, hour: Int, min: Int, tool: String = "dnd", status: String = "completed", args: String = """{"mode":"priority"}""") =
        "${dayN * day + hour * 3_600_000L + min * 60_000L}\t$tool\t$status\t$args"

    @Test fun proposesAnActionRepeatedAtTheSameTimeOnThreeDays() {
        val p = RoutineMiner().mine(listOf(line(1, 22, 0), line(2, 22, 10), line(3, 21, 55)))
        assertEquals(1, p.size); assertEquals("dnd", p[0].tool); assertEquals(3, p[0].days); assertEquals("22:00", p[0].time); assertEquals("0 22 * * *", p[0].cron)
        assertTrue(p[0].spoken().contains("22:00"))
    }
    @Test fun twoDaysIsNotEnough() { assertTrue(RoutineMiner().mine(listOf(line(1, 22, 0), line(2, 22, 0))).isEmpty()) }
    @Test fun repeatsOnTheSameDayCountOnce() { assertTrue(RoutineMiner().mine(List(5) { line(1, 22, it) }).isEmpty()) }
    @Test fun scatteredTimesDoNotCluster() { assertTrue(RoutineMiner().mine(listOf(line(1, 8, 0), line(2, 14, 0), line(3, 21, 0))).isEmpty()) }
    @Test fun differentArgsAreDifferentActions() { assertTrue(RoutineMiner().mine(listOf(line(1, 9, 0, args = "a"), line(2, 9, 0, args = "b"), line(3, 9, 0, args = "c"))).isEmpty()) }
    @Test fun failuresEventsAndRoutinesAreIgnored() {
        val ls = (1..3).flatMap { d -> listOf(line(d, 9, 0, status = "failed"), line(d, 9, 0, tool = "routine:x"), line(d, 9, 0, tool = "voice_turn"), line(d, 9, 0, tool = "presence", status = "event")) }
        assertTrue(RoutineMiner().mine(ls).isEmpty())
    }
    @Test fun windowWrapsMidnight() { assertEquals(1, RoutineMiner().mine(listOf(line(1, 23, 50), line(2, 0, 10), line(3, 23, 55))).size) }
    @Test fun timezoneOffsetShiftsTheLocalClock() {
        val p = RoutineMiner().mine(listOf(line(1, 20, 0), line(2, 20, 0), line(3, 20, 0)), utcOffsetMs = 2 * 3_600_000L); assertEquals("22:00", p.single().time)
    }
    @Test fun malformedLinesAreSkippedAndBestProposalComesFirst() {
        val ls = listOf("garbage", "x\ty\tz", line(1, 9, 0, tool = "torch"), line(2, 9, 0, tool = "torch"), line(3, 9, 0, tool = "torch"), line(4, 9, 0, tool = "torch"), line(1, 7, 0, tool = "alarm"), line(2, 7, 0, tool = "alarm"), line(3, 7, 0, tool = "alarm"))
        assertEquals(listOf("torch", "alarm"), RoutineMiner().mine(ls).map { it.tool })
    }

    @Test fun learningOffersOneProposalInTheEveningThenStaysQuiet() = runTest {
        var now = 0L
        val src = FakeAuditSource(listOf(line(1, 22, 0), line(2, 22, 0), line(3, 22, 0)))
        val l = RoutineLearning(src, clock = { now }, hour = { 19 })
        assertEquals(1, l.react(Input.Tick).size); assertTrue(l.react(Input.Tick).isEmpty())
        now = 8 * day; assertEquals(1, l.react(Input.Tick).size)
    }
    @Test fun learningIsSilentOutsideEveningOrOnOtherInputs() = runTest {
        val src = FakeAuditSource(listOf(line(1, 22, 0), line(2, 22, 0), line(3, 22, 0)))
        assertTrue(RoutineLearning(src, hour = { 10 }).react(Input.Tick).isEmpty()); assertTrue(RoutineLearning(src, hour = { 19 }).react(Input.Heard("x")).isEmpty())
    }
}

class PresenceCapabilitiesTest {
    private val none = JsonObject(emptyMap())
    private fun args(vararg p: Pair<String, String>) = buildJsonObject { p.forEach { put(it.first, it.second) } }

    @Test fun quietStatusReportsReasonsAndHeldCount() = runTest {
        val q = FakeQuietContext(QuietContext(dnd = true, sleeping = true)); val hub = BehaviorHub(BehaviorRegistry(), q, FakeCueSink())
        hub.publish(listOf(Cue.Speak("later")))
        val r = quietStatusCapability(q, hub).executor.execute(none)
        assertEquals("Quiet: dnd, sleep", r.message); assertEquals("1", r.data!!["held"].toString())
        q.context = QuietContext(); assertEquals("Not quiet", quietStatusCapability(q, hub).executor.execute(none).message)
    }

    @Test fun alertRaiseSpeaksAckClears() = runTest {
        val sink = FakeCueSink(); val hub = BehaviorHub(BehaviorRegistry(), FakeQuietContext(), sink); val esc = Escalation()
        val cap = alertCapability(esc, hub).executor
        assertEquals(ActionResult.FAILED, cap.execute(args("action" to "raise")).status)
        assertEquals(ActionResult.COMPLETED, cap.execute(args("action" to "raise", "id" to "p", "text" to "Take the pill")).status)
        assertEquals(listOf("Take the pill"), sink.spoken); assertEquals("p", cap.execute(args("action" to "status")).message)
        assertEquals(ActionResult.COMPLETED, cap.execute(args("action" to "ack", "id" to "p")).status)
        assertEquals(ActionResult.FAILED, cap.execute(args("action" to "ack", "id" to "p")).status)
        assertEquals(ActionResult.FAILED, cap.execute(args("action" to "nope")).status)
    }

    @Test fun taskStartsValidatesAndReportsStatus() = runTest {
        val calls = mutableListOf<String>()
        val reg = CapabilityRegistry(listOf(Capability("torch", "t") { calls += "torch"; ActionResult.completed() }, Capability("shell", "s") { ActionResult.completed() }))
        val tasks = TaskSupervisor(backgroundScope); val cap = taskCapability(tasks, { reg }, { RunCapability(reg) }).executor
        suspend fun start(vararg tools: String) = cap.execute(buildJsonObject { put("action", "start"); put("steps", JsonArray(tools.map { buildJsonObject { put("tool", it) } })) })
        assertEquals(ActionResult.FAILED, cap.execute(args("action" to "start")).status)
        assertEquals(ActionResult.FAILED, start("nope").status); assertEquals(ActionResult.DENIED, start("shell").status)
        assertEquals(ActionResult.ACCEPTED, start("torch", "torch").status); runCurrent()
        assertEquals(listOf("torch", "torch"), calls); assertTrue(cap.execute(args("action" to "status")).message.startsWith("Finished"))
        assertEquals(ActionResult.FAILED, cap.execute(args("action" to "cancel")).status)
        assertEquals(ActionResult.FAILED, cap.execute(args("action" to "x")).status)
    }

    @Test fun taskStepFailureIsSurfaced() = runTest {
        val reg = CapabilityRegistry(listOf(Capability("bad", "b") { ActionResult.failed("nope") }))
        val tasks = TaskSupervisor(backgroundScope); val cap = taskCapability(tasks, { reg }, { RunCapability(reg) }).executor
        cap.execute(buildJsonObject { put("action", "start"); put("steps", JsonArray(listOf(buildJsonObject { put("tool", "bad") }))) }); runCurrent()
        assertTrue(cap.execute(args("action" to "status")).message.contains("failed: bad: nope"))
    }

    @Test fun routineSuggestListsProposals() = runTest {
        val day = 86_400_000L
        val src = FakeAuditSource((1..3).map { "${it * day + 22 * 3_600_000L}\ttorch\tcompleted\t{}" })
        val r = routineSuggestCapability(src).executor.execute(none)
        assertTrue(r.message.contains("torch")); assertTrue(r.message.contains("0 22 * * *"))
        assertEquals("No repeated actions found", routineSuggestCapability(FakeAuditSource()).executor.execute(none).message)
    }
}
