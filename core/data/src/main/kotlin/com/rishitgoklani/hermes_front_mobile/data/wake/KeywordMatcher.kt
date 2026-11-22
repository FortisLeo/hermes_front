package com.rishitgoklani.hermes_front_mobile.data.wake

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Pure matching of recognizer output against wanted phrases (whole-word, case-insensitive). */
object KeywordMatcher {
    fun match(text: String, keywords: List<String>): String? {
        val t = " " + text.lowercase().trim().replace(Regex("\\s+"), " ") + " "
        if (t.isBlank()) return null
        return keywords.firstOrNull { k -> k.isNotBlank() && t.contains(" ${k.lowercase().trim()} ") }
    }

    /** Vosk results are JSON: {"text":"…"} for finals and {"partial":"…"} for partials. */
    fun textOf(json: String): String = runCatching { Json.parseToJsonElement(json).jsonObject.let { o -> (o["text"]?.jsonPrimitive?.contentOrNull).orEmpty().ifEmpty { (o["partial"]?.jsonPrimitive?.contentOrNull).orEmpty() } } }.getOrDefault("")

    /** Grammar for a restricted vocabulary: the phrases plus the unknown token, as a JSON array string. */
    fun grammar(keywords: List<String>): String = (keywords.map { it.lowercase() } + "[unk]").joinToString(prefix = "[", postfix = "]", separator = ",") { "\"" + it.replace("\"", "") + "\"" }
}
