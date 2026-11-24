package com.rishitgoklani.hermes_front_mobile.data.live

import com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiLog
import com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiSource
import com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiStatus
import com.rishitgoklani.hermes_front_mobile.domain.live.LiveConfig
import com.rishitgoklani.hermes_front_mobile.domain.live.LiveEvent
import com.rishitgoklani.hermes_front_mobile.domain.live.LiveResult
import com.rishitgoklani.hermes_front_mobile.domain.live.LiveSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * [LiveSession] over the Gemini Live WebSocket. The key is read per connection and only ever sits in the request URL; it is never logged
 * and error text from the server is passed through [redact] before it leaves this class.
 */
class GeminiLiveClient(
    private val http: OkHttpClient = OkHttpClient.Builder().pingInterval(20, java.util.concurrent.TimeUnit.SECONDS).readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS).build(),
    private val apiKey: suspend () -> String,
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val setupTimeoutMs: Long = 12_000,
    /** Above this many queued bytes microphone audio is dropped rather than building up latency. */
    private val maxQueuedBytes: Long = 512 * 1024,
    /** Records the session open, tool calls and their results, audio volume and errors for the Debug screen (never the key). */
    private val log: ApiLog? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) : LiveSession {
    private val _events = MutableSharedFlow<LiveEvent>(extraBufferCapacity = 2048)
    override val events: Flow<LiveEvent> = _events

    @Volatile private var socket: WebSocket? = null
    private val generation = AtomicInteger(0)
    private val toolEntries = ConcurrentHashMap<String, Long>()
    private val upChunks = AtomicLong(); private val upBytes = AtomicLong(); private val downChunks = AtomicLong(); private val downBytes = AtomicLong()
    @Volatile private var audioEntry: Long? = null
    @Volatile private var lastFlush = 0L

    override suspend fun open(config: LiveConfig) {
        close()
        val key = apiKey().trim()
        if (key.isEmpty()) throw IOException("No Gemini API key")
        val gen = generation.incrementAndGet()
        val ready = CompletableDeferred<Unit>()
        resetAudioStats()
        val openEntry = log?.begin(ApiSource.GeminiLive, "open ${config.model}", GeminiLiveProtocol.setup(config))
        val request = Request.Builder().url("$endpoint?key=$key").build()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.send(GeminiLiveProtocol.setup(config)) }
            override fun onMessage(webSocket: WebSocket, text: String) = receive(gen, text, ready, openEntry)
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = receive(gen, bytes.utf8(), ready, openEntry)
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                finish(gen, ready, code, reason, error = code != 1000 && code != 1001, openEntry)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = finish(gen, ready, code, reason, error = code != 1000 && code != 1001, openEntry)
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (gen != generation.get()) return
                val why = redact("${response?.code?.let { "HTTP $it " } ?: ""}${t.message ?: t::class.simpleName}", key)
                if (!ready.isCompleted) openEntry?.let { log?.finish(it, ApiStatus.Error, why) }
                ready.completeExceptionally(IOException(why))
                log?.record(ApiSource.GeminiLive, "error", "", why, ApiStatus.Error)
                _events.tryEmit(LiveEvent.Closed(why, error = true))
            }
        }
        socket = http.newWebSocket(request, listener)
        try { withTimeout(setupTimeoutMs) { ready.await() } } catch (e: Exception) { close(); throw if (e is kotlinx.coroutines.TimeoutCancellationException) IOException("Gemini Live did not answer in time") else e }
    }

    private fun receive(gen: Int, text: String, ready: CompletableDeferred<Unit>, openEntry: Long?) {
        if (gen != generation.get()) return
        val parsed = GeminiLiveProtocol.parse(text)
        if (parsed.setupComplete) { if (!ready.isCompleted) openEntry?.let { log?.finish(it, ApiStatus.Ok, "setupComplete") }; ready.complete(Unit) }
        parsed.events.forEach { e ->
            when (e) {
                is LiveEvent.AudioOut -> { downChunks.incrementAndGet(); downBytes.addAndGet(e.pcm.size.toLong()); flushAudioStats() }
                is LiveEvent.ToolCall -> e.calls.forEach { c -> log?.begin(ApiSource.GeminiLive, "toolCall ${c.name}", c.args.toString())?.let { toolEntries[c.id] = it } }
                is LiveEvent.ToolCancel -> e.ids.forEach { id -> toolEntries.remove(id)?.let { log?.finish(it, ApiStatus.Error, "cancelled by the model (user spoke)") } }
                is LiveEvent.GoAway -> log?.record(ApiSource.GeminiLive, "goAway", "", "closing in ${e.timeLeftMs} ms", ApiStatus.Ok)
                else -> Unit
            }
            _events.tryEmit(e)
        }
    }

    private fun finish(gen: Int, ready: CompletableDeferred<Unit>, code: Int, reason: String, error: Boolean, openEntry: Long?) {
        if (gen != generation.get()) return
        val why = reason.ifBlank { "closed ($code)" }
        if (!ready.isCompleted) openEntry?.let { log?.finish(it, ApiStatus.Error, why) }
        ready.completeExceptionally(IOException(why))
        log?.record(ApiSource.GeminiLive, if (error) "closed with error" else "closed", "", "$why (code $code)", if (error) ApiStatus.Error else ApiStatus.Ok)
        _events.tryEmit(LiveEvent.Closed(why, error))
    }

    private fun send(text: String) { socket?.let { if (it.queueSize() < maxQueuedBytes) it.send(text) } }
    override fun sendAudio(pcm: ByteArray) { upChunks.incrementAndGet(); upBytes.addAndGet(pcm.size.toLong()); flushAudioStats(); send(GeminiLiveProtocol.audio(pcm)) }
    override fun sendFrame(jpeg: ByteArray) { log?.record(ApiSource.GeminiLive, "camera frame", "image/jpeg, ${jpeg.size} bytes", "sent", ApiStatus.Ok); send(GeminiLiveProtocol.frame(jpeg)) }
    override fun sendText(text: String) { log?.record(ApiSource.GeminiLive, "text", text, "sent", ApiStatus.Ok); send(GeminiLiveProtocol.text(text)) }
    override fun audioStreamEnd() = send(GeminiLiveProtocol.audioStreamEnd())
    override fun sendToolResponses(responses: List<LiveResult>) {
        responses.forEach { r ->
            toolEntries.remove(r.id)?.let { id ->
                val status = (r.response["status"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                log?.finish(id, if (status == "completed" || status == "accepted") ApiStatus.Ok else ApiStatus.Error, r.response.toString())
            }
        }
        socket?.send(GeminiLiveProtocol.toolResponses(responses))
    }

    private fun resetAudioStats() { upChunks.set(0); upBytes.set(0); downChunks.set(0); downBytes.set(0); audioEntry = null; lastFlush = 0 }

    /** Audio is far too frequent to log per chunk: one entry per connection, with running totals refreshed about once a second. */
    private fun flushAudioStats() {
        val l = log ?: return
        val now = clock()
        if (now - lastFlush < 1_000 && audioEntry != null) return
        lastFlush = now
        val up = "up: ${upChunks.get()} chunks, ${upBytes.get() / 1024} KB (16 kHz mic)"; val down = "down: ${downChunks.get()} chunks, ${downBytes.get() / 1024} KB (24 kHz speech)"
        val id = audioEntry ?: l.begin(ApiSource.GeminiLive, "audio stream", up).also { audioEntry = it }
        l.set(id, request = up, response = down)
    }

    override fun close() {
        generation.incrementAndGet()          // late callbacks from the old socket are ignored
        audioEntry?.let { log?.finish(it, ApiStatus.Ok) }; audioEntry = null
        socket?.close(1000, "bye"); socket = null
    }

    companion object {
        const val DEFAULT_ENDPOINT = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        fun redact(text: String, key: String): String = if (key.isEmpty()) text else text.replace(key, "***")
    }
}
