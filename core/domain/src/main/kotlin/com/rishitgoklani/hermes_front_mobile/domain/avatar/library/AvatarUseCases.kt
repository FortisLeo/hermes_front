package com.rishitgoklani.hermes_front_mobile.domain.avatar.library

import com.rishitgoklani.hermes_front_mobile.domain.avatar.AvatarModelGuard
import com.rishitgoklani.hermes_front_mobile.domain.avatar.TextureBudget
import com.rishitgoklani.hermes_front_mobile.domain.avatar.VrmCheck
import com.rishitgoklani.hermes_front_mobile.domain.avatar.VrmLicense

data class ImportRequest(val bytes: ByteArray, val name: String, val source: AvatarSource, val enforceLicense: Boolean)
data class ImportProgress(val stage: String, val done: Int = 0, val total: Int = 0)

sealed interface ImportResult {
    data class Imported(val entry: AvatarEntry) : ImportResult
    data class Rejected(val reason: String) : ImportResult
}

/** Validate, optimize and store a model, then add it to the library as PENDING (the viewer proves it later via [MarkProven]). */
class ImportAvatar(
    private val library: AvatarLibrary, private val files: AvatarFiles, private val optimizer: GlbOptimizer,
    private val newId: () -> String, private val now: () -> Long, private val budget: TextureBudget = TextureBudget(),
) {
    suspend operator fun invoke(req: ImportRequest, onProgress: (ImportProgress) -> Unit = {}): ImportResult {
        onProgress(ImportProgress("Checking"))
        AvatarModelGuard.problem(req.bytes)?.let { return ImportResult.Rejected(it) }
        val info = when (val c = VrmLicense.check(req.bytes, req.enforceLicense)) { is VrmCheck.Rejected -> return ImportResult.Rejected(c.reason); is VrmCheck.Ok -> c.info }
        val plan = budget.plan(req.bytes)
        onProgress(ImportProgress("Optimizing"))
        val opt = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { optimizer.optimize(req.bytes, plan.cap) { d, t -> onProgress(ImportProgress("Optimizing textures", d, t)) } }
        val after = budget.plan(opt.bytes)
        val id = newId()
        onProgress(ImportProgress("Saving"))
        files.write(id, opt.bytes)
        val saved = if (opt.savedBytes > 0) "Optimized: textures at most ${plan.cap} px, file ${"%.1f".format(opt.savedBytes / 1048576.0)} MB smaller." else plan.note()
        val t = now()
        val entry = AvatarEntry(id, req.name.ifBlank { info.title.ifBlank { "Avatar" } }, req.source, info.license, info.author, req.bytes.size.toLong(), opt.bytes.size.toLong(), plan.cap, after.estimatedBytes, saved, null, t, t, AvatarStatus.PENDING)
        library.add(entry)
        return ImportResult.Imported(entry)
    }
}

/** The viewer rendered the model and the app survived: it is safe. */
class MarkProven(private val library: AvatarLibrary) { suspend operator fun invoke(id: String) { if (library.get(id)?.status == AvatarStatus.PENDING) library.setStatus(id, AvatarStatus.OK) } }

/**
 * At startup: the app died while a model was still PENDING. Only the model that was on screen is blamed (BROKEN, and the slot goes back to Eyes);
 * other pending models were never shown, so they stay PENDING and get their chance when shown.
 */
class ReconcilePending(private val library: AvatarLibrary, private val active: ActiveSlotStore) {
    suspend operator fun invoke(): List<AvatarEntry> {
        val slot = active.current() as? AvatarSlot.Model ?: return emptyList()
        val bad = library.get(slot.id)?.takeIf { it.status == AvatarStatus.PENDING } ?: return emptyList()
        library.setStatus(bad.id, AvatarStatus.BROKEN); active.set(AvatarSlot.Eyes)
        return listOf(bad)
    }
}

class SelectAvatar(private val library: AvatarLibrary, private val active: ActiveSlotStore, private val now: () -> Long) {
    suspend operator fun invoke(slot: AvatarSlot) {
        if (slot is AvatarSlot.Model) { if (library.get(slot.id)?.status?.let { it != AvatarStatus.BROKEN } != true) return; library.touch(slot.id, now()) }
        active.set(slot)
    }
}

/** Swipe: move to the next slot of the ring. Returns the new slot. */
class CycleAvatar(private val library: AvatarLibrary, private val active: ActiveSlotStore, private val select: SelectAvatar) {
    suspend operator fun invoke(): AvatarSlot { val next = FaceCarousel(library.all()).next(active.current()); select(next); return next }
}

class RemoveAvatar(private val library: AvatarLibrary, private val files: AvatarFiles, private val active: ActiveSlotStore) {
    suspend operator fun invoke(id: String) {
        val slot = active.current()
        if (slot is AvatarSlot.Model && slot.id == id) active.set(AvatarSlot.Eyes)
        files.delete(id); library.remove(id)
    }
}
