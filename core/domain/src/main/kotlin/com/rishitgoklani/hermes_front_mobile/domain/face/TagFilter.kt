package com.rishitgoklani.hermes_front_mobile.domain.face

/** Streaming splitter: feed raw reply deltas, get ordered text and expression segments. Tags may span deltas. */
class TagFilter {
    sealed interface Segment { data class Text(val text: String) : Segment; data class Tag(val expr: Expr) : Segment }

    private val buffer = StringBuilder()

    fun feed(delta: String): List<Segment> {
        buffer.append(delta)
        val out = mutableListOf<Segment>()
        while (true) {
            val lt = buffer.indexOf("<")
            if (lt < 0) { emitText(out, buffer.length); break }
            if (lt > 0) { emitText(out, lt); continue }
            // buffer starts with '<'
            val gt = buffer.indexOf(">")
            if (gt < 0) { if (buffer.length > MAX_TAG) emitText(out, 1) else break; continue }
            val candidate = buffer.substring(0, gt + 1)
            val m = TAG.matchEntire(candidate)
            if (m != null) {
                ExpressionNames.parse(m.groupValues[1], m.groupValues[2].toFloatOrNull() ?: 1f)?.let { out += Segment.Tag(it) }
                buffer.delete(0, gt + 1)
            } else emitText(out, 1) // a lone '<' that is not one of our tags
        }
        return out
    }

    fun flush(): List<Segment> = if (buffer.isEmpty()) emptyList() else listOf<Segment>(Segment.Text(buffer.toString())).also { buffer.clear() }

    private fun emitText(out: MutableList<Segment>, n: Int) {
        if (n <= 0) return
        val s = buffer.substring(0, n); buffer.delete(0, n)
        val last = out.lastOrNull()
        if (last is Segment.Text) out[out.size - 1] = Segment.Text(last.text + s) else out += Segment.Text(s)
    }

    private companion object {
        const val MAX_TAG = 60
        val TAG = Regex("""<expr\s+([a-zA-Z_ -]+?)(?:\s+([0-9]*\.?[0-9]+))?\s*/>""")
    }
}
