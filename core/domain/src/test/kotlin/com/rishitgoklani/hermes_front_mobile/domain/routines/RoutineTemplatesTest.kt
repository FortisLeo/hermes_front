package com.rishitgoklani.hermes_front_mobile.domain.routines

import com.rishitgoklani.hermes_front_mobile.domain.behavior.RunCapability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.CapabilityRegistry
import com.rishitgoklani.hermes_front_mobile.domain.notifications.NotificationRecord
import com.rishitgoklani.hermes_front_mobile.testing.FakeNotificationSource
import com.rishitgoklani.hermes_front_mobile.testing.FakeStateStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoutineTemplatesTest {
    private fun obj(s: String) = Json.parseToJsonElement(s).jsonObject
    private val templates = RoutineTemplateRegistry(listOf(WakeUpTemplate(), BedtimeTemplate(), FocusTemplate()))

    @Test fun wakeupRampsBrightnessThenGreets() {
        val steps = WakeUpTemplate().plan(obj("""{"ramp_min":4,"briefing":"Sunny, 24 degrees."}"""))
        assertEquals("dnd", steps.first().tool)
        val b = steps.filter { it.tool == "brightness" }
        assertEquals(listOf("10", "30", "55", "80", "100"), b.map { it.args["level"]!!.jsonPrimitive.content })
        assertEquals(60_000L, b[1].delayBeforeMs)
        assertTrue(steps.last().args["text"]!!.jsonPrimitive.content.contains("Sunny"))
    }

    @Test fun bedtimeDimsAndSetsDndAndSpeaksSummary() {
        val steps = BedtimeTemplate().plan(obj("""{"dnd":"silence","summary":"two meetings"}"""))
        assertEquals("silence", steps.first { it.tool == "dnd" }.args["mode"]!!.jsonPrimitive.content)
        assertTrue(steps.last().args["text"]!!.jsonPrimitive.content.contains("two meetings"))
        assertTrue(runCatching { BedtimeTemplate().plan(obj("""{"dnd":"loud"}""")) }.isFailure)
    }

    @Test fun focusPlanHasTimerDndFilterAndEndSteps() {
        val steps = FocusTemplate().plan(obj("""{"minutes":30,"allow":["Phone"]}"""))
        assertEquals(listOf("focus_filter", "dnd", "timer_start", "tts_say", "dnd", "focus_filter", "tts_say"), steps.map { it.tool })
        assertEquals(30 * 60_000L, steps[4].delayBeforeMs); assertEquals("30m", steps[2].args["duration"]!!.jsonPrimitive.content)
        assertTrue(runCatching { FocusTemplate().plan(obj("""{"minutes":999}""")) }.isFailure)
        assertTrue(runCatching { FocusTemplate().plan(obj("""{"minutes":"x"}""")) }.isFailure)
    }

    @Test fun registryRejectsDuplicates() { assertTrue(runCatching { templates.register(FocusTemplate()) }.isFailure); assertEquals(3, templates.list().size) }

    private fun harness(scope: TestScope): Triple<TemplateRunner, MutableList<String>, RunCapability> {
        val calls = mutableListOf<String>()
        val caps = listOf("dnd", "screen_dim", "brightness", "tts_say", "timer_start", "focus_filter").map { n -> Capability(n, "") { calls += "$n ${it}"; ActionResult.completed() } }
        val run = RunCapability(CapabilityRegistry(caps))
        return Triple(TemplateRunner(templates, { run }, scope) { kotlinx.coroutines.delay(it) }, calls, run)
    }

    @Test fun runnerExecutesFocusStepsAndEndsAfterDelay() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler)); val (runner, calls, _) = harness(scope)
        assertEquals(7, runner.start("focus", obj("""{"minutes":1}""")))
        scope.advanceTimeBy(1_000); assertEquals(4, calls.size); assertTrue(runner.running().contains("focus"))
        scope.advanceTimeBy(60_000); scope.testScheduler.runCurrent()
        assertEquals(7, calls.size); assertTrue(calls.last().startsWith("tts_say")); assertTrue(runner.running().isEmpty())
    }

    @Test fun cancelRunsFocusCleanup() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler)); val (runner, calls, _) = harness(scope)
        runner.start("focus", obj("""{"minutes":10}""")); scope.advanceTimeBy(1_000)
        assertTrue(runner.cancel("focus")); scope.testScheduler.runCurrent()
        assertTrue(calls.takeLast(2).map { it.substringBefore(' ') } == listOf("dnd", "focus_filter")); assertFalse(runner.cancel("focus"))
    }

    @Test fun restartReplacesRunningJob() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler)); val (runner, calls, _) = harness(scope)
        runner.start("focus", obj("""{"minutes":10}""")); scope.advanceTimeBy(1_000); runner.start("focus", obj("""{"minutes":1}""")); scope.advanceTimeBy(70_000); scope.testScheduler.runCurrent()
        assertEquals(4 + 7, calls.size)
    }

    @Test fun toolsListRunCancelAndErrors() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler)); val (runner, _, _) = harness(scope)
        val caps = routineTemplateCapabilities(templates, runner).associateBy { it.name }
        assertTrue(caps.getValue("routine_template_list").executor.execute(JsonObject(emptyMap())).message.contains("bedtime"))
        assertEquals("failed", caps.getValue("routine_template_run").executor.execute(obj("""{"id":"nope"}""")).status)
        assertEquals("failed", caps.getValue("routine_template_run").executor.execute(obj("""{"id":"focus","params":{"minutes":0}}""")).status)
        assertEquals("accepted", caps.getValue("routine_template_run").executor.execute(obj("""{"id":"bedtime"}""")).status)
        assertEquals("failed", caps.getValue("routine_template_cancel").executor.execute(obj("""{"id":"wakeup"}""")).status)
    }

    // Focus filter
    private val now = 10_000_000L
    private fun n(pkg: String, title: String = "t") = NotificationRecord("k-$pkg", pkg, pkg.substringAfterLast('.'), title, "", now)

    @Test fun filterBlocksNonAllowedOnlyWhileActive() {
        var t = now; val f = FocusFilter(FakeStateStore()) { t }
        assertFalse(f.blocks(n("com.chat.slack")))
        f.start(10, listOf("phone")); assertTrue(f.blocks(n("com.chat.slack"))); assertFalse(f.blocks(n("com.android.phone")))
        t = now + 11 * 60_000; assertFalse(f.active()); assertFalse(f.blocks(n("com.chat.slack")))
    }

    @Test fun guardDismissesBlockedNotificationsOnly() = runTest {
        val f = FocusFilter(FakeStateStore()) { now }; f.start(10, listOf("calendar"))
        val src = FakeNotificationSource(listOf(n("com.chat.slack", "ping"), n("com.google.calendar", "ping")))
        val g = FocusNotificationGuard(f, src, "me.deskbot")
        assertEquals(1, g.onEvent(DeviceEvent.NotificationPosted("slack", "ping", ""))); assertEquals(listOf("k-com.chat.slack"), src.dismissed)
        assertEquals(0, g.onEvent(DeviceEvent.ChargerConnected())); f.stop(); assertEquals(0, g.onEvent(DeviceEvent.NotificationPosted("slack", "ping", "")))
    }

    @Test fun focusToolOnOffStatus() = runTest {
        val f = FocusFilter(FakeStateStore()) { now }; val cap = focusFilterCapability(f)
        assertEquals("failed", cap.executor.execute(obj("""{"action":"on","minutes":500}""")).status)
        assertEquals("completed", cap.executor.execute(obj("""{"action":"on","minutes":5,"allow":["Phone"]}""")).status)
        assertTrue(f.active()); assertEquals(listOf("Phone"), f.state().allow)
        cap.executor.execute(obj("""{"action":"off"}""")); assertFalse(f.active())
        assertEquals("failed", cap.executor.execute(obj("""{"action":"x"}""")).status)
    }
}
