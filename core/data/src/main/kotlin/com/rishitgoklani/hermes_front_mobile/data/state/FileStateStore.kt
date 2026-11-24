package com.rishitgoklani.hermes_front_mobile.data.state

import com.rishitgoklani.hermes_front_mobile.domain.state.StateStore
import java.io.File

/** One small file per key under [dir]; writes go through a temp file and rename so a crash never leaves a half-written value. Keys are restricted to a safe charset. */
class FileStateStore(private val dir: File) : StateStore {
    private fun file(key: String): File {
        require(Regex("[a-z0-9_]{1,60}").matches(key)) { "bad state key" }
        return File(dir, "$key.json")
    }

    @Synchronized override fun load(key: String): String? = runCatching { file(key).takeIf { it.isFile }?.readText() }.getOrNull()

    @Synchronized override fun save(key: String, value: String) {
        dir.mkdirs()
        val target = file(key); val tmp = File(dir, "$key.json.tmp")
        tmp.writeText(value)
        if (!tmp.renameTo(target)) { target.writeText(value); tmp.delete() }
    }
}
