package com.rishitgoklani.hermes_front_mobile.data.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileStateStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun roundTripsAndOverwrites() {
        val s = FileStateStore(File(tmp.root, "state"))
        assertNull(s.load("a")); s.save("a", "1"); s.save("a", "2")
        assertEquals("2", s.load("a")); assertEquals("2", FileStateStore(File(tmp.root, "state")).load("a"))
        assertTrue(File(tmp.root, "state").listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test fun rejectsUnsafeKeys() {
        val s = FileStateStore(tmp.root)
        assertTrue(runCatching { s.save("../evil", "x") }.isFailure); assertNull(s.load("../evil"))
    }
}
