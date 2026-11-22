package com.rishitgoklani.hermes_front_mobile.data.wake

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** Pure file helpers for the speech model: checksum + zip-slip-safe extraction. */
object ModelFiles {
    fun sha256(file: File): String = file.inputStream().use { sha256(it) }

    fun sha256(input: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256"); val buf = ByteArray(64 * 1024)
        while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Extracts [zip] into [dest]; rejects entries that would escape [dest]. Returns the number of files written. */
    fun unzipSafely(zip: File, dest: File): Int {
        dest.mkdirs(); val root = dest.canonicalFile; var count = 0
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            while (true) {
                val e = zin.nextEntry ?: break
                val target = File(root, e.name).canonicalFile
                if (target != root && !target.path.startsWith(root.path + File.separator)) throw SecurityException("zip entry escapes target: ${e.name}")
                if (e.isDirectory) target.mkdirs() else { target.parentFile?.mkdirs(); target.outputStream().use { zin.copyTo(it) }; count++ }
            }
        }
        return count
    }
}

/** Where the model lives and whether it is ready. The download itself is injected so tests and the app share the logic. */
class ModelStore(
    private val baseDir: File,
    private val expectedSha256: String,
    private val download: suspend (to: File) -> Unit,
    private val modelDirName: String = "vosk-model-small-en-us-0.15",
) {
    val modelDir get() = File(baseDir, "model/$modelDirName")
    fun isReady() = File(modelDir, "conf/model.conf").exists()

    /** Downloads (if needed), verifies the pinned checksum, extracts. Idempotent. Throws on checksum mismatch. */
    suspend fun ensure(): File {
        if (isReady()) return modelDir
        baseDir.mkdirs()
        val zip = File(baseDir, "model.zip.part")
        try {
            download(zip)
            val actual = ModelFiles.sha256(zip)
            if (!actual.equals(expectedSha256, ignoreCase = true)) throw java.io.IOException("model checksum mismatch ($actual)")
            val staging = File(baseDir, "model.staging").also { it.deleteRecursively() }
            ModelFiles.unzipSafely(zip, staging)
            File(baseDir, "model").deleteRecursively()
            staging.renameTo(File(baseDir, "model")).also { if (!it) throw java.io.IOException("could not install model") }
        } finally { zip.delete() }
        check(isReady()) { "model incomplete after extraction" }
        return modelDir
    }
}
