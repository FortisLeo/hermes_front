package com.rishitgoklani.hermes_front_mobile.domain.sense

import com.rishitgoklani.hermes_front_mobile.domain.routines.DeviceEvent
import kotlinx.serialization.Serializable
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Serializable data class Place(val id: String, val name: String, val lat: Double, val lon: Double, val radiusM: Double = 100.0)

interface PlaceRepository {
    suspend fun all(): List<Place>
    suspend fun save(place: Place)
    suspend fun delete(id: String): Boolean
}

object Geo {
    /** Great-circle distance in metres. */
    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0; val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }
}

/** Emits enter/exit transitions with hysteresis (exit only past radius + [hysteresisM]) so GPS jitter does not flap. */
class PlaceTracker(private val hysteresisM: Double = 30.0) {
    private val inside = HashSet<String>()

    fun update(lat: Double, lon: Double, places: List<Place>): List<DeviceEvent.Place> {
        val out = mutableListOf<DeviceEvent.Place>()
        places.forEach { p ->
            val d = Geo.distanceM(lat, lon, p.lat, p.lon)
            if (p.id !in inside && d <= p.radiusM) { inside += p.id; out += DeviceEvent.Place(p.name, true) }
            else if (p.id in inside && d > p.radiusM + hysteresisM) { inside -= p.id; out += DeviceEvent.Place(p.name, false) }
        }
        inside.retainAll(places.map { it.id }.toSet())
        return out
    }
    fun current(): Set<String> = inside.toSet()
}
