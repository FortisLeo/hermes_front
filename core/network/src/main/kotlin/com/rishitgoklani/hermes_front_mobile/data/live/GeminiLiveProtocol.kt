package com.rishitgoklani.hermes_front_mobile.data.live

import com.rishitgoklani.hermes_front_mobile.domain.live.LiveCall
import com.rishitgoklani.hermes_front_mobile.domain.live.LiveConfig
import com.rishitgoklani.hermes_front_mobile.domain.live.LiveEvent
import com.rishitgoklani.hermes_front_mobile.domain.live.LiveResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.Base64

/** Wire format of the Gemini Live (BidiGenerateContent) WebSocket: pure functions from/to JSON text, no I/O. */
object GeminiLiveProtocol {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    const val INPUT_AUDIO_MIME = "audio/pcm;rate=16000"

    fun setup(c: LiveConfig): String = buildJsonObject {
        putJsonObject("setup") {
            put("model", if (c.model.startsWith("models/")) c.model else "models/${c.model}")
            putJsonObject("generationConfig") {
                putJsonArray("responseModalities") { add(JsonPrimitive("AUDIO")) }
                putJsonObject("speechConfig") { putJsonObject("voiceConfig") { putJsonObject("prebuiltVoiceConfig") { put("voiceName", c.voice) } } }
            }
            putJsonObject("systemInstruction") { putJsonArray("parts") { add(buildJsonObject { put("text", c.systemInstruction) }) } }
            if (c.tools.isNotEmpty() || c.googleSearch) putJsonArray("tools") {
                if (c.tools.isNotEmpty()) add(buildJsonObject { putJsonArray("functionDeclarations") { c.tools.forEach { add(declaration(it)) } } })
                if (c.googleSearch) add(buildJsonObject { putJsonObject("googleSearch") {} })
            }
            putJsonObject("realtimeInputConfig") {
                putJsonObject("automaticActivityDetection") { put("silenceDurationMs", c.silenceMs) }
                put("activityHandling", if (c.allowBargeIn) "START_OF_ACTIVITY_INTERRUPTS" else "NO_INTERRUPTION")
            }
            if (c.transcribe) { putJsonObject("inputAudioTranscription") {}; putJsonObject("outputAudioTranscription") {} }
            putJsonObject("sessionResumption") { c.resumeHandle?.let { put("handle", it) } }
            putJsonObject("contextWindowCompression") { putJsonObject("slidingWindow") {} }
        }
    }.toString()

    private fun declaration(t: com.rishitgoklani.hermes_front_mobile.domain.live.LiveToolDecl): JsonObject = buildJsonObject {
        put("name", t.name); put("description", t.description)
        t.parametersJson?.let { put("parameters", json.parseToJsonElement(it)) }
        if (t.nonBlocking) put("behavior", "NON_BLOCKING")
    }

    fun audio(pcm16k: ByteArray): String = realtime("audio", pcm16k, INPUT_AUDIO_MIME)
    fun frame(jpeg: ByteArray): String = realtime("video", jpeg, "image/jpeg")
    fun text(text: String): String = buildJsonObject { putJsonObject("realtimeInput") { put("text", text) } }.toString()
    fun audioStreamEnd(): String = buildJsonObject { putJsonObject("realtimeInput") { put("audioStreamEnd", true) } }.toString()

    private fun realtime(key: String, bytes: ByteArray, mime: String) = buildJsonObject {
        putJsonObject("realtimeInput") { putJsonObject(key) { put("data", Base64.getEncoder().encodeToString(bytes)); put("mimeType", mime) } }
    }.toString()

    fun toolResponses(results: List<LiveResult>): String = buildJsonObject {
        putJsonObject("toolResponse") {
            putJsonArray("functionResponses") { results.forEach { r -> add(buildJsonObject { put("id", r.id); put("name", r.name); put("response", r.response) }) } }
        }
    }.toString()

    /** [events] in the order the server sent them; [setupComplete] is true for the handshake reply. */
    data class Parsed(val events: List<LiveEvent>, val setupComplete: Boolean = false)

    fun parse(text: String): Parsed {
        val root = try { json.parseToJsonElement(text).jsonObject } catch (e: Exception) { return Parsed(emptyList()) }
        val out = ArrayList<LiveEvent>()
        root["serverContent"]?.jsonObject?.let { sc ->
            sc.obj("inputTranscription")?.str("text")?.takeIf { it.isNotEmpty() }?.let { out += LiveEvent.InputText(it) }
            sc.obj("modelTurn")?.get("parts")?.let { it as? JsonArray }?.forEach { part ->
                part.jsonObject.obj("inlineData")?.str("data")?.let { b64 -> out += LiveEvent.AudioOut(Base64.getDecoder().decode(b64)) }
            }
            sc.obj("outputTranscription")?.str("text")?.takeIf { it.isNotEmpty() }?.let { out += LiveEvent.OutputText(it) }
            if (sc["interrupted"]?.flag() == true) out += LiveEvent.Interrupted
            if (sc["turnComplete"]?.flag() == true) out += LiveEvent.TurnComplete
        }
        root.obj("toolCall")?.get("functionCalls")?.let { it as? JsonArray }?.let { calls ->
            out += LiveEvent.ToolCall(calls.map { c -> val o = c.jsonObject; LiveCall(o.str("id").orEmpty(), o.str("name").orEmpty(), o.obj("args") ?: JsonObject(emptyMap())) })
        }
        root.obj("toolCallCancellation")?.get("ids")?.let { it as? JsonArray }?.let { ids -> out += LiveEvent.ToolCancel(ids.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }) }
        root.obj("goAway")?.let { out += LiveEvent.GoAway(durationMs(it.str("timeLeft"))) }
        root.obj("sessionResumptionUpdate")?.let { u ->
            val handle = u.str("newHandle").orEmpty()
            if (u["resumable"]?.flag() != false && handle.isNotEmpty()) out += LiveEvent.Resumable(handle)
        }
        return Parsed(out, setupComplete = "setupComplete" in root)
    }

    /** "50s" or "1.5s" to milliseconds; 0 when absent or unparseable. */
    fun durationMs(d: String?): Long = d?.removeSuffix("s")?.toDoubleOrNull()?.let { (it * 1000).toLong() } ?: 0L

    private fun JsonObject.obj(k: String): JsonObject? = this[k] as? JsonObject
    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonElement.flag(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull
}
