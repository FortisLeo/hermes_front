package com.rishitgoklani.hermes_front_mobile.domain.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SystemPureTests {
    @get:Rule val tmp = TemporaryFolder()
    private var now = 1_000L
    private val book = TimerBook { now }

    @Test fun timerCountsDownAndCancels() {
        book.start("Tea", 60_000); now += 20_000
        assertEquals(40_000L, book.remaining("tea")); assertTrue(book.cancel("TEA")); assertNull(book.remaining("tea"))
    }
    @Test fun timerNeverNegative() { book.start("x", 10); now += 1000; assertEquals(0L, book.remaining("x")) }
    @Test fun timerRejectsBadDuration() { assertThrows(IllegalArgumentException::class.java) { book.start("x", 0) }; assertThrows(IllegalArgumentException::class.java) { book.start("x", 25 * 3600_000L) } }
    @Test fun listSortsByInsertion() { book.start("a", 1000); book.start("b", 2000); assertEquals(listOf("a", "b"), book.list().map { it.first.name }) }
    @Test fun stopwatchElapsed() { book.stopwatchStart("lap"); now += 5000; assertEquals(5000L, book.stopwatchStop("lap")); assertNull(book.stopwatchStop("lap")) }
    @Test fun parseDurationForms() {
        assertEquals(5400_000L, TimerBook.parseDuration("1h 30m")); assertEquals(90_000L, TimerBook.parseDuration("90s")); assertEquals(300_000L, TimerBook.parseDuration("5 min"))
        assertEquals(5400_000L, TimerBook.parseDuration("1:30:00")); assertEquals(90_000L, TimerBook.parseDuration("1:30")); assertEquals(45_000L, TimerBook.parseDuration("45")); assertEquals(90_000L, TimerBook.parseDuration("1.5 minutes"))
        assertNull(TimerBook.parseDuration("soon")); assertNull(TimerBook.parseDuration(""))
    }
    @Test fun formatDuration() { assertEquals("1h 2m 3s", TimerBook.format(3723_000)); assertEquals("5s", TimerBook.format(5000)); assertEquals("2m 0s", TimerBook.format(120_000)) }

    @Test fun sandboxAllowsInsideRejectsOutsideAndTraversal() {
        val root = tmp.newFolder("root"); File(root, "a.txt").writeText("x"); val sb = FileSandbox(listOf(root))
        assertNotNull(sb.resolve(File(root, "a.txt").path)); assertNotNull(sb.resolve(root.path))
        assertNull(sb.resolve(File(root, "../outside").path)); assertNull(sb.resolve("/etc/passwd")); assertNull(sb.resolve("")); assertNull(sb.resolve(root.path + "evil"))
    }
    @Test fun sandboxBlocksSymlinkEscape() {
        val root = tmp.newFolder("r2"); val outside = tmp.newFolder("o2"); val link = File(root, "link")
        java.nio.file.Files.createSymbolicLink(link.toPath(), outside.toPath()); assertNull(FileSandbox(listOf(root)).resolve(File(link, "f").path))
    }
    @Test fun settingsGuard() { assertTrue(SettingsGuard.isProtected("DEVICE_PROVISIONED")); assertFalse(SettingsGuard.isProtected("screen_off_timeout")) }
    @Test fun urlGuard() {
        assertNull(UrlGuard.check("https://example.com/a?b=1")); assertEquals("only http/https allowed", UrlGuard.check("file:///etc/passwd")); assertEquals("only http/https allowed", UrlGuard.check("javascript:alert(1)"))
        assertNotNull(UrlGuard.check("http://user:pw@host/")); assertNotNull(UrlGuard.check("not a url")); assertNotNull(UrlGuard.check("http://"))
    }
}
