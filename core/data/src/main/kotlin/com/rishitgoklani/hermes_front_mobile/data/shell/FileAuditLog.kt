package com.rishitgoklani.hermes_front_mobile.data.shell

import com.rishitgoklani.hermes_front_mobile.domain.bridge.shell.AuditLog
import java.io.File

/** Append-only text log, capped so it cannot fill storage: rotates to `.1` past [maxBytes]. */
class FileAuditLog(
    private val file: File,
    private val maxBytes: Long = 512 * 1024,
    private val clock: () -> Long = System::currentTimeMillis,
) : AuditLog {
    @Synchronized
    override fun record(tool: String, args: String, status: String) {
        runCatching {
            file.parentFile?.mkdirs()
            if (file.exists() && file.length() > maxBytes) file.renameTo(File(file.path + ".1"))
            file.appendText("${clock()}\t$tool\t$status\t${args.take(500).replace('\n', ' ')}\n")
        }
    }
}
