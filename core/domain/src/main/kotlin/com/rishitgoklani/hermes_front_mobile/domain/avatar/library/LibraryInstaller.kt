package com.rishitgoklani.hermes_front_mobile.domain.avatar.library

import com.rishitgoklani.hermes_front_mobile.domain.avatar.InstallResult
import com.rishitgoklani.hermes_front_mobile.domain.avatar.ModelInstaller
import com.rishitgoklani.hermes_front_mobile.domain.avatar.VrmInfo
import com.rishitgoklani.hermes_front_mobile.domain.bridge.ActionResult
import com.rishitgoklani.hermes_front_mobile.domain.bridge.Capability
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Imports into the library (optimize, store, Room row) and makes the new avatar the active slot. Used by upload, catalog and the agent tools. */
class LibraryInstaller(private val import: ImportAvatar, private val select: SelectAvatar) : ModelInstaller {
    override suspend fun install(bytes: ByteArray, enforceLicense: Boolean, name: String, source: AvatarSource, onProgress: (ImportProgress) -> Unit): InstallResult =
        when (val r = import(ImportRequest(bytes, name, source, enforceLicense), onProgress)) {
            is ImportResult.Rejected -> InstallResult.Rejected(r.reason)
            is ImportResult.Imported -> { select(AvatarSlot.Model(r.entry.id)); InstallResult.Installed(VrmInfo(r.entry.name, r.entry.author, r.entry.license, "")) }
        }
}

/** First start after the library was introduced: the single old model.vrm becomes the first library entry (already proven, selected if the avatar was on). */
class MigrateLegacyAvatar(private val library: AvatarLibrary, private val import: ImportAvatar, private val proven: MarkProven, private val select: SelectAvatar) {
    suspend operator fun invoke(legacy: ByteArray?, wasOn: Boolean): AvatarEntry? {
        if (legacy == null || library.all().isNotEmpty()) return null
        val r = import(ImportRequest(legacy, "My avatar", AvatarSource.UPLOAD, enforceLicense = false)) as? ImportResult.Imported ?: return null
        proven(r.entry.id); if (wasOn) select(AvatarSlot.Model(r.entry.id))
        return r.entry
    }
}

/** avatar_library: list the installed avatars and switch between them (the same ring the swipe gesture walks). */
fun avatarLibraryCapability(library: AvatarLibrary, active: ActiveSlotStore, select: SelectAvatar, cycle: CycleAvatar, remove: RemoveAvatar) = Capability(
    "avatar_library", "Installed avatars. action=list shows them; select (id) shows one; eyes shows the eyes face; next switches to the next one in the swipe ring; remove (id) deletes one.",
    """{"type":"object","properties":{"action":{"type":"string","enum":["list","select","eyes","next","remove"]},"id":{"type":"string"}},"required":["action"]}""",
) { a ->
    val id = a["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
    when (a["action"]?.jsonPrimitive?.contentOrNull) {
        "list" -> { val cur = active.current(); ActionResult.completed(listOf("eyes${if (cur == AvatarSlot.Eyes) " (showing)" else ""}") .plus(library.all().sortedBy { it.addedAt }.map { "${it.id} | ${it.name} | ${it.status.name.lowercase()}${if (cur == AvatarSlot.Model(it.id)) " | showing" else ""}" }).joinToString("\n")) }
        "select" -> if (library.get(id) == null) ActionResult.failed("no avatar '$id'; use action=list") else { select(AvatarSlot.Model(id)); if (active.current() == AvatarSlot.Model(id)) ActionResult.completed("Showing ${library.get(id)!!.name}") else ActionResult.failed("that avatar crashed the app earlier; remove it or import it again") }
        "eyes" -> { select(AvatarSlot.Eyes); ActionResult.completed("Showing the eyes face") }
        "next" -> ActionResult.completed("Now showing ${cycle().let { s -> if (s is AvatarSlot.Model) library.get(s.id)?.name ?: s.id else "the eyes face" }}")
        "remove" -> if (library.get(id) == null) ActionResult.failed("no avatar '$id'") else { remove(id); ActionResult.completed("Removed $id") }
        else -> ActionResult.failed("action must be list, select, eyes, next or remove")
    }
}
