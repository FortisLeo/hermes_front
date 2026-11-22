package com.rishitgoklani.hermes_front_mobile.data.hermes

import com.rishitgoklani.hermes_front_mobile.domain.HermesConfig
import com.rishitgoklani.hermes_front_mobile.domain.HermesGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class HermesClient(
    private val config: () -> HermesConfig,
    /** Extra context sent as a system message with every request (e.g. the local date and time). Empty = none. */
    private val context: () -> String = { "" },
    private val http: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // streaming: no read timeout
        .build(),
) : HermesGateway {

    override fun send(text: String, imageJpegBase64: String?): Flow<String> = flow {
        val cfg = config()
        val request = Request.Builder()
            .url(cfg.baseUrl.trimEnd('/') + "/v1/chat/completions")
            .header("Authorization", "Bearer ${cfg.apiKey}")
            .header("X-Hermes-Session-Id", cfg.sessionId)
            .post(buildBody(text, imageJpegBase64, cfg).toString().toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Hermes HTTP ${response.code}")
            val source = response.body!!.source()
            while (true) {
                val line = source.readUtf8Line() ?: break
                when (val event = SseParser.parseLine(line)) {
                    is SseParser.Event.Delta -> emit(event.text)
                    SseParser.Event.Done -> return@use
                    null -> Unit
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    internal fun buildBody(text: String, imageB64: String?, cfg: HermesConfig): JsonObject {
        val content = if (imageB64 == null) JsonPrimitive(text) else JsonArray(listOf(
            JsonObject(mapOf("type" to JsonPrimitive("text"), "text" to JsonPrimitive(text))),
            JsonObject(mapOf(
                "type" to JsonPrimitive("image_url"),
                "image_url" to JsonObject(mapOf("url" to JsonPrimitive("data:image/jpeg;base64,$imageB64"))),
            )),
        ))
        return JsonObject(mapOf(
            "model" to JsonPrimitive("hermes-agent"),
            "stream" to JsonPrimitive(true),
            "user" to JsonPrimitive(cfg.sessionId),
            "messages" to JsonArray(listOfNotNull(
                context().takeIf { it.isNotBlank() }?.let { JsonObject(mapOf("role" to JsonPrimitive("system"), "content" to JsonPrimitive(it))) },
                JsonObject(mapOf("role" to JsonPrimitive("user"), "content" to content)),
            )),
        ))
    }

    private companion object { val JSON = "application/json".toMediaType() }
}
