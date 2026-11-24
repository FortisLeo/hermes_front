package com.rishitgoklani.hermes_front_mobile.domain.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ApiSource(val label: String) { Hermes("Hermes"), GeminiLive("Gemini Live"), Bridge("Bridge") }
enum class ApiStatus { Pending, Ok, Error }

/** One API exchange: what was sent and what came back. [response] grows while a streamed reply arrives. */
data class ApiLogEntry(
    val id: Long, val startedAt: Long, val source: ApiSource, val label: String,
    val request: String, val response: String = "", val status: ApiStatus = ApiStatus.Pending, val durationMs: Long? = null,
)

/** Read side, newest first. What the Debug screen shows. */
interface ApiLogReader {
    val entries: StateFlow<List<ApiLogEntry>>
    fun clear()
}

/** Write side. Every method redacts secrets and truncates, so callers pass raw text. */
interface ApiLog : ApiLogReader {
    fun begin(source: ApiSource, label: String, request: String): Long
    /** Adds streamed response text to an open entry. */
    fun append(id: Long, text: String)
    /** Closes an entry. A non-null [response] replaces the text so far. */
    fun finish(id: Long, status: ApiStatus, response: String? = null)
    /** Rewrites the request and/or response of an entry (used for running counters). Status is unchanged. */
    fun set(id: Long, request: String? = null, response: String? = null)
    /** One-shot entry for an exchange that is already complete. */
    fun record(source: ApiSource, label: String, request: String, response: String, status: ApiStatus, durationMs: Long? = null): Long
}

/** Removes anything that must never be shown or copied: keys, tokens, and bulky binary payloads. */
object ApiRedactor {
    private val queryKey = Regex("""([?&](?:key|access_token|token|api_key)=)[^&\s"']+""", RegexOption.IGNORE_CASE)
    private val bearer = Regex("""(Bearer\s+)[A-Za-z0-9._~+/=-]+""", RegexOption.IGNORE_CASE)
    private val jsonSecret = Regex(""""(token|api_key|apiKey|access_token|authorization|x-goog-api-key)"\s*:\s*"[^"]*"""", RegexOption.IGNORE_CASE)
    private val dataUri = Regex("""data:[a-z]+/[a-z0-9.+-]+;base64,[A-Za-z0-9+/=]+""", RegexOption.IGNORE_CASE)
    private val blob = Regex("""[A-Za-z0-9+/=_-]{200,}""")

    fun redact(text: String, secrets: List<String> = emptyList()): String {
        var s = text
        secrets.filter { it.length >= 4 }.forEach { s = s.replace(it, "***") }
        s = queryKey.replace(s, "$1***")
        s = bearer.replace(s, "$1***")
        s = jsonSecret.replace(s) { "\"${it.groupValues[1]}\":\"***\"" }
        s = dataUri.replace(s) { "<data uri ${it.value.length} chars>" }
        s = blob.replace(s) { "<blob ${it.value.length} chars>" }
        return s
    }

    fun truncate(text: String, max: Int): String = if (text.length <= max) text else text.take(max) + "… [+${text.length - max} chars]"
}

/**
 * Bounded in-memory log (oldest entries drop first). Kept in memory on purpose: it can hold prompts and replies, so it never touches disk
 * and is gone when the app process ends.
 */
class InMemoryApiLog(
    private val capacity: Int = 300,
    private val clock: () -> Long = System::currentTimeMillis,
    private val secrets: () -> List<String> = { emptyList() },
    private val maxField: Int = 4_000,
) : ApiLog {
    private val lock = Any()
    private var nextId = 1L
    private val state = MutableStateFlow<List<ApiLogEntry>>(emptyList())
    override val entries: StateFlow<List<ApiLogEntry>> = state

    private fun clean(text: String) = ApiRedactor.truncate(ApiRedactor.redact(text, secrets()), maxField)

    override fun begin(source: ApiSource, label: String, request: String): Long = synchronized(lock) {
        val id = nextId++
        state.value = (listOf(ApiLogEntry(id, clock(), source, label, clean(request))) + state.value).take(capacity)
        id
    }

    override fun append(id: Long, text: String) = update(id) { it.copy(response = ApiRedactor.truncate(ApiRedactor.redact(it.response + text, secrets()), maxField)) }

    override fun finish(id: Long, status: ApiStatus, response: String?) = update(id) {
        it.copy(status = status, durationMs = clock() - it.startedAt, response = response?.let(::clean) ?: it.response)
    }

    override fun record(source: ApiSource, label: String, request: String, response: String, status: ApiStatus, durationMs: Long?): Long {
        val id = begin(source, label, request)
        update(id) { it.copy(response = clean(response), status = status, durationMs = durationMs ?: (clock() - it.startedAt)) }
        return id
    }

    override fun set(id: Long, request: String?, response: String?) = update(id) { it.copy(request = request?.let(::clean) ?: it.request, response = response?.let(::clean) ?: it.response) }

    override fun clear() { synchronized(lock) { state.value = emptyList() } }

    private fun update(id: Long, f: (ApiLogEntry) -> ApiLogEntry) = synchronized(lock) {
        state.value = state.value.map { if (it.id == id) f(it) else it }
    }
}

/** Plain-text rendering for the Copy button. */
object ApiLogFormat {
    fun entry(e: ApiLogEntry, timeText: (Long) -> String = { it.toString() }): String = buildString {
        append("[${timeText(e.startedAt)}] ${e.source.label}  ${e.label}  ${e.status.name.lowercase()}")
        e.durationMs?.let { append("  ${it} ms") }
        append("\n> ").append(e.request.ifBlank { "(empty)" })
        append("\n< ").append(e.response.ifBlank { "(none)" })
    }
    fun all(entries: List<ApiLogEntry>, timeText: (Long) -> String = { it.toString() }): String = entries.joinToString("\n\n") { entry(it, timeText) }
}
