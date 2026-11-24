package com.rishitgoklani.hermes_front_mobile.data.presence

import android.app.NotificationManager
import android.media.AudioManager
import com.rishitgoklani.hermes_front_mobile.data.shell.FileAuditLog
import com.rishitgoklani.hermes_front_mobile.domain.behavior.SleepSchedule
import com.rishitgoklani.hermes_front_mobile.domain.presence.RoutineMiner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PresenceAdaptersTest {
    @get:Rule val tmp = TemporaryFolder()
    private val s = SleepSchedule()
    private fun q(mode: Int = AudioManager.MODE_NORMAL, call: Int = 0, filter: Int = NotificationManager.INTERRUPTION_FILTER_ALL, hour: Int = 12) = QuietSignals.map(mode, call, filter, hour, s)

    @Test fun normalDayIsNotQuiet() { assertFalse(q().quiet) }
    @Test fun androidConstantsMatchTheMapping() {
        assertTrue(q(mode = AudioManager.MODE_IN_CALL).inCall); assertTrue(q(mode = AudioManager.MODE_IN_COMMUNICATION).inCall); assertTrue(q(mode = AudioManager.MODE_RINGTONE).inCall)
        assertTrue(q(filter = NotificationManager.INTERRUPTION_FILTER_NONE).dnd); assertTrue(q(filter = NotificationManager.INTERRUPTION_FILTER_ALARMS).dnd)
        assertTrue(q(filter = NotificationManager.INTERRUPTION_FILTER_PRIORITY).focus); assertFalse(q(filter = NotificationManager.INTERRUPTION_FILTER_PRIORITY).dnd)
    }
    @Test fun anActiveTelephonyCallCounts() { assertTrue(q(call = 2).inCall) }
    @Test fun sleepHoursAreQuiet() { assertTrue(q(hour = 2).sleeping); assertFalse(q(hour = 8).sleeping) }

    @Test fun auditSourceReadsRotatedThenCurrentAndMissingFileIsEmpty() {
        val f = File(tmp.root, "audit.log")
        assertTrue(FileAuditSource(f).lines().isEmpty())
        val log = FileAuditLog(f, maxBytes = 10, clock = { 1000 })
        log.record("torch", "{}", "completed"); log.record("torch", "{}", "completed")
        val lines = FileAuditSource(f).lines()
        assertEquals(2, lines.size); assertTrue(lines.all { it.contains("\ttorch\tcompleted") })
    }

    @Test fun realAuditFormatFeedsTheMiner() {
        val f = File(tmp.root, "audit.log"); var t = 0L
        val log = FileAuditLog(f, clock = { t })
        for (d in 1..3) { t = d * 86_400_000L + 22 * 3_600_000L; log.record("dnd", """{"mode":"priority"}""", "completed") }
        assertEquals("dnd", RoutineMiner().mine(FileAuditSource(f).lines()).single().tool)
    }
}
