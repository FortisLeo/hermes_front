package com.rishitgoklani.hermes_front_mobile.domain

/** Splits a streamed reply into speakable sentences so TTS can start before the reply ends. */
class SentenceChunker {
    private val buffer = StringBuilder()

    fun feed(delta: String): List<String> {
        buffer.append(delta)
        val out = mutableListOf<String>()
        while (true) {
            val end = boundary(buffer) ?: break
            val sentence = buffer.substring(0, end).trim()
            buffer.delete(0, end)
            if (sentence.isNotEmpty()) out += sentence
        }
        return out
    }

    fun flush(): String? = buffer.toString().trim().also { buffer.clear() }.ifEmpty { null }

    /** Index just after the first sentence end, or null. A terminator needs trailing whitespace to count. */
    private fun boundary(s: CharSequence): Int? {
        for (i in s.indices) {
            val c = s[i]
            if (c == '\n') return i + 1
            if ((c == '.' || c == '!' || c == '?') && i + 1 < s.length && s[i + 1].isWhitespace()) return i + 1
        }
        return null
    }
}
