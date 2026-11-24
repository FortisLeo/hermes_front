package com.rishitgoklani.hermes_front_mobile.domain.sense

import com.rishitgoklani.hermes_front_mobile.domain.power.BatteryStatus
import com.rishitgoklani.hermes_front_mobile.domain.power.BatteryStatusReader
import com.rishitgoklani.hermes_front_mobile.domain.routines.DeviceEvent

/** Battery- and distance-aware poll interval for geofencing (#264): poll often near a place, rarely far away, slower on low battery, back off when fixes fail. */
object GeofencePolicy {
    const val IDLE_MS = 300_000L
    const val MIN_MS = 30_000L
    const val MAX_MS = 1_800_000L

    /** [nearestEdgeM] is the distance to the nearest place boundary (negative inside); null when there are no places. */
    fun nextDelayMs(nearestEdgeM: Double?, battery: BatteryStatus, failures: Int): Long {
        if (nearestEdgeM == null) return IDLE_MS
        var ms = when { nearestEdgeM <= 200 -> 60_000.0; nearestEdgeM <= 2_000 -> 180_000.0; nearestEdgeM <= 10_000 -> 420_000.0; else -> 600_000.0 }
        if (battery.percent in 0..14 && !battery.charging) ms *= 3 else if (battery.percent in 0..29 && !battery.charging) ms *= 2 else if (battery.charging) ms /= 2
        repeat(failures.coerceIn(0, 4)) { ms *= 2 }
        return ms.toLong().coerceIn(MIN_MS, MAX_MS)
    }

    fun validFix(lat: Double, lon: Double) = !lat.isNaN() && !lon.isNaN() && lat in -90.0..90.0 && lon in -180.0..180.0 && !(lat == 0.0 && lon == 0.0)
}

/**
 * One poll step of the geofence loop: get a fix, feed the [PlaceTracker], emit enter/exit events, return how long to sleep.
 * Never throws (except cancellation): a failing location source or a failing event consumer only lengthens the next delay.
 */
class GeofencePoller(
    private val places: PlaceRepository, private val locate: suspend () -> Pair<Double, Double>?, private val battery: BatteryStatusReader,
    private val tracker: PlaceTracker, private val enabled: () -> Boolean, private val emit: suspend (DeviceEvent) -> Unit,
) {
    var failures = 0; private set

    suspend fun pollOnce(): Long {
        val b = runCatching { battery.read() }.getOrDefault(BatteryStatus(-1, false))
        if (!enabled()) return GeofencePolicy.IDLE_MS
        val ps = try { places.all() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { return GeofencePolicy.nextDelayMs(0.0, b, ++failures) }
        if (ps.isEmpty()) { if (tracker.current().isNotEmpty()) tracker.update(0.0, 0.0, emptyList()); failures = 0; return GeofencePolicy.IDLE_MS }
        val fix = try { locate() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { null }
        if (fix == null || !GeofencePolicy.validFix(fix.first, fix.second)) return GeofencePolicy.nextDelayMs(nearestEdge(ps, null), b, ++failures)
        failures = 0
        tracker.update(fix.first, fix.second, ps).forEach { e -> try { emit(e) } catch (c: kotlinx.coroutines.CancellationException) { throw c } catch (_: Exception) { } }
        return GeofencePolicy.nextDelayMs(nearestEdge(ps, fix), b, 0)
    }

    /** Without a fix we assume we may be close (poll at the base pace, only backoff slows it). */
    private fun nearestEdge(ps: List<Place>, fix: Pair<Double, Double>?): Double =
        if (fix == null) 0.0 else ps.minOf { Geo.distanceM(fix.first, fix.second, it.lat, it.lon) - it.radiusM }
}
