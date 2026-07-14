package org.markupcarve.carve.highlight

import com.intellij.openapi.editor.colors.TextAttributesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarveMarkerScannerTest {

    private fun scan(text: String) =
        CarveMarkerScanner.scan(text).map { Triple(it.range.startOffset, it.range.endOffset, it.key) }

    /** The exact substring a span covers, plus its colour key - the thing that actually matters. */
    private fun covered(text: String): List<Pair<String, TextAttributesKey>> =
        CarveMarkerScanner.scan(text).map { text.substring(it.range.startOffset, it.range.endOffset) to it.key }

    @Test
    fun headingMarkerOnly() {
        assertEquals(listOf("###" to CarveColors.HEADING_MARKER), covered("### Title\n"))
    }

    @Test
    fun bulletAndOrdered() {
        assertEquals(listOf("-" to CarveColors.LIST_MARKER), covered("- item\n"))
        assertEquals(listOf("1." to CarveColors.LIST_MARKER), covered("1. item\n"))
    }

    @Test
    fun continuationIsNotABullet() {
        // A lone `+` is a continuation, not a list bullet (bullets are `-` / `*`).
        assertEquals(listOf("+" to CarveColors.CONTINUATION_MARKER), covered("+\n"))
    }

    @Test
    fun divAndQuote() {
        assertEquals(listOf(":::" to CarveColors.DIV_MARKER), covered("::: note\n"))
        assertEquals(listOf(">" to CarveColors.QUOTE_MARKER), covered("> quote\n"))
    }

    @Test
    fun tablePipesButNotStrayPipe() {
        assertEquals(
            listOf("|" to CarveColors.TABLE_PIPE, "|" to CarveColors.TABLE_PIPE, "|" to CarveColors.TABLE_PIPE),
            covered("| a | b |\n"),
        )
        // A single pipe in prose is not a table row.
        assertTrue(scan("use a | b in text\n").isEmpty())
    }

    @Test
    fun fenceMarkersColoredAndBodyLeftAlone() {
        val text = "```js\nconst a = 1 <1>\n# not a heading in code\n```\n"
        val spans = covered(text)
        // Two fence markers, nothing from the body (no heading marker for the `#` inside).
        assertEquals(listOf("```" to CarveColors.FENCE_MARKER, "```" to CarveColors.FENCE_MARKER), spans)
    }

    @Test
    fun plusFollowedByProseIsNotAContinuation() {
        // Only a lone `+` line, or a `+ ... |` table row, is a continuation.
        assertTrue(scan("+ not a continuation\n").isEmpty())
        assertEquals(listOf("+" to CarveColors.CONTINUATION_MARKER), covered("+\n"))
        assertEquals(
            listOf("+" to CarveColors.CONTINUATION_MARKER, "|" to CarveColors.TABLE_PIPE),
            covered("+ x |\n"),
        )
    }

    @Test
    fun orderedMarkerForms() {
        // Digits or letters (roman included), with `.` or `)`.
        assertEquals(listOf("10." to CarveColors.LIST_MARKER), covered("10. item\n"))
        assertEquals(listOf("1)" to CarveColors.LIST_MARKER), covered("1) item\n"))
        assertEquals(listOf("iv)" to CarveColors.LIST_MARKER), covered("iv) item\n"))
        assertEquals(listOf("a." to CarveColors.LIST_MARKER), covered("a. item\n"))
        // A parenthesized counter is prose, not a marker.
        assertTrue(scan("(1) explain\n").isEmpty())
    }

    @Test
    fun nestedBulletChainColorsEachMarker() {
        assertEquals(
            listOf("-" to CarveColors.LIST_MARKER, "-" to CarveColors.LIST_MARKER),
            covered("- - item\n"),
        )
    }

    @Test
    fun prosePipesAreNotTableSeparators() {
        assertTrue(scan("choose a | b | c here\n").isEmpty())
        // A real row (leading + trailing pipe) is coloured.
        assertEquals(
            List(3) { "|" to CarveColors.TABLE_PIPE },
            covered("| a | b |\n"),
        )
    }

    @Test
    fun shorterFenceRunInsideLongerBlockIsCodeNotACloser() {
        // Opener ```` (4). An inner ``` (3) is code content, not a closer.
        val text = "````\n```\n# still code\n````\n"
        val keys = CarveMarkerScanner.scan(text).map { it.key }
        assertEquals(listOf(CarveColors.FENCE_MARKER, CarveColors.FENCE_MARKER), keys)
    }

    @Test
    fun malformedFenceOpenerDoesNotSuppressMarkersBelow() {
        // `title="x"` is not valid fence info, so the line is not a fence and the heading
        // below must still be coloured.
        val text = "```js title=\"x\"\n# heading\n"
        assertEquals(listOf("#" to CarveColors.HEADING_MARKER), covered(text))
    }

    @Test
    fun pipeInsideInlineCodeIsNotATableSeparator() {
        // The three outer pipes are separators; the one inside `a|b` is code content.
        assertEquals(
            List(3) { "|" to CarveColors.TABLE_PIPE },
            covered("| `a|b` | c |\n"),
        )
    }

    @Test
    fun headingInsideFenceIsNotColored() {
        val text = "# real heading\n\n```\n# fake\n```\n"
        val keys = CarveMarkerScanner.scan(text).map { it.key }
        assertEquals(
            listOf(CarveColors.HEADING_MARKER, CarveColors.FENCE_MARKER, CarveColors.FENCE_MARKER),
            keys,
        )
    }
}
