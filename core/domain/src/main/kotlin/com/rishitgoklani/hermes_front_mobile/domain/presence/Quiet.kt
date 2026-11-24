package com.rishitgoklani.hermes_front_mobile.domain.presence

/** What should keep the bot quiet right now (#333). */
data class QuietContext(val inCall: Boolean = false, val dnd: Boolean = false, val sleeping: Boolean = false, val focus: Boolean = false) {
    val quiet: Boolean get() = inCall || dnd || sleeping || focus
    fun reasons(): List<String> = buildList { if (inCall) add("call"); if (dnd) add("dnd"); if (sleeping) add("sleep"); if (focus) add("focus") }
}

/** Port: reads the live quiet context (telephony/audio mode, Do Not Disturb, sleep schedule). */
fun interface QuietContextPort { suspend fun current(): QuietContext }

/** Pure rule: calls silence everything; DND/sleep/focus only let Important speech through. */
object QuietPolicy {
    fun allows(urgency: Urgency, ctx: QuietContext): Boolean = when {
        ctx.inCall -> false
        ctx.quiet -> urgency == Urgency.Important
        else -> true
    }
}
