package com.rishitgoklani.hermes_front_mobile.domain.avatar.library

/** Memory rule for keeping a second model ready: the staged model is only parsed when both fit the budget. */
object StagingPolicy {
    const val COMBINED_TEXTURE_BUDGET = 96L * 1024 * 1024
    fun shouldStage(current: AvatarEntry?, next: AvatarEntry, lowMemory: Boolean): Boolean =
        !lowMemory && next.status != AvatarStatus.BROKEN && (current?.estTexBytes ?: 0L) + next.estTexBytes <= COMBINED_TEXTURE_BUDGET
}

/** The ring of slots the user swipes through: Eyes, then every usable avatar in the order it was added. */
class FaceCarousel(models: List<AvatarEntry>) {
    private val usable = models.filter { it.status != AvatarStatus.BROKEN }.sortedWith(compareBy({ it.addedAt }, { it.id }))
    val slots: List<AvatarSlot> = listOf<AvatarSlot>(AvatarSlot.Eyes) + usable.map { AvatarSlot.Model(it.id) }

    /** The slot after [current] (wraps). An unknown or removed [current] counts as Eyes. */
    fun next(current: AvatarSlot): AvatarSlot { val i = slots.indexOf(current).coerceAtLeast(0); return slots[(i + 1) % slots.size] }

    /** The avatar worth keeping ready behind [current], or null when the next stop is Eyes (nothing to stage). */
    fun stageTarget(current: AvatarSlot): AvatarEntry? = (next(current) as? AvatarSlot.Model)?.let { m -> usable.firstOrNull { it.id == m.id } }
}
