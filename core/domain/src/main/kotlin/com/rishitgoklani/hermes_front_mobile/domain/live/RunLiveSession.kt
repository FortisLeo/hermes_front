package com.rishitgoklani.hermes_front_mobile.domain.live

import com.rishitgoklani.hermes_front_mobile.domain.BotState
import com.rishitgoklani.hermes_front_mobile.domain.CameraFacing
import com.rishitgoklani.hermes_front_mobile.domain.TurnTiming
import com.rishitgoklani.hermes_front_mobile.domain.VoiceEvent
import com.rishitgoklani.hermes_front_mobile.domain.bridge.shell.AuditLog
import com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiLog
import com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiSource
import com.rishitgoklani.hermes_front_mobile.domain.diagnostics.ApiStatus
import com.rishitgoklani.hermes_front_mobile.domain.face.EmotionHints
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Use case: one spoken conversation with the model over a single streaming session.
 * Microphone audio goes up, speech comes back and plays as it arrives, tool calls run on the phone, and the user can interrupt at any time.
 * Ends on silence ([LiveSettings.idleSeconds], unless started with `keepAlive`), when the model calls [END_TOOL], when the connection cannot be re-established, or when cancelled.
 * A dropped connection or a microphone that stops is retried, so a hiccup does not end the conversation. Every end records why (audit, API log) and tells the user ([notice]).
 * Emits the same [VoiceEvent]s as the Hermes turn so the face and chat UI need no changes.
 */
