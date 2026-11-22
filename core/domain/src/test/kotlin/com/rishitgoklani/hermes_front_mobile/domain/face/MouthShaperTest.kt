package com.rishitgoklani.hermes_front_mobile.domain.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MouthShaperTest {
    @Test fun blankWordKeepsMouthClosed() { assertEquals(0f, MouthShaper.peak(""), 0f); assertEquals(0f, MouthShaper.peak("..."), 0f) }
    @Test fun openVowelIsWiderThanCloseVowel() { assertTrue(MouthShaper.peak("ha") > MouthShaper.peak("hi")) }
    @Test fun aIsFullyOpen() { assertEquals(1f, MouthShaper.peak("cat"), 0f) }
    @Test fun longWordsAreSoftened() { assertTrue(MouthShaper.peak("catastrophe") < 1f) }
    @Test fun resultStaysInRange() { listOf("hello", "xyz", "Zzz!", "aeiou").forEach { assertTrue(MouthShaper.peak(it) in 0f..1f) } }
}
