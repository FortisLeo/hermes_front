package com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Port: one persistent, lock-screen-visible notification slot (replaced on every show). */
interface LockScreenNotifier {
    fun show(title: String, text: String): Int
    fun clear()
}

/** Answers on the lock screen (#59), complementing lock_display (face above the keyguard): a PUBLIC-visibility notification shows the text without unlocking. Keep it short; it is visible to anyone. */
fun lockScreenAnswerCapability(notifier: LockScreenNotifier) = Capability("lock_screen_answer",
    "Show a short answer on the lock screen as a notification visible without unlocking: action=show (title, text) | clear. Never put private data in it.",
    """{"type":"object","properties":{"action":{"type":"string","enum":["show","clear"]},"title":{"type":"string"},"text":{"type":"string"}},"required":["action"]}""") { a: JsonObject ->
    when (a["action"]?.jsonPrimitive?.contentOrNull) {
        "show" -> {
            val title = a["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifEmpty { "Deskbot" }.take(60)
            val text = a["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (text.isEmpty()) return@Capability ActionResult.failed("text required")
            val id = notifier.show(title, text.take(400))
            ActionResult.completed("Showing on the lock screen", buildJsonObject { put("id", id) })
        }
        "clear" -> { notifier.clear(); ActionResult.completed("Cleared") }
        else -> ActionResult.failed("action must be show or clear")
    }
}
