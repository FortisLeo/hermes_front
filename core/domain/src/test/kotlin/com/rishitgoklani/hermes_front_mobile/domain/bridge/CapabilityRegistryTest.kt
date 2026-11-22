package com.rishitgoklani.hermes_front_mobile.domain.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CapabilityRegistryTest {
    private fun cap(name: String) = Capability(name, "d") { ActionResult.completed() }

    @Test fun listsInRegistrationOrder() =
        assertEquals(listOf("b", "a"), CapabilityRegistry(listOf(cap("b"), cap("a"))).list().map { it.name })

    @Test fun getUnknownIsNull() = assertNull(CapabilityRegistry().get("x"))

    @Test fun duplicateNameRejected() {
        val r = CapabilityRegistry(listOf(cap("a")))
        assertThrows(IllegalArgumentException::class.java) { r.register(cap("a")) }
    }

    @Test fun completedHelperSetsStatus() = assertEquals("completed", ActionResult.completed("ok").status)
}
