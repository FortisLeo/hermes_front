package com.rishitgoklani.hermes_front_mobile.domain.messaging

/** Pure rules for outgoing SMS so the Android adapter stays thin and the safety gate is testable. */
sealed interface SmsCheck {
    data class Ok(val number: String, val text: String) : SmsCheck
    data class Refused(val reason: String) : SmsCheck
}

object SmsRules {
    const val MAX_CHARS = 640

    /** Sending costs money and cannot be undone, so the agent must pass `confirm=true` after the user agreed. */
    fun check(number: String?, text: String?, confirm: Boolean): SmsCheck {
        val n = number.orEmpty().filter { it.isDigit() || it == '+' }
        if (n.count { it.isDigit() } < 3) return SmsCheck.Refused("A valid phone number is required")
        if (n.indexOf('+') > 0 || n.count { it == '+' } > 1) return SmsCheck.Refused("A valid phone number is required")
        val t = text.orEmpty().trim()
        if (t.isEmpty()) return SmsCheck.Refused("Message text is empty")
        if (t.length > MAX_CHARS) return SmsCheck.Refused("Message too long (max $MAX_CHARS characters)")
        if (!confirm) return SmsCheck.Refused("Ask the user first, then call again with confirm=true")
        return SmsCheck.Ok(n, t)
    }
}
