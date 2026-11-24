package com.rishitgoklani.hermes_front_mobile.data.hermes

import com.rishitgoklani.hermes_front_mobile.domain.HermesConfig
import com.rishitgoklani.hermes_front_mobile.domain.HermesGateway
import com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiLog
import com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiSource
import com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiStatus
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
    /** Where requests and streamed replies are recorded for the Debug screen. */
    private val log: ApiLog? = null,
) : HermesGateway {

    override fun send(text: String, imageJpegBase64: String?): Flow<String> = flow {
        val cfg = config()
        val body = buildBody(text, imageJpegBase64, cfg)
        val entry = log?.begin(ApiSource.Hermes, "POST /v1/chat/completions", body.toString())
        var closed = false
        fun close(status: ApiStatus, response: String? = null) { if (!closed) { closed = true; entry?.let { log?.finish(it, status, response) } } }
        val request = Request.Builder()
            .url(cfg.baseUrl.trimEnd('/') + "/v1/chat/completions")
            .header("Authorization", "Bearer ${cfg.apiKey}")
            .header("X-Hermes-Session-Id", cfg.sessionId)
            .post(body.toString().toRequestBody(JSON))
            .build()
        try {
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Hermes HTTP ${response.code}")
            val source = response.body!!.source()
            while (true) {
                val line = source.readUtf8Line() ?: break
                when (val event = SseParser.parseLine(line)) {
                    is SseParser.Event.Delta -> { entry?.let { log?.append(it, event.text) }; emit(event.text) }
                    SseParser.Event.Done -> return@use
                    null -> Unit
                }
            }
        }
        close(ApiStatus.Ok)
        } catch (e: kotlinx.coroutines.CancellationException) { close(ApiStatus.Error, "cancelled"); throw e }
        catch (e: Exception) { close(ApiStatus.Error, e.message ?: e::class.simpleName.orEmpty()); throw e }
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
