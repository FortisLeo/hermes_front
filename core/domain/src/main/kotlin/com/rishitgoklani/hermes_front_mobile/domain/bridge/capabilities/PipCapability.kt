package com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities

import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability

/** Picture-in-picture face (#55): shrink the app into a floating PiP window showing the bot face. [enter] reports success. */
fun pipFaceCapability(enter: suspend () -> Boolean) = Capability(
    "pip_face", "Shrink the Deskbot face into a picture-in-picture window that floats above other apps (leave it by tapping it).",
) { if (enter()) ActionResult.completed("Face is in picture-in-picture") else ActionResult.failed("Could not enter picture-in-picture (activity not available)") }
