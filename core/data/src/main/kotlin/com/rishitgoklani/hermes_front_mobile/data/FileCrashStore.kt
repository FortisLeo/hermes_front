package com.rishitgoklani.hermes_front_mobile.data

import com.rishitgoklani.hermes_front_mobile.domain.diagnostics.CrashStore
import java.io.File

/** Crash text in one private file. */
class FileCrashStore(private val file: File) : CrashStore {
    override fun read(): String? = runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull()
    override fun write(text: String) { file.parentFile?.mkdirs(); file.writeText(text) }
    override fun clear() { file.delete() }
}
