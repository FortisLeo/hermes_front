package com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities

import com.rishitgoklani.hermes_front_mobile.domain.SpeechSynthesizer
import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Speak text aloud through the bot's voice. */
fun ttsSayCapability(speaker: SpeechSynthesizer) = Capability(
    name = "tts_say",
    description = "Speak the given text aloud on the phone using the bot's voice.",
    inputSchemaJson = """{"type":"object","properties":{"text":{"type":"string","description":"What to say"}},"required":["text"]}""",
) { args ->
    val text = args["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
    if (text.isEmpty()) ActionResult.failed("text is required")
    else { speaker.enqueue(text); ActionResult.completed("Speaking") }
}
