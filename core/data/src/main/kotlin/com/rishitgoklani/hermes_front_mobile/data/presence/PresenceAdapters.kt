package com.rishitgoklani.hermes_front_mobile.data.presence

import android.app.NotificationManager
import android.content.ContentUris
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.provider.CalendarContract
import android.telephony.TelephonyManager
import com.rishitgoklani.hermes_front_mobile.domain.behavior.SleepSchedule
import com.rishitgoklani.hermes_front_mobile.domain.presence.AmbientSense
import com.rishitgoklani.hermes_front_mobile.domain.presence.AmbientSnapshot
import com.rishitgoklani.hermes_front_mobile.domain.presence.AuditSource
import com.rishitgoklani.hermes_front_mobile.domain.presence.QuietContext
import com.rishitgoklani.hermes_front_mobile.domain.presence.QuietContextPort
import com.rishitgoklani.hermes_front_mobile.domain.presence.UpcomingEvent
import com.rishitgoklani.hermes_front_mobile.domain.presence.UpcomingEvents
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

/** Pure mapping from raw Android values to a [QuietContext]; separated so it runs on the JVM. */
object QuietSignals {
    // AudioManager.MODE_RINGTONE=1, MODE_IN_CALL=2, MODE_IN_COMMUNICATION=3; TelephonyManager.CALL_STATE_IDLE=0.
    // NotificationManager.INTERRUPTION_FILTER_*: UNKNOWN=0, ALL=1, PRIORITY=2, NONE=3, ALARMS=4.
    fun map(audioMode: Int, callState: Int, interruptionFilter: Int, hour: Int, schedule: SleepSchedule): QuietContext = QuietContext(
        inCall = audioMode in 1..3 || callState != 0,
        dnd = interruptionFilter == 3 || interruptionFilter == 4,
        focus = interruptionFilter == 2,
        sleeping = schedule.isSleepTime(hour),
    )
}

/** Reads call state (audio mode needs no permission; telephony is best effort), Do Not Disturb and the sleep schedule. */
class AndroidQuietContext(private val context: Context, private val schedule: () -> SleepSchedule = { SleepSchedule() }, private val hour: () -> Int) : QuietContextPort {
    override suspend fun current(): QuietContext {
        val mode = runCatching { context.getSystemService(AudioManager::class.java).mode }.getOrDefault(AudioManager.MODE_NORMAL)
        val call = runCatching { context.getSystemService(TelephonyManager::class.java).callState }.getOrDefault(0)
        val filter = runCatching { context.getSystemService(NotificationManager::class.java).currentInterruptionFilter }.getOrDefault(1)
        return QuietSignals.map(mode, call, filter, hour(), schedule())
    }
}

/** Next calendar event starting soon, from CalendarContract.Instances (needs READ_CALENDAR; without it there is simply no event). */
class AndroidUpcomingEvents(private val context: Context, private val clock: () -> Long = System::currentTimeMillis) : UpcomingEvents {
    override suspend fun next(withinMs: Long): UpcomingEvent? = runCatching {
        val now = clock()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it, now); ContentUris.appendId(it, now + withinMs) }.build()
        context.contentResolver.query(uri, arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY), null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) { if (c.getInt(3) == 0 && c.getLong(2) >= now) return@runCatching UpcomingEvent(c.getString(0).orEmpty(), c.getString(1).orEmpty(), c.getLong(2)) }
            null
        }
    }.getOrNull()
}

/** One light-sensor reading plus whatever noise level [noiseDbfs] can provide (null when the mic is busy). */
class AndroidAmbientSense(private val context: Context, private val noiseDbfs: suspend () -> Int?) : AmbientSense {
    override suspend fun read(): AmbientSnapshot {
        val sm = context.getSystemService(SensorManager::class.java)
        val light = sm.getDefaultSensor(Sensor.TYPE_LIGHT)
        val lux = if (light == null) null else withTimeoutOrNull(2000) {
            suspendCancellableCoroutine<Float> { c ->
                val l = object : SensorEventListener {
                    override fun onSensorChanged(e: SensorEvent) { sm.unregisterListener(this); if (c.isActive) c.resume(e.values[0]) }
                    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
                }
                sm.registerListener(l, light, SensorManager.SENSOR_DELAY_NORMAL); c.invokeOnCancellation { sm.unregisterListener(l) }
            }
        }
        return AmbientSnapshot(lux, runCatching { noiseDbfs() }.getOrNull())
    }
}

/** Reads the audit log written by FileAuditLog: the rotated `.1` file first, then the current one. */
class FileAuditSource(private val file: File) : AuditSource {
    override fun lines(): List<String> = runCatching {
        listOf(File(file.path + ".1"), file).filter { it.isFile }.flatMap { it.readLines() }
    }.getOrDefault(emptyList())
}
