package com.rishitgoklani.hermes_front_mobile.data.events

import com.rishitgoklani.hermes_front_mobile.domain.sense.Place
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FilePlaceAndLaunchStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    @Test fun placesPersistReplaceDeleteAndSurviveCorruption() = runBlocking {
        val f = File(tmp.root, "p/places.json"); val r = FilePlaceRepository(f); r.save(Place("home", "Home", 1.0, 2.0)); r.save(Place("home", "Home", 3.0, 4.0))
        assertEquals(listOf(3.0), FilePlaceRepository(f).all().map { it.lat }); assertTrue(r.delete("home")); assertFalse(r.delete("home")); f.writeText("junk"); assertEquals(0, r.all().size)
    }
    @Test fun launchStoreRoundTripAndGarbageLines() { val s = FileLaunchStore(File(tmp.root, "l/launch.txt")); assertEquals(emptyList<Long>(), s.load()); s.save(listOf(1, 2, 3)); assertEquals(listOf(1L, 2L, 3L), s.load()); File(tmp.root, "l/launch.txt").writeText("5\nxx\n7"); assertEquals(listOf(5L, 7L), s.load()) }
}
