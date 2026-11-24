package com.rishitgoklani.hermes_front_mobile.domain.diagnostics

/** Port: where the crash text is kept (one small file in app-private storage). */
interface CrashStore {
    fun read(): String?
    fun write(text: String)
    fun clear()
}

/**
 * Keeps the last few crash reports (newest first) in a size-capped text blob.
 * Pure: formatting, redaction of anything that looks like a credential, truncation and rotation.
 * Never given settings or tokens; [redact] is a second line of defence for exception messages.
 */
class CrashLog(
    private val store: CrashStore,
    private val maxReports: Int = 3,
    private val maxBytes: Int = 24_000,
    private val maxReportChars: Int = 8_000,
) {
    /** Never throws: it runs inside the uncaught-exception handler. */
    fun record(thread: String, error: Throwable, version: String, nowMillis: Long) {
        runCatching {
            val report = truncate(redact(format(thread, error, version, nowMillis)), maxReportChars)
            val kept = (listOf(report) + split(store.read())).take(maxReports)
            store.write(capBytes(kept))
        }
    }

    /** The newest report, or null when there is none. */
    fun latest(): String? = split(runCatching { store.read() }.getOrNull()).firstOrNull()

    /** All kept reports, newest first. */
    fun all(): List<String> = split(runCatching { store.read() }.getOrNull())

    fun clear() { runCatching { store.clear() } }

    private fun capBytes(reports: List<String>): String {
        var list = reports
        while (list.size > 1 && join(list).toByteArray().size > maxBytes) list = list.dropLast(1)
        var text = join(list)
        if (text.toByteArray().size > maxBytes) text = text.take(maxBytes / 4) // a single report over the cap: char count under the byte cap even for 4-byte chars
        return text
    }

    companion object {
        const val SEPARATOR = "\n=====\n"
        private fun join(l: List<String>) = l.joinToString(SEPARATOR)
        private fun split(text: String?): List<String> = if (text.isNullOrBlank()) emptyList() else text.split(SEPARATOR).filter { it.isNotBlank() }

        fun format(thread: String, error: Throwable, version: String, nowMillis: Long): String =
            "time: $nowMillis\nthread: $thread\napp: $version\n" + error.stackTraceToString()

        fun truncate(text: String, max: Int): String = if (text.length <= max) text else text.take(max) + "\n... (truncated ${text.length - max} chars)"

        private val patterns = listOf(
            Regex("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]{6,}") to "$1[redacted]",
            Regex("(?i)((?:api[_-]?key|token|authorization|secret|password|passwd)\\s*[=:]\\s*)\\S+") to "$1[redacted]",
            Regex("\\bsk-[A-Za-z0-9_-]{12,}") to "[redacted]",
            Regex("\\b[A-Fa-f0-9]{32,}\\b") to "[redacted]",
        )

        fun redact(text: String): String = patterns.fold(text) { acc, (re, rep) -> re.replace(acc, rep) }
    }
}
