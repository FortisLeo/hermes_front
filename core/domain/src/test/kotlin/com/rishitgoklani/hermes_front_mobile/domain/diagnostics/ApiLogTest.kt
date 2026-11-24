package com.rishitgoklani.hermes_front_mobile.domain.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiLogTest {
    private var now = 1_000L
    private fun log(capacity: Int = 300, secrets: List<String> = emptyList(), max: Int = 4_000) = InMemoryApiLog(capacity, { now }, { secrets }, max)

    @Test fun newestEntryComesFirst() {
        val l = log(); l.record(ApiSource.Hermes, "a", "", "", ApiStatus.Ok); l.record(ApiSource.Bridge, "b", "", "", ApiStatus.Ok)
        assertEquals(listOf("b", "a"), l.entries.value.map { it.label })
    }

    @Test fun anOpenEntryStartsPendingAndGrowsWithStreamedText() {
        val l = log(); val id = l.begin(ApiSource.Hermes, "chat", "hi")
        assertEquals(ApiStatus.Pending, l.entries.value.single().status)
        l.append(id, "Hel"); l.append(id, "lo")
        assertEquals("Hello", l.entries.value.single().response)
    }

    @Test fun finishingRecordsStatusAndDuration() {
        val l = log(); val id = l.begin(ApiSource.Hermes, "chat", "hi"); now += 250
        l.finish(id, ApiStatus.Ok)
        val e = l.entries.value.single(); assertEquals(ApiStatus.Ok, e.status); assertEquals(250L, e.durationMs)
    }

    @Test fun finishCanReplaceTheResponse() {
        val l = log(); val id = l.begin(ApiSource.Bridge, "t", ""); l.append(id, "partial"); l.finish(id, ApiStatus.Error, "HTTP 500")
        assertEquals("HTTP 500", l.entries.value.single().response)
    }

    @Test fun theOldestEntriesDropAtCapacity() {
        val l = log(capacity = 3); repeat(5) { l.record(ApiSource.Hermes, "e$it", "", "", ApiStatus.Ok) }
        assertEquals(listOf("e4", "e3", "e2"), l.entries.value.map { it.label })
    }

    @Test fun clearEmptiesTheLog() { val l = log(); l.record(ApiSource.Hermes, "a", "", "", ApiStatus.Ok); l.clear(); assertTrue(l.entries.value.isEmpty()) }

    @Test fun updatingAnEntryThatWasDroppedIsHarmless() {
        val l = log(capacity = 1); val first = l.begin(ApiSource.Hermes, "old", ""); l.begin(ApiSource.Hermes, "new", "")
        l.append(first, "late"); l.finish(first, ApiStatus.Ok)
        assertEquals(listOf("new"), l.entries.value.map { it.label })
    }

    @Test fun redactionHidesKeysInUrlsHeadersAndJson() {
        val r = ApiRedactor.redact("""GET wss://x/ws?key=AIzaSECRET&a=1 Authorization: Bearer abc.def-123 {"token":"t0k3n","name":"x"}""")
        assertFalse("AIzaSECRET" in r || "abc.def-123" in r || "t0k3n" in r)
        assertTrue("key=***&a=1" in r && "\"token\":\"***\"" in r && "\"name\":\"x\"" in r)
    }

    @Test fun knownSecretsAreRemovedWhereverTheyAppear() {
        val l = log(secrets = listOf("my-private-key")); l.record(ApiSource.GeminiLive, "x", "using my-private-key", "echo my-private-key", ApiStatus.Ok)
        val e = l.entries.value.single(); assertFalse("my-private-key" in e.request || "my-private-key" in e.response)
    }

    @Test fun imagesAndAudioBlobsAreCollapsed() {
        val b64 = "A".repeat(5_000)
        val r = ApiRedactor.redact("""{"url":"data:image/jpeg;base64,$b64","data":"$b64"}""")
        assertTrue("<data uri" in r && "<blob 5000 chars>" in r && r.length < 200)
    }

    @Test fun longFieldsAreTruncatedWithTheHiddenLength() {
        val l = log(max = 10); l.record(ApiSource.Hermes, "x", "0123456789ABCDEF", "", ApiStatus.Ok)
        assertEquals("0123456789… [+6 chars]", l.entries.value.single().request)
    }

    @Test fun streamedTextIsRedactedAsItAccumulates() {
        val l = log(secrets = listOf("SECRETVALUE")); val id = l.begin(ApiSource.Hermes, "c", "")
        l.append(id, "the key is SECR"); l.append(id, "ETVALUE ok")
        assertFalse("SECRETVALUE" in l.entries.value.single().response)
    }

    @Test fun formatShowsSourceLabelStatusDurationAndBothSides() {
        val l = log(); l.record(ApiSource.Bridge, "call battery", "{}", "85%", ApiStatus.Ok, 12)
        val t = ApiLogFormat.entry(l.entries.value.single())
        assertTrue("Bridge" in t && "call battery" in t && "ok" in t && "12 ms" in t && "> {}" in t && "< 85%" in t)
    }

    @Test fun formatMarksMissingBodies() {
        val t = ApiLogFormat.entry(ApiLogEntry(1, 0, ApiSource.Hermes, "x", ""))
        assertTrue("(empty)" in t && "(none)" in t); assertNull(null); assertNotNull(t)
    }
}
