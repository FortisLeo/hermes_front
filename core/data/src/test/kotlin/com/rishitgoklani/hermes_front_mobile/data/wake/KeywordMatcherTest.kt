package com.rishitgoklani.hermes_front_mobile.data.wake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeywordMatcherTest {
    private val k = listOf("hey bot", "stop")
    @Test fun matchesWholePhrase() = assertEquals("hey bot", KeywordMatcher.match("well hey bot there", k))
    @Test fun caseAndSpacingInsensitive() = assertEquals("hey bot", KeywordMatcher.match("  HEY   Bot ", k))
    @Test fun noPartialWordMatch() { assertNull(KeywordMatcher.match("stopwatch", k)); assertNull(KeywordMatcher.match("hey bottle", k)) }
    @Test fun emptyAndBlankKeywordsSafe() { assertNull(KeywordMatcher.match("", k)); assertNull(KeywordMatcher.match("anything", listOf("", " "))) }
    @Test fun extractsTextOrPartial() { assertEquals("hey bot", KeywordMatcher.textOf("""{"text":"hey bot"}""")); assertEquals("hey", KeywordMatcher.textOf("""{"partial":"hey"}""")); assertEquals("", KeywordMatcher.textOf("not json")) }
    @Test fun grammarIncludesUnknownAndEscapes() { assertEquals("""["hey bot","stop","[unk]"]""", KeywordMatcher.grammar(k)); assertEquals("""["ab","[unk]"]""", KeywordMatcher.grammar(listOf("a\"b"))) }
}
