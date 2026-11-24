package com.rishitgoklani.hermes_front_mobile.testing

import com.rishitgoklani.hermes_front_mobile.domain.bridge.capabilities.LockScreenNotifier

class FakeLockScreenNotifier : LockScreenNotifier {
    val shown = mutableListOf<Pair<String, String>>(); var cleared = 0
    override fun show(title: String, text: String): Int { shown += title to text; return 7 }
    override fun clear() { cleared++ }
}
