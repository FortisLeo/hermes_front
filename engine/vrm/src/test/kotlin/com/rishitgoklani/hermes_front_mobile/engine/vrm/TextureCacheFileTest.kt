package com.rishitgoklani.hermes_front_mobile.engine.vrm

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TextureCacheFileTest {
    @get:Rule val tmp = TemporaryFolder()
    private val stamp = SourceStamp(10, 20)
    private val rgb = 0x9274; private val rgba = 0x9278

    /** A fake chain from w x h down to 1x1, each level's bytes filled with its level number. */
    private fun chain(w: Int, h: Int): List<Triple<Int, Int, ByteArray>> {
        val out = ArrayList<Triple<Int, Int, ByteArray>>(); var x = w; var y = h; var l = 0
        while (true) { out += Triple(x, y, ByteArray(((x + 3) / 4) * ((y + 3) / 4) * 8) { l.toByte() }); if (x == 1 && y == 1) break; x = maxOf(1, x / 2); y = maxOf(1, y / 2); l++ }
        return out
    }
    private fun written(): java.io.File {
        val b = TextureCacheFile.Builder(2048); b.add(0, rgb, false, chain(2048, 1024)); b.add(3, rgba, true, chain(64, 64))
        return tmp.newFile().also { b.write(it, stamp, 1) }
    }

    @Test fun imagesAndEveryLevelReadBackFromTheMappedFile() {
        val c = TextureCacheFile.read(written(), stamp, 1)!!
        assertEquals(2048, c.cap); assertEquals(setOf(0, 3), c.images.keys)
        val img = c.images.getValue(0); assertEquals(2048, img.width); assertEquals(1024, img.height); assertEquals(12, img.levels.size); assertFalse(img.alpha); assertEquals(rgb, img.glFormat)
        for ((l, level) in img.levels.withIndex()) { val d = c.data(level); assertEquals(level.size, d.remaining()); assertEquals(l.toByte(), d.get(0)) }
        assertTrue(c.images.getValue(3).alpha); assertEquals(rgba, c.images.getValue(3).glFormat)
    }

    @Test fun aSmallerCapStartsFurtherDownTheChain() {
        val img = TextureCacheFile.read(written(), stamp, 1)!!.images.getValue(0)
        assertEquals(0, img.firstLevelFor(2048)); assertEquals(1, img.firstLevelFor(1024)); assertEquals(2, img.firstLevelFor(512)); assertEquals(2, img.firstLevelFor(600))
        assertEquals(11, img.firstLevelFor(0))   // never past the last level
        assertEquals(img.levels.drop(2).sumOf { it.size.toLong() }, img.bytesFrom(2))
    }

    @Test fun coversTellsWhetherEveryUsedImageIsThere() {
        val c = TextureCacheFile.read(written(), stamp, 1)!!; assertTrue(c.covers(listOf(0, 3))); assertFalse(c.covers(listOf(0, 1, 3)))
    }

    @Test fun anotherSourceEncoderVersionOrDamagedFileIsIgnored() {
        val f = written()
        assertNull(TextureCacheFile.read(f, SourceStamp(10, 21), 1)); assertNull(TextureCacheFile.read(f, stamp, 2))
        val cut = tmp.newFile(); cut.writeBytes(f.readBytes().copyOf((f.length() / 2).toInt())); assertNull(TextureCacheFile.read(cut, stamp, 1))
        val junk = tmp.newFile(); junk.writeBytes(ByteArray(100) { 1 }); assertNull(TextureCacheFile.read(junk, stamp, 1))
    }

    @Test fun levelBytesAreReturnedUnchanged() {
        val b = TextureCacheFile.Builder(8); val bytes = ByteArray(8) { (it * 3).toByte() }; b.add(1, rgb, false, listOf(Triple(4, 4, bytes)))
        val f = tmp.newFile(); b.write(f, stamp, 1)
        val c = TextureCacheFile.read(f, stamp, 1)!!; val d = c.data(c.images.getValue(1).levels[0]); assertArrayEquals(bytes, ByteArray(8).also { d.get(it) })
    }
}
