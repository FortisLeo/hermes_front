package com.rishitgoklani.hermes_front_mobile.ui.a2ui

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Tolerant prop readers: model-written JSON is often slightly off (numbers as strings, etc.). Never throw. */
internal fun JsonObject.str(k: String, d: String = ""): String = (this[k] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: d
internal fun JsonObject.num(k: String, d: Float = 0f): Float = (this[k] as? kotlinx.serialization.json.JsonPrimitive)?.let { it.floatOrNull ?: it.contentOrNull?.toFloatOrNull() } ?: d
internal fun JsonObject.flag(k: String, d: Boolean = false): Boolean = (this[k] as? kotlinx.serialization.json.JsonPrimitive)?.let { it.booleanOrNull ?: it.contentOrNull?.toBooleanStrictOrNull() } ?: d
internal fun JsonObject.kids(): List<JsonObject> = (this["children"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
internal fun fraction(value: Float, max: Float): Float = if (max <= 0f) 0f else (value / max).coerceIn(0f, 1f)
internal fun formatRemaining(ms: Long): String { val s = (ms.coerceAtLeast(0) / 1000); val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60; return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec) }
