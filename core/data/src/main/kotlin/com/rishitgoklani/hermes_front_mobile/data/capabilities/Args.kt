package com.rishitgoklani.hermes_front_mobile.data.capabilities

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

fun JsonObject.str(k: String): String? = this[k]?.jsonPrimitive?.contentOrNull
fun JsonObject.int(k: String): Int? = this[k]?.jsonPrimitive?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }
fun JsonObject.long(k: String): Long? = this[k]?.jsonPrimitive?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }
fun JsonObject.dbl(k: String): Double? = this[k]?.jsonPrimitive?.doubleOrNull
fun JsonObject.bool(k: String): Boolean? = this[k]?.jsonPrimitive?.let { it.booleanOrNull ?: it.contentOrNull?.toBooleanStrictOrNull() }
