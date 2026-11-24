package com.rishitgoklani.hermes_front_mobile.domain.diagnostics

import com.rishitgoklani.hermes_front_mobile.testing.FakeCrashStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashLogTest {
    @Test fun recordsTimeThreadVersionAndStack() {
        val l = CrashLog(FakeCrashStore())
        l.record("main", IllegalStateException("boom"), "1.0 (debug)", 42L)
        val r = l.latest()!!
        assertTrue(r.contains("time: 1970-01-01T00:00:00.042Z (epoch 42)")); assertTrue(r.contains("thread: main")); assertTrue(r.contains("app: 1.0 (debug)")); assertTrue(r.contains("IllegalStateException: boom"))
    }

    @Test fun redactsBearerTokensAndKeys() {
        val l = CrashLog(FakeCrashStore())
        l.record("t", RuntimeException("failed Authorization: Bearer abcdef123456 api_key=SECRETVALUE sk-abcdefghijklmnop0123"), "v", 1)
        val r = l.latest()!!
        assertFalse(r.contains("abcdef123456")); assertFalse(r.contains("SECRETVALUE")); assertFalse(r.contains("sk-abcdefghijklmnop0123"))
        assertTrue(r.contains("[redacted]"))
    }

    @Test fun redactsLongHexTokens() { assertEquals("x [redacted] y", CrashLog.redact("x " + "a1".repeat(20) + " y")) }

    @Test fun keepsOnlyNewestReportsNewestFirst() {
        val l = CrashLog(FakeCrashStore(), maxReports = 2)
        listOf("one", "two", "three").forEachIndexed { i, m -> l.record("t", RuntimeException(m), "v", i.toLong()) }
        assertEquals(2, l.all().size); assertTrue(l.latest()!!.contains("three")); assertTrue(l.all()[1].contains("two"))
    }

    @Test fun truncatesLongReports() {
        val l = CrashLog(FakeCrashStore(), maxReportChars = 200)
        l.record("t", RuntimeException("x".repeat(5000)), "v", 1)
        assertTrue(l.latest()!!.contains("truncated"))
    }

    @Test fun totalSizeStaysUnderCap() {
        val store = FakeCrashStore(); val l = CrashLog(store, maxReports = 10, maxBytes = 1000, maxReportChars = 400)
        repeat(10) { l.record("t", RuntimeException("y".repeat(1000)), "v", it.toLong()) }
        assertTrue(store.text!!.toByteArray().size <= 1000)
    }

    @Test fun clearRemovesEverything() {
        val store = FakeCrashStore(); val l = CrashLog(store)
        l.record("t", RuntimeException("a"), "v", 1); l.clear()
        assertNull(l.latest()); assertNull(store.text)
    }

    @Test fun recordNeverThrowsWhenStoreFails() {
        val l = CrashLog(FakeCrashStore(failWrites = true))
        l.record("t", RuntimeException("a"), "v", 1)
        assertNull(l.latest())
    }
}
