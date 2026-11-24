package com.rishitgoklani.hermes_front_mobile.domain.avatar.library

/**
 * Remembers that native 3D rendering crashed or kept failing on this GPU, so the next launch does not retry it (a crash loop).
 * The app persists it (DataStore); the user re-enables it with a retry.
 */
interface NativeGlSwitch {
    suspend fun disabled(): Boolean
    suspend fun setDisabled(disabled: Boolean)
}

/** Non-persistent switch, used where nothing is wired. One instance per owner, never shared state. */
class MemoryNativeGlSwitch(private var value: Boolean = false) : NativeGlSwitch {
    override suspend fun disabled() = value
    override suspend fun setDisabled(disabled: Boolean) { value = disabled }
}
