package com.rishitgoklani.hermes_front_mobile.data.vision

import com.rishitgoklani.hermes_front_mobile.domain.vision.FrameSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Writes frames to `<root>/<session>/<name>` (falling back to [fallback] when [root] is not writable). Names are reduced to a safe charset so a caller cannot escape the root. */
class FileFrameSink(private val root: File, private val fallback: File? = null) : FrameSink {
    override suspend fun save(session: String, name: String, jpeg: ByteArray): String = withContext(Dispatchers.IO) {
        fun clean(s: String) = s.filter { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' }.trimStart('.').ifEmpty { "x" }
        val dir = File(root, clean(session)).let {
            if (it.isDirectory || it.mkdirs()) it else File(requireNotNull(fallback) { "cannot create ${it.path}" }, clean(session)).also { d -> d.mkdirs() }
        }
        File(dir, clean(name)).also { it.writeBytes(jpeg) }.path
    }
}
