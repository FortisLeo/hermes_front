package com.rishitgoklani.hermes_front_mobile.domain.behavior

import com.rishitgoklani.hermes_front_mobile.domain.HermesGateway
import com.rishitgoklani.hermes_front_mobile.domain.SentenceChunker
import com.rishitgoklani.hermes_front_mobile.domain.SpeechSynthesizer
import com.rishitgoklani.hermes_front_mobile.domain.face.TagFilter
import kotlinx.coroutines.flow.toList

/** "Send to Deskbot" from any app (#204): forwards shared text/image to Hermes and speaks a short reply. */
class HandleShare(private val gateway: HermesGateway, private val tts: SpeechSynthesizer) {
    suspend operator fun invoke(text: String?, imageBase64: String?, subject: String? = null): String {
        val body = text?.trim().orEmpty().take(MAX_TEXT)
        if (body.isEmpty() && imageBase64 == null) return "nothing to share"
        val prompt = buildString {
            append("The user shared ")
            append(if (imageBase64 != null && body.isEmpty()) "an image" else if (imageBase64 != null) "an image with text" else "this text")
            subject?.takeIf { it.isNotBlank() }?.let { append(" (subject: ${it.take(120)})") }
            append(". Briefly say what it is and offer one useful action; remember it if it looks worth keeping.")
            if (body.isNotEmpty()) append("\n\n").append(body)
        }
        val reply = gateway.send(prompt, imageBase64).toList().joinToString("")
        val tags = TagFilter(); val spoken = (tags.feed(reply) + tags.flush()).filterIsInstance<TagFilter.Segment.Text>().joinToString("") { it.text }
        val chunker = SentenceChunker(); (chunker.feed(spoken) + listOfNotNull(chunker.flush())).take(2).forEach { tts.enqueue(it) }
        return spoken.trim().ifEmpty { "shared" }
    }
    private companion object { const val MAX_TEXT = 8000 }
}
