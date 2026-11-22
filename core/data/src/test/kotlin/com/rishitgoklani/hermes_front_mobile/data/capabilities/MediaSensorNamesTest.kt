package com.rishitgoklani.hermes_front_mobile.data.capabilities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MediaSensorNamesTest {
    @Test fun mediaKeysResolveAliases() { assertNotNull(MediaKeys.code("play_pause")); assertEquals(MediaKeys.code("prev"), MediaKeys.code("previous")); assertNull(MediaKeys.code("explode")) }
    @Test fun streamsResolve() { assertEquals(MediaKeys.stream("music"), MediaKeys.stream("MEDIA")); assertNull(MediaKeys.stream("nope")) }
    @Test fun sensorNamesNormalise() { assertEquals(SensorNames.typeOf("light"), SensorNames.typeOf(" Light ")); assertEquals(SensorNames.typeOf("pressure"), SensorNames.typeOf("barometer")); assertEquals(SensorNames.typeOf("step counter"), SensorNames.typeOf("step_counter")); assertNull(SensorNames.typeOf("x")) }
}