class RunLiveSession(
    private val session: LiveSession,
    private val mic: LiveAudioIn,
    private val speaker: LiveAudioOut,
    private val tools: LiveToolBridge,
    private val settings: suspend () -> LiveSettings,
    /** The system instruction; receives whether the full tool and skill set is on, so the skill index is only mentioned when it can be used. */
    private val prompt: (Boolean) -> String,
    /** Captures one JPEG from the camera for the `look` tool; null disables vision. */
    private val frame: (suspend (CameraFacing) -> ByteArray)? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val audit: AuditLog? = null,
    private val maxReconnects: Int = 4,
    private val halfDuplexTailMs: Long = 350,
    /** One entry per spoken exchange (what was heard, what was said) for the Debug screen. */
    private val log: ApiLog? = null,
) : LiveActivity {
    private val _active = kotlinx.coroutines.flow.MutableStateFlow(false)
    override val active: kotlinx.coroutines.flow.StateFlow<Boolean> = _active
    private val _micLevel = kotlinx.coroutines.flow.MutableStateFlow(0f)
    override val micLevel: kotlinx.coroutines.flow.StateFlow<Float> = _micLevel
    private val _notice = kotlinx.coroutines.flow.MutableStateFlow<LiveNotice?>(null)
    override val notice: kotlinx.coroutines.flow.StateFlow<LiveNotice?> = _notice
    private var noticeId = 0L
    private fun note(text: String) { _notice.value = LiveNotice(++noticeId, text) }

    /** [keepAlive]: a conversation the user started on purpose (tap) stays open through silence until it is stopped. */
    operator fun invoke(keepAlive: Boolean = false): Flow<VoiceEvent> = channelFlow {
        val s = settings()
        require(s.ready) { "Gemini Live is not set up: add an API key in Settings" }
        val declared = tools.declarations(s.allTools) + extraDecls()
        fun config(handle: String?) = LiveConfig(s.model, s.voice, prompt(s.allTools), declared, resumeHandle = handle, allowBargeIn = !s.halfDuplex)
        val state = Turn()
        val finished = CompletableDeferred<Unit>()
        var failure: Throwable? = null
        send(VoiceEvent.State(BotState.Thinking))
        coroutineScope {
            val pump = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                session.events.collect { e ->
                    try { handle(e, state, finished, ::config) } catch (c: CancellationException) { throw c } catch (t: Throwable) { failure = t; finished.complete(Unit) }
                }
            }
            try {
                session.open(config(null))
                state.openedAt = clock()
                _active.value = true; _notice.value = null
                send(VoiceEvent.State(BotState.Listening))
                state.activity = clock()
                val micJob = launch {
                    var silentUntil = 0L; var failures = 0
                    while (true) {
                        try {
                            mic.chunks().collect { chunk ->
                                failures = 0
                                if (s.halfDuplex) {
                                    if (speaker.playing.value) { silentUntil = clock() + halfDuplexTailMs; _micLevel.value = 0f; return@collect }
                                    if (clock() < silentUntil) { _micLevel.value = 0f; return@collect }
                                }
                                _micLevel.value = PcmLevel.mouthOpenness(PcmLevel.rms(chunk), MIC_GAIN)
                                session.sendAudio(chunk)
                            }
                            throw java.io.IOException("microphone stopped")
                        } catch (c: CancellationException) { throw c } catch (e: Exception) {
                            // The system or another app took the microphone for a moment: wait and open it again instead of ending the conversation.
                            _micLevel.value = 0f
                            if (++failures > MAX_MIC_RETRIES) throw java.io.IOException("microphone unavailable: ${e.message}")
                            note("Microphone interrupted, reconnecting…"); delay(400L * failures)
                        }
                    }
                }
                val idleMs = s.idleSeconds * 1000L
                val watchdog = launch {
                    while (true) {
                        delay(500)
                        val quiet = !speaker.playing.value && state.pendingCalls.isEmpty()
                        if (!keepAlive && quiet && clock() - state.activity > idleMs) {
                            state.endReason = "idle: no speech for ${s.idleSeconds} s"
                            note("Stopped listening after ${s.idleSeconds} s of silence. Say the wake word or tap the wave.")
                            finished.complete(Unit); return@launch
                        }
                    }
                }
                finished.await()
                micJob.cancel(); watchdog.cancel()
            } finally {
                // Cleanup must run even when this coroutine was cancelled (stop tap, kill switch): otherwise the socket stays open and the wave stays "active".
                withContext(NonCancellable) {
                    pump.cancel()
                    withTimeoutOrNull(2_000) { while (state.ending && speaker.playing.value) delay(100) }
                    state.entry?.let { log?.finish(it, ApiStatus.Ok) }; state.entry = null
                    _active.value = false; _micLevel.value = 0f
                    speaker.flush(); session.close()
                    val failed = failure
                    val reason = state.endReason ?: failed?.let { "error: ${it.message}" } ?: "cancelled"
                    if (failed != null) note("Stopped: ${failed.message ?: "connection problem"}")
                    log?.record(ApiSource.GeminiLive, "session ended", "", reason, if (failed != null) ApiStatus.Error else ApiStatus.Ok)
                    audit?.record("live_session", """{"turns":${state.turns},"reconnects":${state.reconnects},"end":"${reason.replace('"', '\'')}"}""", if (failed != null) "failed" else "ok")
                }
            }
        }
        failure?.let { throw it }
        send(VoiceEvent.State(BotState.Idle))
    }

    private fun extraDecls(): List<LiveToolDecl> = buildList {
        add(LiveToolDecl(END_TOOL, "Call when the user says goodbye or the conversation is clearly over. Say a short goodbye first."))
        if (frame != null) add(LiveToolDecl(LOOK_TOOL, "Look through the phone camera so you can see what the user shows you. Call it before answering questions about what is in front of you.",
            """{"type":"object","properties":{"facing":{"type":"string","enum":["front","back"],"description":"front faces the user, back faces the room"}}}"""))
    }

    /** Mutable per-conversation bookkeeping, touched only from the event pump and the calls it launches. */
    private class Turn {
        val input = StringBuilder()
        var heard = false
        var speaking = false
        var lastInputAt = -1L
        var firstOutputMs = -1L
        var looked = false
        var activity = 0L
        var ending = false
        var handle: String? = null
        var reconnects = 0
        var openedAt = 0L
        var endReason: String? = null
        var turns = 0
        var entry: Long? = null
        val pendingCalls = HashMap<String, Job>()
    }

    private suspend fun ProducerScope<VoiceEvent>.handle(e: LiveEvent, t: Turn, finished: CompletableDeferred<Unit>, config: (String?) -> LiveConfig) {
        suspend fun ensureHeard() {
            if (t.heard) return
            t.heard = true; t.turns++
            val text = t.input.toString().trim()
            t.entry = log?.begin(ApiSource.GeminiLive, "turn", text.ifEmpty { "(no transcript)" })
            if (text.isNotEmpty()) { send(VoiceEvent.Heard(text)); EmotionHints.forUtterance(text)?.let { send(VoiceEvent.Expression(it)) } }
            send(VoiceEvent.State(BotState.Thinking))
        }
        when (e) {
            is LiveEvent.InputText -> {
                if (t.heard) { t.input.clear(); t.heard = false; t.firstOutputMs = -1; t.looked = false }
                t.input.append(e.text); t.lastInputAt = clock(); t.activity = t.lastInputAt
                send(VoiceEvent.Partial(t.input.toString().trim())); send(VoiceEvent.State(BotState.Listening))
            }
            is LiveEvent.OutputText -> { ensureHeard(); t.activity = clock(); t.entry?.let { log?.append(it, e.text) }; send(VoiceEvent.ReplyDelta(e.text)) }
            is LiveEvent.AudioOut -> {
                ensureHeard(); t.activity = clock()
                if (!t.speaking) {
                    t.speaking = true
                    val since = if (t.lastInputAt >= 0) clock() - t.lastInputAt else 0L
                    send(VoiceEvent.Timing(TurnTiming(since, since, t.looked)))
                    audit?.record("voice_turn", """{"backend":"gemini_live","first_audio_ms":$since,"vision":${t.looked}}""", "ok")
                    send(VoiceEvent.State(BotState.Speaking))
                }
                speaker.play(e.pcm)
            }
            is LiveEvent.ToolCall -> { ensureHeard(); send(VoiceEvent.State(BotState.Working)); e.calls.forEach { runCall(it, t, finished) } }
            is LiveEvent.ToolCancel -> e.ids.forEach { t.pendingCalls.remove(it)?.cancel() }
            LiveEvent.Interrupted -> { t.entry?.let { log?.append(it, " [interrupted]"); log?.finish(it, ApiStatus.Ok) }; t.entry = null; speaker.flush(); t.speaking = false; t.heard = false; t.input.clear(); send(VoiceEvent.State(BotState.Listening)) }
            LiveEvent.TurnComplete -> {
                t.speaking = false; t.heard = false; t.input.clear(); t.activity = clock()
                t.entry?.let { log?.finish(it, ApiStatus.Ok) }; t.entry = null
                if (t.ending) { t.endReason = "goodbye"; finished.complete(Unit) } else send(VoiceEvent.State(BotState.Listening))
            }
            is LiveEvent.Resumable -> t.handle = e.handle
            is LiveEvent.GoAway -> { /* the close that follows triggers the reconnect */ }
            is LiveEvent.Closed -> {
                if (clock() - t.openedAt > STABLE_MS) t.reconnects = 0 // a long healthy stretch earns a fresh budget of retries
                when {
                    t.ending || finished.isCompleted -> { if (t.ending) t.endReason = "goodbye"; finished.complete(Unit) }
                    t.reconnects < maxReconnects -> {
                        // Resume with the handle when the server gave one; otherwise start a fresh session (the conversation so far is lost, but listening continues).
                        t.reconnects++
                        note(if (t.handle != null) "Connection dropped, reconnecting…" else "Connection dropped, starting a new session…")
                        delay(300L * t.reconnects)
                        session.open(config(t.handle)); t.openedAt = clock()
                    }
                    e.error -> throw java.io.IOException(e.reason)
                    else -> { t.endReason = "server closed: ${e.reason}"; finished.complete(Unit) }
                }
            }
        }
    }

    private suspend fun ProducerScope<VoiceEvent>.runCall(call: LiveCall, t: Turn, finished: CompletableDeferred<Unit>) {
        val job = launch {
            val result = when (call.name) {
                END_TOOL -> { t.ending = true; ok(call) }
                LOOK_TOOL -> look(call, t)
                else -> tools.execute(call)
            }
            t.pendingCalls.remove(call.id)
            t.activity = clock()
            session.sendToolResponses(listOf(result))
            if (!t.speaking) send(VoiceEvent.State(BotState.Listening))
        }
        t.pendingCalls[call.id] = job
    }

    private suspend fun look(call: LiveCall, t: Turn): LiveResult {
        val capture = frame ?: return fail(call, "No camera")
        val facing = if ((call.args["facing"] as? JsonPrimitive)?.content == "back") CameraFacing.Back else CameraFacing.Front
        return try {
            session.sendFrame(capture(facing)); t.looked = true
            ok(call, "Frame sent from the ${facing.name.lowercase()} camera. Describe what you see.")
        } catch (c: CancellationException) { throw c } catch (e: Exception) { fail(call, e.message ?: "camera failed") }
    }

    private fun ok(call: LiveCall, message: String = "ok") = LiveResult(call.id, call.name, buildJsonObject { put("status", "completed"); put("message", message) })
    private fun fail(call: LiveCall, message: String) = LiveResult(call.id, call.name, buildJsonObject { put("status", "failed"); put("message", message) })

    companion object {
        const val END_TOOL = "end_conversation"
        const val LOOK_TOOL = "look"
        /** Speech through the phone mic is quiet (RMS ~0.02..0.15), so scale it up to fill the wave. */
        const val MIC_GAIN = 7f
        const val MAX_MIC_RETRIES = 5
        /** A connection that stayed up this long counts as healthy, so earlier reconnects are forgiven. */
        const val STABLE_MS = 60_000L
    }
}
