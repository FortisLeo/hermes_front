package com.rishitgoklani.hermes_front_mobile.ui.avatar

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AvatarPostersTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun posters() = AvatarPosters(tmp.newFolder("posters"))

    @Test fun savedPosterReadsBackAndReplacesTheOldOne() {
        val p = posters(); assertTrue(p.save("d8224ba4-756d", byteArrayOf(1, 2, 3))); assertTrue(p.save("d8224ba4-756d", byteArrayOf(9)))
        assertArrayEquals(byteArrayOf(9), p.read("d8224ba4-756d")); assertEquals(listOf("d8224ba4-756d.jpg"), p.file("d8224ba4-756d")!!.parentFile!!.list()!!.toList())
    }

    @Test fun missingPosterIsNull() { assertNull(posters().read("nope")) }

    @Test fun keysThatAreNotPlainIdsAreRefused() {
        val p = posters()
        for (k in listOf("../escape", "a/b", "", "x".repeat(81))) { assertNull(k, p.file(k)); assertFalse(p.save(k, byteArrayOf(1))) }
    }

    @Test fun emptyDataIsNotSavedAndDeleteRemovesThePoster() {
        val p = posters(); assertFalse(p.save("a", byteArrayOf())); p.save("a", byteArrayOf(1)); p.delete("a"); assertNull(p.read("a"))
    }
}
