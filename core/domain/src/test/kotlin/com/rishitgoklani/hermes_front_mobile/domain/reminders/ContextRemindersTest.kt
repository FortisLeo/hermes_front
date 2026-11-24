package com.rishitgoklani.hermes_front_mobile.domain.reminders

import com.rishitgoklani.hermes_front_mobile.domain.routines.DeviceEvent
import com.rishitgoklani.hermes_front_mobile.testing.FakeNotificationPoster
import com.rishitgoklani.hermes_front_mobile.testing.FakeSpeechSynthesizer
import com.rishitgoklani.hermes_front_mobile.testing.FakeStateStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextRemindersTest {
    private val book = ContextReminderBook(FakeStateStore()); private val poster = FakeNotificationPoster(); private val tts = FakeSpeechSynthesizer()
    private var now = 0L
    private val engine = ContextReminderEngine(book, poster, tts) { now }
    private val min = 60_000L
    private fun state(id: String) = book.all().first { it.id == id }.state

    @Test fun staysArmedUntilConditionMatches() {
        book.upsert(ContextReminder("r", "Take the keys", ReminderCondition.PLACE_ENTER, "home"))
        engine.onEvent(DeviceEvent.Place("Office", true)); engine.onEvent(DeviceEvent.Place("home", false)); engine.onEvent(DeviceEvent.ChargerConnected())
        assertEquals(ReminderState.ARMED, state("r")); assertTrue(poster.posted.isEmpty())
        engine.onEvent(DeviceEvent.Place("Home", true))
        assertEquals(ReminderState.NAGGING, state("r")); assertEquals("Reminder: Take the keys", tts.spoken.single())
    }

    @Test fun chargerReminderFiresOnPlugIn() {
        book.upsert(ContextReminder("c", "Take pills", ReminderCondition.CHARGER)); engine.onEvent(DeviceEvent.ChargerConnected(40))
        assertEquals(ReminderState.NAGGING, state("c"))
    }

    @Test fun nagsEveryIntervalUntilDone() = runTest {
        book.upsert(ContextReminder("c", "Take pills", ReminderCondition.CHARGER, nagEveryMin = 5)); engine.onEvent(DeviceEvent.ChargerConnected())
        now = 3 * min; engine.tick(now); assertEquals(1, poster.posted.size)
        now = 5 * min; engine.tick(now); assertEquals(2, poster.posted.size)
        now = 10 * min; engine.tick(now); assertEquals(3, poster.posted.size)
        assertEquals(1, book.done("pills")); now = 20 * min; engine.tick(now); assertEquals(3, poster.posted.size); assertEquals(ReminderState.DONE, state("c"))
    }

    @Test fun expiresAfterMaxNags() = runTest {
        book.upsert(ContextReminder("c", "x", ReminderCondition.CHARGER, nagEveryMin = 1, maxNags = 2)); engine.onEvent(DeviceEvent.ChargerConnected())
        now = min; engine.tick(now); now = 2 * min; engine.tick(now)
        assertEquals(ReminderState.EXPIRED, state("c")); assertEquals(2, poster.posted.size)
    }

    @Test fun doneTwiceAndRemoveBehave() {
        book.upsert(ContextReminder("a", "Water plants", ReminderCondition.CHARGER)); assertEquals(1, book.done("a")); assertEquals(0, book.done("a"))
        assertTrue(book.remove("a")); assertTrue(!book.remove("a"))
    }

    @Test fun toolAddListDoneRemoveAndValidation() = runTest {
        val cap = contextReminderCapability(book)
        suspend fun call(s: String) = cap.executor.execute(Json.parseToJsonElement(s).jsonObject)
        assertEquals("failed", call("""{"action":"add","text":"x"}""").status)
        assertEquals("failed", call("""{"action":"add","text":"x","when":"place"}""").status)
        assertEquals("failed", call("""{"action":"add","text":"x","when":"charger","nag_every_min":0}""").status)
        assertEquals("completed", call("""{"action":"add","text":"Bring laptop","when":"place","place":"office","id":"laptop"}""").status)
        assertTrue(call("""{"action":"list"}""").message.contains("laptop [armed]"))
        engine.onEvent(DeviceEvent.Place("Office", true))
        assertEquals("completed", call("""{"action":"done","text":"laptop"}""").status)
        assertEquals("failed", call("""{"action":"done","id":"laptop"}""").status)
        assertEquals("completed", call("""{"action":"remove","id":"laptop"}""").status)
    }

    @Test fun corruptStoreIsEmpty() = assertTrue(ContextReminderBook(FakeStateStore(mutableMapOf("context_reminders" to "nope"))).all().isEmpty())
}
