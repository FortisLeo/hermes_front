package com.rishitgoklani.hermes_front_mobile.domain.bridge

import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.lockScreenAnswerCapability
import com.rishitgoklani.hermes_front_mobile.testing.FakeLockScreenNotifier
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class LockScreenAnswerTest {
    private val n = FakeLockScreenNotifier()
    private val cap = lockScreenAnswerCapability(n)
    private suspend fun call(s: String) = cap.executor.execute(Json.parseToJsonElement(s).jsonObject)

    @Test fun showsTextWithDefaultTitle() = runTest {
        assertEquals("completed", call("""{"action":"show","text":"Rain at 5"}""").status)
        assertEquals("Deskbot" to "Rain at 5", n.shown.single())
    }

    @Test fun truncatesLongText() = runTest { call("""{"action":"show","title":"T","text":"${"x".repeat(900)}"}"""); assertEquals(400, n.shown.single().second.length) }

    @Test fun rejectsEmptyText() = runTest { assertEquals("failed", call("""{"action":"show","text":"  "}""").status); assertEquals(0, n.shown.size) }

    @Test fun clearsAndRejectsUnknownAction() = runTest {
        assertEquals("completed", call("""{"action":"clear"}""").status); assertEquals(1, n.cleared)
        assertEquals("failed", call("""{"action":"zap"}""").status)
    }
}
