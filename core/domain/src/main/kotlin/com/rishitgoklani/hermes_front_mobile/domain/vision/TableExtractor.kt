package com.rishitgoklani.hermes_front_mobile.domain.vision

import kotlin.math.abs

/** Turns OCR boxes into rows and columns, then CSV (#47). Pure geometry: no OCR engine knowledge. */
object TableExtractor {
    /** Rows of cell strings, top to bottom, left to right. Cells line up across rows (missing cells are ""). */
    fun rows(boxes: List<TextBox>): List<List<String>> {
        if (boxes.isEmpty()) return emptyList()
        val medianH = boxes.map { it.height }.sorted()[boxes.size / 2]
        val rowTol = medianH * 0.6
        val rows = mutableListOf<MutableList<TextBox>>()
        for (b in boxes.sortedBy { it.centerY }) {
            val row = rows.lastOrNull()
            if (row != null && abs(row.map { it.centerY }.average() - b.centerY) <= rowTol) row += b else rows += mutableListOf(b)
        }
        // merge touching boxes in a row into one cell ("Total" + "sales"); real gaps stay column breaks
        val cellRows = rows.map { r ->
            val out = mutableListOf<TextBox>()
            for (b in r.sortedBy { it.left }) {
                val last = out.lastOrNull()
                if (last != null && b.left - last.right < medianH * 0.8) out[out.lastIndex] = TextBox("${last.text} ${b.text}", last.left, minOf(last.top, b.top), b.right, maxOf(last.bottom, b.bottom))
                else out += b
            }
            out
        }
        val anchors = mutableListOf<Double>()
        val colTol = medianH * 2.5
        for (x in cellRows.flatten().map { it.left.toDouble() }.sorted()) if (anchors.isEmpty() || x - anchors.last() > colTol) anchors += x
        return cellRows.map { r ->
            val cells = MutableList(anchors.size) { "" }
            for (c in r) {
                val i = anchors.indices.minByOrNull { abs(anchors[it] - c.left) }!!
                cells[i] = if (cells[i].isEmpty()) c.text else cells[i] + " " + c.text
            }
            cells
        }
    }

    fun csv(rows: List<List<String>>): String = rows.joinToString("\n") { r -> r.joinToString(",") { quote(it) } }

    private fun quote(s: String): String = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}
