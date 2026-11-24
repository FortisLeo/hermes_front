package com.rishitgoklani.hermes_front_mobile.testing

import com.rishitgoklani.hermes_front_mobile.domain.avatar.library.NativeGlSwitch

class FakeNativeGlSwitch(var value: Boolean = false) : NativeGlSwitch {
    var writes = 0
    override suspend fun disabled() = value
    override suspend fun setDisabled(disabled: Boolean) { value = disabled; writes++ }
}
