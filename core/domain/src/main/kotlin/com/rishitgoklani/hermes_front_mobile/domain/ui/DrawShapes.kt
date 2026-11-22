package com.rishitgoklani.hermes_front_mobile.domain.ui

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Annotation shapes for the on-screen drawing overlay (#60). Coordinates are screen pixels. */
sealed interface DrawShape {
    val color: Long
    data class Circle(val x: Float, val y: Float, val r: Float, override val color: Long) : DrawShape
    data class Rect(val l: Float, val t: Float, val r: Float, val b: Float, override val color: Long) : DrawShape
    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val arrow: Boolean, override val color: Long) : DrawShape
    data class Label(val x: Float, val y: Float, val text: String, override val color: Long) : DrawShape
}

object DrawParser {
    const val MAX_SHAPES = 40
    private const val DEFAULT = 0xFFFFEB3BL

    /** Returns shapes or an error message. Unknown types and missing fields are errors, never silently skipped. */
    fun parse(arr: JsonArray): Pair<List<DrawShape>, String?> {
        if (arr.size > MAX_SHAPES) return emptyList<DrawShape>() to "at most $MAX_SHAPES shapes"
        val out = mutableListOf<DrawShape>()
        arr.forEachIndexed { i, el ->
            val o = el as? JsonObject ?: return emptyList<DrawShape>() to "shape $i is not an object"
            fun f(k: String) = o[k]?.jsonPrimitive?.floatOrNull
            val color = parseColor(o["color"]?.jsonPrimitive?.contentOrNull) ?: return emptyList<DrawShape>() to "shape $i: color must be #RRGGBB"
            val s: DrawShape = when (val t = o["type"]?.jsonPrimitive?.contentOrNull) {
                "circle" -> DrawShape.Circle(f("x") ?: return err(i, "x"), f("y") ?: return err(i, "y"), f("r") ?: return err(i, "r"), color)
                "rect" -> DrawShape.Rect(f("x") ?: return err(i, "x"), f("y") ?: return err(i, "y"), (f("x")!! + (f("w") ?: return err(i, "w"))), (f("y")!! + (f("h") ?: return err(i, "h"))), color)
                "line", "arrow" -> DrawShape.Line(f("x") ?: return err(i, "x"), f("y") ?: return err(i, "y"), f("x2") ?: return err(i, "x2"), f("y2") ?: return err(i, "y2"), t == "arrow", color)
                "text" -> DrawShape.Label(f("x") ?: return err(i, "x"), f("y") ?: return err(i, "y"), (o["text"]?.jsonPrimitive?.contentOrNull ?: return err(i, "text")).take(80), color)
                else -> return emptyList<DrawShape>() to "shape $i: unknown type '$t'"
            }
            out += s
        }
        return out to null
    }

    private fun err(i: Int, field: String) = emptyList<DrawShape>() to "shape $i: missing $field"
    private fun parseColor(s: String?): Long? = if (s == null) DEFAULT else Regex("#([0-9a-fA-F]{6})").matchEntire(s)?.let { 0xFF000000L or it.groupValues[1].toLong(16) }
}
