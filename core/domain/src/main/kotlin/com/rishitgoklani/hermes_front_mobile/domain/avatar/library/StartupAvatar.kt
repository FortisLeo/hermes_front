package com.rishitgoklani.hermes_front_mobile.domain.avatar.library

/**
 * Which avatar the face will show when the app opens, decided the same way the carousel decides it (a saved model slot, native 3D
 * not switched off, the entry not BROKEN, the file present), so its model and poster can be loaded before the UI asks for them.
 */
class StartupAvatar(private val library: AvatarLibrary, private val active: ActiveSlotStore, private val files: AvatarFiles, private val nativeGl: NativeGlSwitch) {
    data class Pick(val id: String, val textureCap: Int)

    suspend operator fun invoke(): Pick? {
        val slot = active.current() as? AvatarSlot.Model ?: return null
        if (nativeGl.disabled()) return null
        val entry = library.get(slot.id)?.takeIf { it.status != AvatarStatus.BROKEN } ?: return null
        if (files.modelPath(entry.id) == null) return null
        return Pick(entry.id, entry.textureCap)
    }
}
