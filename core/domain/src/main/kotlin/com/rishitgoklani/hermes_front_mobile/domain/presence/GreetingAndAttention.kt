package com.rishitgoklani.hermes_front_mobile.domain.presence

import com.rishitgoklani.hermes_front_mobile.domain.BotState
import com.rishitgoklani.hermes_front_mobile.domain.face.ArrivalGreeter
import com.rishitgoklani.hermes_front_mobile.domain.face.Expr
import com.rishitgoklani.hermes_front_mobile.domain.face.Greetings
import com.rishitgoklani.hermes_front_mobile.domain.face.Reaction
import com.rishitgoklani.hermes_front_mobile.domain.routines.DeviceEvent

/** #325 Greeting and farewell: greets on arrival (time of day, time away) and says goodbye when the user does. */
class GreetingBehavior(private val greeter: ArrivalGreeter, private val classifier: ToneClassifier = KeywordToneClassifier(), private val hour: () -> Int) : Behavior {
    override val id = "greeting"

    override suspend fun react(input: Input): List<Cue> = when (input) {
        is Input.Device -> greeter.onEvent(input.event)?.let { listOf(Cue.Express(Expr.ReactionExpr(Reaction.Giggle, 1f)), Cue.Speak(it, Urgency.Normal)) }.orEmpty()
        is Input.Heard -> if (classifier.classify(input.text) == Tone.Farewell) listOf(Cue.Speak(Greetings.farewell(hour()), Urgency.Normal), Cue.Express(Expr.ReactionExpr(Reaction.Wink, 0.8f))) else emptyList()
        else -> emptyList()
    }
}

/**
 * #334 Attention cue: when the user starts speaking to the bot, glance up and turn the eyes toward them.
 * `presence_check` uses the FRONT camera (the desk robot faces the user) and steers the gaze to the detected face.
 */
class AttentionCue(private val minGapMs: Long = 8_000, private val clock: () -> Long = System::currentTimeMillis) : Behavior {
    override val id = "attention-cue"
    private var last = Long.MIN_VALUE / 2

    override suspend fun react(input: Input): List<Cue> {
        if (input !is Input.State || input.state != BotState.Listening) return emptyList()
        val now = clock()
        if (now - last < minGapMs) return listOf(Cue.Look(0f, 0f))
        last = now
        return listOf(Cue.Look(0f, 0f), Cue.Express(Expr.ReactionExpr(Reaction.Glance, 0.7f)), Cue.Call("presence_check"))
    }
}
