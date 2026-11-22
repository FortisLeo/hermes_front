package com.rishitgoklani.hermes_front_mobile.data.http

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import com.rishitgoklani.hermes_front_mobile.domain.system.UrlGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val MAX_BODY = 64_000

fun httpRequestCapability(client: OkHttpClient = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()) = Capability(
    "http_request", "HTTP(S) request (GET/POST/PUT/DELETE/PATCH/HEAD) with headers and body; retries on connection errors. Returns status and first 64 KB of the body.",
    """{"type":"object","properties":{"url":{"type":"string"},"method":{"type":"string"},"headers":{"type":"object"},"body":{"type":"string"},"retries":{"type":"integer"}},"required":["url"]}""",
) { a ->
    val url = a["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
    UrlGuard.check(url)?.let { return@Capability ActionResult(ActionResult.DENIED, it) }
    val method = (a["method"]?.jsonPrimitive?.contentOrNull ?: "GET").uppercase()
    val body = a["body"]?.jsonPrimitive?.contentOrNull
    val headers = (a["headers"] as? JsonObject)?.mapValues { it.value.jsonPrimitive.content }.orEmpty()
    val retries = (a["retries"]?.jsonPrimitive?.intOrNull ?: 0).coerceIn(0, 3)
    val reqBody = if (method in setOf("GET", "HEAD")) null else (body ?: "").toRequestBody((headers["Content-Type"] ?: "text/plain").toMediaTypeOrNull())
    val request = Request.Builder().url(url).method(method, reqBody).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
    var last: Exception? = null
    repeat(retries + 1) { attempt ->
        try {
            return@Capability withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { r ->
                    val text = r.body?.source()?.let { s -> s.request(MAX_BODY.toLong() + 1); s.buffer.readUtf8(minOf(s.buffer.size, MAX_BODY.toLong())) }.orEmpty()
                    ActionResult(if (r.isSuccessful) ActionResult.COMPLETED else ActionResult.FAILED, "HTTP ${r.code}\n$text", buildJsonObject {
                        put("status_code", r.code); put("body", text); put("attempts", attempt + 1)
                    })
                }
            }
        } catch (e: java.io.IOException) { last = e; if (attempt < retries) kotlinx.coroutines.delay(300L * (attempt + 1)) }
    }
    ActionResult.failed("Request failed: ${last?.message}")
}
