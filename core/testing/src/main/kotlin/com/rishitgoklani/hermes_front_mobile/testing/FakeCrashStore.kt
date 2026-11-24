package com.rishitgoklani.hermes_front_mobile.testing

import com.rishitgoklani.hermes_front_mobile.domain.diagnostics.CrashStore

class FakeCrashStore(var text: String? = null, var failWrites: Boolean = false) : CrashStore {
    override fun read() = text
    override fun write(text: String) { if (failWrites) error("disk full"); this.text = text }
    override fun clear() { text = null }
}
