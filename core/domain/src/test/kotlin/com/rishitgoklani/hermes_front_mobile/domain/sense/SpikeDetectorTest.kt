package com.rishitgoklani.hermes_front_mobile.domain.sense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SpikeDetectorTest {
    @Test fun flagsSpikeOnceWithCooldown() { var t = 0L; val d = SpikeDetector(18.0, 10_000) { t }
        assertNull(d.update(-60.0)); assertNull(d.update(-58.0)); assertNotNull(d.update(-20.0)); t += 1000; assertNull(d.update(-20.0)); t += 20_000; assertNotNull(d.update(-15.0)) }
    @Test fun slowDriftIsAbsorbed() { val d = SpikeDetector(18.0, 0) { 0 }; var v = -60.0; repeat(40) { v += 1.0; assertNull(d.update(v)) } }
    @Test fun reportsMargin() { val d = SpikeDetector(10.0, 0) { 0 }; d.update(-50.0); assertEquals(30.0, d.update(-20.0)!!, 3.5) }
}
