package com.rishitgoklani.hermes_front_mobile.domain.ui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawParserTest {
    private fun p(s: String) = DrawParser.parse(Json.parseToJsonElement(s).jsonArray)

    @Test fun parsesAllShapeTypes() {
        val (shapes, err) = p("""[{"type":"circle","x":1,"y":2,"r":3},{"type":"rect","x":10,"y":10,"w":5,"h":6,"color":"#FF0000"},{"type":"arrow","x":0,"y":0,"x2":9,"y2":9},{"type":"text","x":1,"y":1,"text":"hi"}]""")
        assertNull(err); assertEquals(4, shapes.size); assertEquals(DrawShape.Rect(10f, 10f, 15f, 16f, 0xFFFF0000), shapes[1]); assertTrue((shapes[2] as DrawShape.Line).arrow)
    }
    @Test fun unknownTypeIsAnError() { assertNotNull(p("""[{"type":"star","x":1,"y":1}]""").second) }
    @Test fun missingFieldIsAnError() { assertTrue(p("""[{"type":"circle","x":1,"y":2}]""").second!!.contains("missing r")) }
    @Test fun badColorIsAnError() { assertNotNull(p("""[{"type":"circle","x":1,"y":2,"r":3,"color":"red"}]""").second) }
    @Test fun tooManyShapesIsAnError() { assertNotNull(p("[" + List(41) { """{"type":"circle","x":1,"y":2,"r":3}""" }.joinToString(",") + "]").second) }
}
