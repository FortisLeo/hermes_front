package com.rishitgoklani.hermes_front_mobile.data.capabilities

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Pure helpers shared by volume/brightness tools. */
object Percent {
    fun clamp(p: Int) = p.coerceIn(0, 100)
    fun toRange(percent: Int, max: Int, min: Int = 0): Int = min + Math.round(clamp(percent) / 100f * (max - min))
    fun fromRange(value: Int, max: Int, min: Int = 0): Int = if (max <= min) 0 else clamp(Math.round((value - min) * 100f / (max - min)))
    fun arg(args: JsonObject, key: String): Int? = args[key]?.jsonPrimitive?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }
}
