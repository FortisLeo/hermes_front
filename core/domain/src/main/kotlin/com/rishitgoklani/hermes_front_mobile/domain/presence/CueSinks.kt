package com.rishitgoklani.hermes_front_mobile.domain.presence

import com.rishitgoklani.hermes_front_mobile.domain.SpeechSynthesizer
import com.rishitgoklani.hermes_front_mobile.domain.behavior.RunCapability
import com.rishitgoklani.hermes_front_mobile.domain.face.FaceController

/** Applies cues to the real body through the same ports Hermes uses: speech, face, capabilities. */
class BodyCueSink(private val speaker: SpeechSynthesizer, private val face: FaceController, private val run: RunCapability) : CueSink {
    override suspend fun apply(cue: Cue) {
        when (cue) {
            is Cue.Speak -> speaker.enqueue(cue.text)
            is Cue.Express -> face.apply(cue.expr)
            is Cue.Look -> face.look(cue.x, cue.y)
            is Cue.Call -> run(cue.tool, cue.args)
        }
    }
}
