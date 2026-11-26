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
        val p = posters(); assertTrue(p.save("d8224ba4-756d", false, byteArrayOf(1, 2, 3))); assertTrue(p.save("d8224ba4-756d", false, byteArrayOf(9)))
        assertArrayEquals(byteArrayOf(9), p.read("d8224ba4-756d", false)); assertEquals(listOf("d8224ba4-756d.port.jpg"), p.file("d8224ba4-756d", false)!!.parentFile!!.list()!!.toList())
    }

    @Test fun eachOrientationHasItsOwnPoster() {
        val p = posters(); p.save("a", false, byteArrayOf(1)); assertNull("no landscape poster yet", p.read("a", true))
        p.save("a", true, byteArrayOf(2)); assertArrayEquals(byteArrayOf(1), p.read("a", false)); assertArrayEquals(byteArrayOf(2), p.read("a", true))
    }

    @Test fun missingPosterIsNull() { assertNull(posters().read("nope", false)) }

    @Test fun keysThatAreNotPlainIdsAreRefused() {
        val p = posters()
        for (k in listOf("../escape", "a/b", "", "x".repeat(81))) { assertNull(k, p.file(k, false)); assertFalse(p.save(k, true, byteArrayOf(1))) }
    }

    @Test fun emptyDataIsNotSavedAndDeleteRemovesThePoster() {
        val p = posters(); assertFalse(p.save("a", false, byteArrayOf())); p.save("a", false, byteArrayOf(1)); p.save("a", true, byteArrayOf(1)); p.delete("a")
        assertNull(p.read("a", false)); assertNull(p.read("a", true))
    }
}
