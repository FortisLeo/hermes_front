package com.rishitgoklani.hermes_front_mobile.data.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComponentListTest {
    private val c = "a.b/.Svc"
    @Test fun addToEmpty() = assertEquals(c, ComponentList.add(null, c))
    @Test fun addAppendsWithColon() = assertEquals("x/.y:$c", ComponentList.add("x/.y", c))
    @Test fun addIsIdempotentAndCaseInsensitive() = assertEquals("A.B/.SVC", ComponentList.add("A.B/.SVC", c))
    @Test fun containsDetects() { assertTrue(ComponentList.contains("x:$c", c)); assertFalse(ComponentList.contains("x", c)) }
    @Test fun removeDropsOnlyThatEntry() = assertEquals("x/.y", ComponentList.remove("x/.y:$c", c))
    @Test fun blankEntriesIgnored() = assertEquals(c, ComponentList.add(" : ", c))
}
