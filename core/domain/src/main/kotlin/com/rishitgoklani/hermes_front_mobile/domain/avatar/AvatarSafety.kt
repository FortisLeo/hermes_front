package com.rishitgoklani.hermes_front_mobile.domain.avatar

/** Decides at startup whether the 3D avatar stays on, given how many starts in a row did not run a full minute with it enabled. */
object AvatarSafety {
    const val MAX_UNCLEAN = 2
    data class Decision(val avatarOn: Boolean, val uncleanCount: Int)

    fun onStart(modeOn: Boolean, previousUnclean: Int): Decision {
        val n = previousUnclean + if (modeOn) 1 else 0
        return if (n >= MAX_UNCLEAN) Decision(false, 0) else Decision(modeOn, n)
    }
}
