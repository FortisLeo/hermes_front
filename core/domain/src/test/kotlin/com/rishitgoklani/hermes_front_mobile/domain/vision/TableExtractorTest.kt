package com.rishitgoklani.hermes_front_mobile.domain.vision

import org.junit.Assert.assertEquals
import org.junit.Test

class TableExtractorTest {
    private fun b(t: String, l: Int, top: Int, w: Int = 60) = TextBox(t, l, top, l + w, top + 20)

    @Test fun emptyInputGivesNoRows() = assertEquals(emptyList<List<String>>(), TableExtractor.rows(emptyList()))

    @Test fun alignsColumnsAcrossRowsEvenWhenOrderIsShuffled() {
        val rows = TableExtractor.rows(listOf(b("3", 400, 60), b("Apple", 10, 60), b("Qty", 400, 10), b("Name", 10, 10), b("Price", 200, 10), b("1.5", 200, 60)))
        assertEquals(listOf(listOf("Name", "Price", "Qty"), listOf("Apple", "1.5", "3")), rows)
    }
    @Test fun missingCellStaysEmpty() {
        val rows = TableExtractor.rows(listOf(b("A", 10, 10), b("B", 300, 10), b("C", 600, 10), b("x", 10, 60), b("z", 600, 60)))
        assertEquals(listOf("x", "", "z"), rows[1])
    }
    @Test fun adjacentWordsMergeIntoOneCell() {
        val rows = TableExtractor.rows(listOf(b("Total", 10, 10, 50), b("sales", 66, 10, 50), b("9", 400, 10, 20)))
        assertEquals(listOf(listOf("Total sales", "9")), rows)
    }
    @Test fun slightVerticalJitterStaysInOneRow() {
        val rows = TableExtractor.rows(listOf(b("a", 10, 10), TextBox("b", 300, 14, 360, 34)))
        assertEquals(1, rows.size)
    }
    @Test fun csvQuotesCommasAndQuotes() {
        assertEquals("a,\"b,c\",\"say \"\"hi\"\"\"\n1,2,3", TableExtractor.csv(listOf(listOf("a", "b,c", "say \"hi\""), listOf("1", "2", "3"))))
    }
}
