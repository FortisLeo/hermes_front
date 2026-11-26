package com.rishitgoklani.hermes_front_mobile.ui.avatar

import java.io.File

/**
 * Posters: a JPEG of each avatar as it last looked on screen, shown in the app's first frame (and while a model loads) and then
 * cross-faded into the live 3D view, so opening the app shows the avatar at once. Files live in a cache directory; losing them
 * only means a plain background until the model is up.
 */
class AvatarPosters(private val dir: File) {
    /** The file for [key] in one orientation, or null for a key that is not a plain id (never a path). */
    fun file(key: String, landscape: Boolean): File? = key.takeIf { KEY.matches(it) }?.let { File(dir, "$it.${if (landscape) "land" else "port"}.jpg") }

    /** Written to a temp file first and renamed, so a reader never sees half a JPEG. One poster per orientation: a portrait one stretched onto a landscape screen looks wrong. */
    fun save(key: String, landscape: Boolean, jpeg: ByteArray): Boolean {
        val f = file(key, landscape) ?: return false
        if (jpeg.isEmpty()) return false
        return runCatching { dir.mkdirs(); val tmp = File(dir, "${f.name}.tmp"); tmp.writeBytes(jpeg); tmp.renameTo(f) || run { f.delete(); tmp.renameTo(f) } }.getOrDefault(false)
    }

    fun read(key: String, landscape: Boolean): ByteArray? = file(key, landscape)?.takeIf { it.isFile }?.let { runCatching { it.readBytes() }.getOrNull() }

    fun delete(key: String) { file(key, false)?.delete(); file(key, true)?.delete() }

    private companion object { val KEY = Regex("[A-Za-z0-9_-]{1,80}") }
}
