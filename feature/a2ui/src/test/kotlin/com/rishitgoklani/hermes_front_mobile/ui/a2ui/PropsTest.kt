package com.rishitgoklani.hermes_front_mobile.ui.a2ui

import com.rishitgoklani.hermes_front_mobile.domain.a2ui.A2uiCatalog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PropsTest {
    private val o = Json.parseToJsonElement("""{"s":"x","n":"3.5","n2":4,"b":"true","kids":1,"children":[{"type":"Text"},5,{"type":"Divider"}]}""").jsonObject
    @Test fun tolerantReaders() { assertEquals("x", o.str("s")); assertEquals("d", o.str("zz", "d")); assertEquals(3.5f, o.num("n"), 0f); assertEquals(4f, o.num("n2"), 0f); assertEquals(9f, o.num("zz", 9f), 0f); assertTrue(o.flag("b")); assertFalse(o.flag("zz")) }
    @Test fun childrenSkipNonObjects() = assertEquals(2, o.kids().size)
    @Test fun fractionClampsAndGuardsZero() { assertEquals(0.5f, fraction(50f, 100f), 0f); assertEquals(1f, fraction(500f, 100f), 0f); assertEquals(0f, fraction(5f, 0f), 0f); assertEquals(0f, fraction(-5f, 100f), 0f) }
    @Test fun formatRemaining() { assertEquals("1:05", formatRemaining(65_000)); assertEquals("1:01:01", formatRemaining(3_661_000)); assertEquals("0:00", formatRemaining(-5)) }
    @Test fun rendererSupportsEveryCatalogComponent() {
        // Keep in sync with A2uiHost's when(): a component in the catalog without a renderer branch would show "Unsupported component".
        val src = java.io.File("src/main/kotlin/com/rishitgoklani/hermes_front_mobile/ui/a2ui/A2uiHost.kt").readText()
        A2uiCatalog.components.keys.forEach { assertTrue("renderer missing $it", src.contains("\"$it\" ->")) }
    }
}
