package com.rishitgoklani.hermes_front_mobile.domain.a2ui

import com.rishitgoklani.hermes_front_mobile.domain.HermesGateway
import com.rishitgoklani.hermes_front_mobile.domain.SpeechSynthesizer
import com.rishitgoklani.hermes_front_mobile.domain.behavior.RunCapability
import com.rishitgoklani.hermes_front_mobile.domain.face.TagFilter
import com.rishitgoklani.hermes_front_mobile.domain.SentenceChunker
import kotlinx.coroutines.flow.toList

/** Routes a UI tap: direct tool call (logged by the registry/audit), dismiss, or a structured turn to Hermes (reply is spoken). */
class HandleUiAction(private val store: A2uiStore, private val run: RunCapability, private val gateway: HermesGateway, private val tts: SpeechSynthesizer) {
    suspend operator fun invoke(action: String): String {
        return when (val p = ParsedAction.parse(action)) {
            ParsedAction.Dismiss -> { store.clear(); "dismissed" }
            is ParsedAction.Tool -> run(p.name, p.args).let { store.onActionDone(); "${p.name}: ${it.status}" }
            is ParsedAction.ToHermes -> {
                val message = "[ui-action ${p.id} ${store.snapshotValues()}]"
                store.onActionDone()
                val reply = gateway.send(message).toList().joinToString("")
                val tags = TagFilter(); val spoken = (tags.feed(reply) + tags.flush()).filterIsInstance<TagFilter.Segment.Text>().joinToString("") { it.text }
                val chunker = SentenceChunker(); (chunker.feed(spoken) + listOfNotNull(chunker.flush())).take(3).forEach { tts.enqueue(it) }
                "sent to Hermes"
            }
        }
    }
}
