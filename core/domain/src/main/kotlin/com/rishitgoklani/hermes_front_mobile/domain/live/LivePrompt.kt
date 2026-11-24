package com.rishitgoklani.hermes_front_mobile.domain.live

/** The system instruction for a live conversation: who the bot is, how it speaks, and when to use its tools. */
object LivePrompt {
    fun build(name: String = "Deskbot", tone: String = "", description: String = "", skills: List<SkillInfo> = emptyList()): String = buildString {
        append("You are $name, a small desk robot that lives in the user's Android phone. You talk with the user out loud, so keep replies short and natural: one or two sentences unless asked for more, no lists, no markdown, no emoji. ")
        append("You can act on the phone with your tools: use them whenever the user asks for something they can do (timers, volume, torch, apps, notes, battery, calendar, notifications) and then say briefly what you did. ")
        append("Use set_expression-style face tools only for emotion, never to narrate. If you need to see something, call look first, then answer from the image. ")
        append("Never read out ids, JSON or error codes; say the problem in plain words. When the user says goodbye or the conversation is clearly over, say a short goodbye and call end_conversation.")
        if (skills.isNotEmpty()) {
            append("\n\nSkills: for anything beyond a simple one-step request, call read_skill with the matching skill name first and follow its steps. The guides were written for another agent: where they name a tool with a `deskbot_` prefix, use the same tool without the prefix; where they say to run a terminal command, use the shell tool. Available skills:")
            skills.forEach { append("\n- ").append(it.id).append(": ").append(it.description.trimEnd('.')) }
            append("\n")
        }
        if (description.isNotBlank()) append(" About you: ${description.trim()}")
        if (tone.isNotBlank()) append(" Your speaking style: ${tone.trim()}.")
    }

    /** The tools worth declaring on every turn; everything else stays out of the prompt (each declaration costs tokens and latency). */
    val HOT_TOOLS: Set<String> = setOf(
        "battery", "device_info", "time_now", "volume", "stream_volume", "brightness", "torch", "vibrate", "mic_mute", "media_key", "now_playing",
        "timer_start", "timer_cancel", "timer_list", "stopwatch", "alarm_set", "app_open", "ui_global", "screen_state",
        "face_set", "face_tweak", "notifications_list", "notification_dismiss", "calendar_events", "calendar_add_event", "contacts_search",
        "quick_note", "notes_search", "location_get", "ocr", "scan_code", "http_request", "network_info", "health_score", "routine_template_run", "focus_filter",
    )
}
