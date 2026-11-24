package com.rishitgoklani.hermes_front_mobile.testing

import com.rishitgoklani.hermes_front_mobile.domain.sense.Place
import com.rishitgoklani.hermes_front_mobile.domain.sense.PlaceRepository

class FakePlaceRepository(places: List<Place> = emptyList(), var failing: Boolean = false) : PlaceRepository {
    val items = LinkedHashMap<String, Place>().also { m -> places.forEach { m[it.id] = it } }
    override suspend fun all(): List<Place> { if (failing) error("disk"); return items.values.toList() }
    override suspend fun save(place: Place) { items[place.id] = place }
    override suspend fun delete(id: String) = items.remove(id) != null
}
