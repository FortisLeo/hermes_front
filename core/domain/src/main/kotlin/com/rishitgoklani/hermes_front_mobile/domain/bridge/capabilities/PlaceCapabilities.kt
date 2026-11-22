package com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.sense.Place
import com.rishitgoklani.hermes_front_mobile.domain.sense.PlaceRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

private fun JsonObject.s(k: String) = this[k]?.jsonPrimitive?.contentOrNull
private fun JsonObject.d(k: String) = this[k]?.jsonPrimitive?.doubleOrNull

/** Named places for geofence routines. [here] returns the current (lat, lon) or null. Pair with a routine whose trigger is event=place. */
fun placeCapabilities(repo: PlaceRepository, here: suspend () -> Pair<Double, Double>?): List<Capability> = listOf(
    Capability("place_add", "Save a named place (home, desk, office) as lat/lon + radius_m (default 100), or here=true to use the current location. Use routine_add with trigger {event:'place', place:'home'} to react.",
        """{"type":"object","properties":{"name":{"type":"string"},"lat":{"type":"number"},"lon":{"type":"number"},"radius_m":{"type":"number"},"here":{"type":"boolean"}},"required":["name"]}""") { a ->
        val name = a.s("name")?.trim().orEmpty(); if (name.isEmpty()) return@Capability ActionResult.failed("name required")
        val (lat, lon) = if (a["here"]?.jsonPrimitive?.booleanOrNull == true) here() ?: return@Capability ActionResult.failed("current location unavailable")
        else (a.d("lat") ?: return@Capability ActionResult.failed("lat/lon or here=true required")) to (a.d("lon") ?: return@Capability ActionResult.failed("lat/lon required"))
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return@Capability ActionResult.failed("lat/lon out of range")
        val radius = (a.d("radius_m") ?: 100.0).coerceIn(20.0, 5000.0)
        repo.save(Place(name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-'), name, lat, lon, radius)); ActionResult.completed("Saved place $name (${radius.toInt()} m)")
    },
    Capability("place_list", "List saved places.") { val all = repo.all(); ActionResult.completed(if (all.isEmpty()) "No places" else all.joinToString("\n") { "${it.name} (${it.radiusM.toInt()} m)" }) },
    Capability("place_remove", "Delete a saved place by name.", """{"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}""") { a ->
        val id = a.s("name").orEmpty().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-'); if (repo.delete(id)) ActionResult.completed("Removed") else ActionResult.failed("No such place")
    },
)
