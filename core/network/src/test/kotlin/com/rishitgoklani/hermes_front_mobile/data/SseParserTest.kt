package com.rishitgoklani.hermes_front_mobile.data

import com.rishitgoklani.hermes_front_mobile.data.hermes.SseParser
import com.rishitgoklani.hermes_front_mobile.data.hermes.SseParser.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SseParserTest {
    @Test fun parsesDelta() =
        assertEquals(Event.Delta("Hi"), SseParser.parseLine("""data: {"choices":[{"delta":{"content":"Hi"}}]}"""))

    @Test fun parsesDone() = assertEquals(Event.Done, SseParser.parseLine("data: [DONE]"))

    @Test fun ignoresRoleOnlyChunk() =
        assertNull(SseParser.parseLine("""data: {"choices":[{"delta":{"role":"assistant"}}]}"""))

    @Test fun ignoresCommentsBlankAndGarbage() {
        assertNull(SseParser.parseLine(": keep-alive"))
        assertNull(SseParser.parseLine(""))
        assertNull(SseParser.parseLine("data: {not json"))
    }
}
