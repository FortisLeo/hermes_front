package com.rishitgoklani.hermes_front_mobile.domain.sense

import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.placeCapabilities
import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.safeModeCapability
import com.rishitgoklani.hermes_front_mobile.domain.routines.DeviceEvent
import com.rishitgoklani.hermes_front_mobile.domain.safety.CrashLoopGuard
import com.rishitgoklani.hermes_front_mobile.domain.safety.LaunchStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceSafeModeTests {
    private val home = Place("home", "Home", 10.0000, 20.0000, 100.0)
    private fun j(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    @Test fun distanceKnownValues() { assertEquals(0.0, Geo.distanceM(1.0, 1.0, 1.0, 1.0), 0.001); assertEquals(111_195.0, Geo.distanceM(0.0, 0.0, 1.0, 0.0), 200.0) }
    @Test fun enterThenExitWithHysteresis() {
        val t = PlaceTracker(30.0)
        assertTrue(t.update(40.0, 70.0, listOf(home)).isEmpty())
        assertEquals(listOf(DeviceEvent.Place("Home", true)), t.update(10.0000, 20.0000, listOf(home)))
        assertTrue(t.update(10.0000, 20.0000, listOf(home)).isEmpty())
        assertTrue(t.update(10.00055, 20.0000, listOf(home)).isEmpty())            // ~61 m: outside radius but inside hysteresis band? (100+30) -> still inside
        assertEquals(listOf(DeviceEvent.Place("Home", false)), t.update(10.0030, 20.0000, listOf(home)))   // ~330 m
    }
    @Test fun removedPlaceForgotten() { val t = PlaceTracker(); t.update(10.0000, 20.0000, listOf(home)); t.update(0.0, 0.0, emptyList()); assertTrue(t.current().isEmpty()) }

    private class Repo : PlaceRepository { val m = LinkedHashMap<String, Place>(); override suspend fun all() = m.values.toList(); override suspend fun save(place: Place) { m[place.id] = place }; override suspend fun delete(id: String) = m.remove(id) != null }

    @Test fun placeAddExplicitHereAndValidation() = runBlocking {
        val repo = Repo(); val c = placeCapabilities(repo) { 1.0 to 2.0 }.associateBy { it.name }
        assertEquals("completed", c["place_add"]!!.executor.execute(j("""{"name":"Home Sweet","lat":10.5,"lon":20.5,"radius_m":150}""")).status); assertEquals("home-sweet", repo.m.keys.single())
        assertEquals("completed", c["place_add"]!!.executor.execute(j("""{"name":"Desk","here":true}""")).status); assertEquals(1.0, repo.m["desk"]!!.lat, 0.0)
        assertEquals("failed", c["place_add"]!!.executor.execute(j("""{"name":"X","lat":200,"lon":0}""")).status); assertEquals("failed", c["place_add"]!!.executor.execute(j("""{"name":"X"}""")).status)
        assertEquals(20.0, run { c["place_add"]!!.let { runBlocking { it.executor.execute(j("""{"name":"Tiny","lat":1,"lon":1,"radius_m":1}""")) } }; repo.m["tiny"]!!.radiusM }, 0.0)
        assertTrue(c["place_list"]!!.executor.execute(j("{}")).message.contains("Home Sweet")); assertEquals("completed", c["place_remove"]!!.executor.execute(j("""{"name":"Home Sweet"}""")).status)
    }
    @Test fun placeAddHereUnavailable() = runBlocking { assertEquals("failed", placeCapabilities(Repo()) { null }.first().executor.execute(j("""{"name":"A","here":true}""")).status) }

    private class Mem : LaunchStore { var t = emptyList<Long>(); override fun load() = t; override fun save(times: List<Long>) { t = times } }
    @Test fun crashLoopEntersSafeModeAfterThresholdWithinWindow() {
        var now = 0L; val g = CrashLoopGuard(Mem(), { now }, threshold = 4, windowMs = 300_000)
        assertFalse(g.onLaunch()); now += 10_000; assertFalse(g.onLaunch()); now += 10_000; assertFalse(g.onLaunch()); now += 10_000; assertTrue(g.onLaunch())
    }
    @Test fun oldLaunchesExpireAndHealthyClears() {
        var now = 0L; val m = Mem(); val g = CrashLoopGuard(m, { now }, threshold = 3, windowMs = 60_000)
        g.onLaunch(); g.onLaunch(); now += 120_000; assertFalse(g.onLaunch()); g.markHealthy(); assertTrue(m.t.isEmpty())
    }
    @Test fun safeModeToolReports() = runBlocking { assertTrue(safeModeCapability { true }.executor.execute(j("{}")).message.startsWith("SAFE")); assertEquals("Normal", safeModeCapability { false }.executor.execute(j("{}")).message) }
}
