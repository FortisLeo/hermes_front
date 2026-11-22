package com.rishitgoklani.hermes_front_mobile.data.capabilities

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PercentTest {
    @Test fun clampsToRange() { assertEquals(0, Percent.clamp(-5)); assertEquals(100, Percent.clamp(150)) }
    @Test fun mapsPercentToDeviceRange() { assertEquals(15, Percent.toRange(100, 15)); assertEquals(8, Percent.toRange(50, 15)); assertEquals(1, Percent.toRange(0, 255, 1)) }
    @Test fun mapsBack() { assertEquals(53, Percent.fromRange(8, 15)); assertEquals(100, Percent.fromRange(255, 255, 1)); assertEquals(0, Percent.fromRange(1, 255, 1)) }
    @Test fun degenerateRangeIsZero() = assertEquals(0, Percent.fromRange(5, 0))
    @Test fun argParsesIntAndString() {
        assertEquals(40, Percent.arg(JsonObject(mapOf("level" to JsonPrimitive(40))), "level"))
        assertEquals(40, Percent.arg(JsonObject(mapOf("level" to JsonPrimitive("40"))), "level"))
        assertNull(Percent.arg(JsonObject(emptyMap()), "level"))
        assertNull(Percent.arg(JsonObject(mapOf("level" to JsonPrimitive("abc"))), "level"))
    }
}
