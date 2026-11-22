package com.rishitgoklani.hermes_front_mobile.domain

import com.rishitgoklani.hermes_front_mobile.domain.face.Expr
import com.rishitgoklani.hermes_front_mobile.domain.face.TagFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed interface VoiceEvent {
    data class State(val state: BotState) : VoiceEvent
    data class Partial(val text: String) : VoiceEvent
    data class Heard(val text: String) : VoiceEvent
    data class ReplyDelta(val text: String) : VoiceEvent
    /** Played just before the sentence it belongs to starts being spoken. */
    data class Expression(val expr: Expr) : VoiceEvent
    data class Timing(val timing: TurnTiming) : VoiceEvent
}

/** Latency of one turn: end of speech -> first token -> first spoken sentence queued. */
data class TurnTiming(val firstTokenMs: Long, val firstSentenceMs: Long, val visionAttached: Boolean)

/** Use case: listen once, send to Hermes (with a camera frame when the request needs sight), speak the streamed reply sentence by sentence. */
class RunVoiceTurn(
    private val stt: SpeechToText,
    private val gateway: HermesGateway,
    private val tts: SpeechSynthesizer,
    private val camera: CameraPort? = null,
    private val encode: (ByteArray) -> String = { java.util.Base64.getEncoder().encodeToString(it) },
    private val clock: () -> Long = System::currentTimeMillis,
    private val audit: com.rishitgoklani.hermes_front_mobile.domain.bridge.shell.AuditLog? = null,
) {
    operator fun invoke(): Flow<VoiceEvent> = flow {
        emit(VoiceEvent.State(BotState.Listening))
        var heard = ""
        stt.listen().collect {
            when (it) {
                is SttEvent.Partial -> emit(VoiceEvent.Partial(it.text))
                is SttEvent.Final -> heard = it.text.trim()
            }
        }
        if (heard.isEmpty()) { emit(VoiceEvent.State(BotState.Idle)); return@flow }
        emit(VoiceEvent.Heard(heard))
        com.rishitgoklani.hermes_front_mobile.domain.face.EmotionHints.forUtterance(heard)?.let { emit(VoiceEvent.Expression(it)) }
        emit(VoiceEvent.State(BotState.Thinking))
        val started = clock()
        var image: String? = null
        if (camera != null && com.rishitgoklani.hermes_front_mobile.domain.voice.VisionTrigger.needsVision(heard)) {
            image = try { encode(camera.capture(com.rishitgoklani.hermes_front_mobile.domain.voice.VisionTrigger.facing(heard), 140_000)) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { null }
        }
        var firstToken = -1L; var firstSentence = -1L
        val chunker = SentenceChunker()
        val tags = TagFilter()
        val pending = mutableListOf<Expr>()
        var speaking = false
        suspend fun speak(sentence: String) {
            if (!speaking) { speaking = true; firstSentence = clock() - started; emit(VoiceEvent.State(BotState.Speaking)) }
            pending.forEach { emit(VoiceEvent.Expression(it)) }; pending.clear()
            tts.enqueue(sentence)
        }
        suspend fun consume(segments: List<TagFilter.Segment>) = segments.forEach { seg ->
            when (seg) {
                is TagFilter.Segment.Tag -> pending += seg.expr
                is TagFilter.Segment.Text -> { emit(VoiceEvent.ReplyDelta(seg.text)); chunker.feed(seg.text).forEach { speak(it) } }
            }
        }
        try {
            gateway.send(heard, image).collect { delta -> if (firstToken < 0) firstToken = clock() - started; consume(tags.feed(delta)) }
            consume(tags.flush())
            chunker.flush()?.let { speak(it) }
            pending.forEach { emit(VoiceEvent.Expression(it)) }; pending.clear()
            val timing = TurnTiming(firstToken, firstSentence, image != null)
            audit?.record("voice_turn", """{"first_token_ms":${timing.firstTokenMs},"first_sentence_ms":${timing.firstSentenceMs},"vision":${timing.visionAttached}}""", "ok")
            emit(VoiceEvent.Timing(timing))
            tts.awaitDone()
            emit(VoiceEvent.State(BotState.Idle))
        } catch (e: kotlinx.coroutines.CancellationException) {
            tts.stop(); throw e
        }
    }
}
