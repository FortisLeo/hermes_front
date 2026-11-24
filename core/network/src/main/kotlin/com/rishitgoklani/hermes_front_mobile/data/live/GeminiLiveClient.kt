package com.rishitgoklani.hermes_front_mobile.data.live

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
import java.util.concurrent.atomic.AtomicInteger

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
) : LiveSession {
    private val _events = MutableSharedFlow<LiveEvent>(extraBufferCapacity = 2048)
    override val events: Flow<LiveEvent> = _events

    @Volatile private var socket: WebSocket? = null
    private val generation = AtomicInteger(0)

    override suspend fun open(config: LiveConfig) {
        close()
        val key = apiKey().trim()
        if (key.isEmpty()) throw IOException("No Gemini API key")
        val gen = generation.incrementAndGet()
        val ready = CompletableDeferred<Unit>()
        val request = Request.Builder().url("$endpoint?key=$key").build()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.send(GeminiLiveProtocol.setup(config)) }
            override fun onMessage(webSocket: WebSocket, text: String) = receive(gen, text, ready)
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = receive(gen, bytes.utf8(), ready)
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                finish(gen, ready, code, reason, error = code != 1000 && code != 1001)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = finish(gen, ready, code, reason, error = code != 1000 && code != 1001)
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (gen != generation.get()) return
                val why = redact("${response?.code?.let { "HTTP $it " } ?: ""}${t.message ?: t::class.simpleName}", key)
                ready.completeExceptionally(IOException(why))
                _events.tryEmit(LiveEvent.Closed(why, error = true))
            }
        }
        socket = http.newWebSocket(request, listener)
        try { withTimeout(setupTimeoutMs) { ready.await() } } catch (e: Exception) { close(); throw if (e is kotlinx.coroutines.TimeoutCancellationException) IOException("Gemini Live did not answer in time") else e }
    }

    private fun receive(gen: Int, text: String, ready: CompletableDeferred<Unit>) {
        if (gen != generation.get()) return
        val parsed = GeminiLiveProtocol.parse(text)
        if (parsed.setupComplete) ready.complete(Unit)
        parsed.events.forEach { _events.tryEmit(it) }
    }

    private fun finish(gen: Int, ready: CompletableDeferred<Unit>, code: Int, reason: String, error: Boolean) {
        if (gen != generation.get()) return
        val why = reason.ifBlank { "closed ($code)" }
        ready.completeExceptionally(IOException(why))
        _events.tryEmit(LiveEvent.Closed(why, error))
    }

    private fun send(text: String) { socket?.let { if (it.queueSize() < maxQueuedBytes) it.send(text) } }
    override fun sendAudio(pcm: ByteArray) = send(GeminiLiveProtocol.audio(pcm))
    override fun sendFrame(jpeg: ByteArray) = send(GeminiLiveProtocol.frame(jpeg))
    override fun sendText(text: String) = send(GeminiLiveProtocol.text(text))
    override fun audioStreamEnd() = send(GeminiLiveProtocol.audioStreamEnd())
    override fun sendToolResponses(responses: List<LiveResult>) { socket?.send(GeminiLiveProtocol.toolResponses(responses)) }

    override fun close() {
        generation.incrementAndGet()          // late callbacks from the old socket are ignored
        socket?.close(1000, "bye"); socket = null
    }

    companion object {
        const val DEFAULT_ENDPOINT = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        fun redact(text: String, key: String): String = if (key.isEmpty()) text else text.replace(key, "***")
    }
}
