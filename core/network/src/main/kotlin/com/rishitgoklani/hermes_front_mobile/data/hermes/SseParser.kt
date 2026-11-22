package com.rishitgoklani.hermes_front_mobile.data.hermes

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Parses OpenAI-style chat-completion SSE lines into content deltas. */
object SseParser {
    private val json = Json { ignoreUnknownKeys = true }

    sealed interface Event {
        data class Delta(val text: String) : Event
        data object Done : Event
    }

    /** Returns null for lines that carry no content (comments, blank, role-only chunks). */
    fun parseLine(line: String): Event? {
        if (!line.startsWith("data:")) return null
        val payload = line.removePrefix("data:").trim()
        if (payload.isEmpty()) return null
        if (payload == "[DONE]") return Event.Done
        val obj = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null
        val content = obj.firstDeltaContent() ?: return null
        return if (content.isEmpty()) null else Event.Delta(content)
    }

    private fun JsonObject.firstDeltaContent(): String? = runCatching {
        this["choices"]!!.jsonArray[0].jsonObject["delta"]!!.jsonObject["content"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()
}
