package com.rishitgoklani.hermes_front_mobile.domain.behavior

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.bridge.CapabilityRegistry
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.quickActionsCapability
import com.rishitgoklani.hermes_front_mobile.domain.face.Reaction
import com.rishitgoklani.hermes_front_mobile.testing.FakeQuickActionsRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BehaviorTests {
    @Test fun sleepScheduleWrapsMidnight() { val s = SleepSchedule(23, 7); assertTrue(s.isSleepTime(23)); assertTrue(s.isSleepTime(2)); assertFalse(s.isSleepTime(7)); assertFalse(s.isSleepTime(12)) }
    @Test fun sleepScheduleSameDay() { val s = SleepSchedule(13, 15); assertTrue(s.isSleepTime(14)); assertFalse(s.isSleepTime(15)) }
    @Test fun equalHoursMeansNeverSleep() = assertFalse(SleepSchedule(5, 5).isSleepTime(5))
    @Test fun idleSilentWhileAsleep() = assertNull(IdleBehavior.next(2, SleepSchedule(), 0.1f))
    @Test fun idleBehaviorsByRoll() {
        val s = SleepSchedule(); assertEquals(Reaction.LookAround, IdleBehavior.next(10, s, 0.1f)); assertEquals(Reaction.Blink, IdleBehavior.next(10, s, 0.5f))
        assertEquals(Reaction.Glance, IdleBehavior.next(10, s, 0.7f)); assertEquals(Reaction.Yawn, IdleBehavior.next(14, s, 0.7f)); assertEquals(Reaction.Nod, IdleBehavior.next(10, s, 0.85f)); assertNull(IdleBehavior.next(10, s, 0.95f))
    }
    private val reg = CapabilityRegistry(listOf(Capability("battery", "b") { ActionResult.completed("80%") }, Capability("boom", "x") { error("kaput") }))
    @Test fun runCapabilityRunsAndHandlesUnknownAndErrors() = runBlocking {
        val run = RunCapability(reg)
        assertEquals("completed", run("battery").status); assertEquals("unknown_tool", run("zzz").status); assertEquals("kaput", run("boom").message)
    }
    @Test fun quickActionsListAndSet() = runBlocking {
        val repo = FakeQuickActionsRepository(); val cap = quickActionsCapability(repo) { reg }
        assertTrue(cap.executor.execute(JsonObject(emptyMap())).message.contains("battery"))
        val r = cap.executor.execute(Json.parseToJsonElement("""{"set":[{"id":"b","label":"Batt","tool":"battery"}]}""").jsonObject)
        assertEquals("completed", r.status); assertEquals(listOf("b"), repo.items.map { it.id })
    }
    @Test fun quickActionsRejectUnknownToolAndTooMany() = runBlocking {
        val repo = FakeQuickActionsRepository(); val cap = quickActionsCapability(repo) { reg }
        assertEquals("failed", cap.executor.execute(Json.parseToJsonElement("""{"set":[{"id":"x","label":"X","tool":"nope"}]}""").jsonObject).status)
        val many = (1..13).joinToString(",") { """{"id":"a$it","label":"A","tool":"battery"}""" }
        assertEquals("failed", cap.executor.execute(Json.parseToJsonElement("""{"set":[$many]}""").jsonObject).status)
        assertEquals(DefaultQuickActions.all.size, repo.items.size)
    }
    @Test fun defaultQuickActionsReferenceRealToolNames() { val known = java.io.File("../../hermes-skills/tools.txt").readLines().toSet(); DefaultQuickActions.all.forEach { assertTrue(it.tool, it.tool in known) } }
}
