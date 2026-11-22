package com.rishitgoklani.hermes_front_mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SentenceChunkerTest {
    @Test fun emitsCompletedSentencesOnly() {
        val c = SentenceChunker()
        assertEquals(emptyList<String>(), c.feed("Hello wor"))
        assertEquals(listOf("Hello world."), c.feed("ld. How are"))
        assertEquals("How are", c.flush())
    }
    @Test fun terminatorWithoutTrailingSpaceWaits() {
        val c = SentenceChunker()
        assertEquals(emptyList<String>(), c.feed("Version 3.5"))
        assertEquals("Version 3.5", c.flush())
    }
    @Test fun newlineEndsSentence() = assertEquals(listOf("one", "two"), SentenceChunker().feed("one\ntwo\n"))
    @Test fun multipleSentencesInOneDelta() = assertEquals(listOf("A!", "B?"), SentenceChunker().feed("A! B? "))
    @Test fun flushEmptyIsNull() = assertNull(SentenceChunker().flush())
}
