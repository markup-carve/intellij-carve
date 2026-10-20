package org.markupcarve.carve.corpus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.markupcarve.carve.highlight.CarveColors
import org.markupcarve.carve.highlight.CarveMarkerScanner

class CarveLonePlusMarkerTest {
    private fun markerOffsets(source: String): List<Int> = CarveMarkerScanner.scan(source)
        .filter { it.key == CarveColors.CONTINUATION_MARKER }
        .map { it.range.startOffset }

    private fun grammarMarks(source: String): Boolean {
        var offset = 0
        return CarveTextMateTokenizer.tokenize(source).any { token ->
            val start = offset
            offset += token.text.length
            '+' in token.text && token.scope.contains("markup.list.continuation.carve") &&
                source.substring(start, offset).contains('+')
        }
    }

    @Test
    fun flushLeftPlusAfterListItemIsMarker() {
        val source = "- a\n+\nb\n"
        assertEquals(listOf(source.indexOf('+')), markerOffsets(source))
        assertTrue(grammarMarks(source))
    }

    @Test
    fun indentedPlusIsText() {
        for (source in listOf("- a\n  +\n", "- a\n  - b\n  +\n", "- a\n\t+\n", "- a\n>   +\n")) {
            assertTrue(source, markerOffsets(source).isEmpty())
            assertFalse(source, grammarMarks(source))
        }
    }

    @Test
    fun fencedPlusIsCode() {
        val source = "```\n+\n```\n"
        assertTrue(markerOffsets(source).isEmpty())
        assertFalse(grammarMarks(source))
    }

    private fun scopesPerChar(source: String): List<String> {
        val out = ArrayList<String>()
        for (token in CarveTextMateTokenizer.tokenize(source)) repeat(token.text.length) { out += token.scope }
        return out
    }

    private val afterOpener = listOf(
        "bullet" to "- +\n- b\n> q\nplain\n",
        "numbered" to "1. +\n2. b\n> q\nplain\n",
        "quote" to "> +\n> b\nplain\n",
        "quote then a lazy-looking line" to "> +\n flush\n",
        "nested quote" to "> > +\n> > b\n> c\nplain\n",
        "description body" to ":: t\n: +\n: b\nplain\n",
        "footnote definition" to "[^a]: +\n  b\nplain\n",
        "stacked bullets" to "- - +\n  - b\nplain\n",
        "open task item" to "- [ ] +\n- b\n> q\nplain\n",
        "checked task item" to "- [x] +\n- b\nplain\n",
    )

    @Test
    fun plusAfterAnOpenerIsMarker() {
        for ((label, source) in afterOpener) {
            val plus = source.indexOf('+')
            assertEquals(label, listOf(plus), markerOffsets(source))
            assertTrue(label, grammarMarks(source))
        }
    }

    @Test
    fun linesBelowAnOpenerPlusKeepTheirScoping() {
        for ((label, source) in afterOpener) {
            val control = source.replaceFirst('+', 'x')
            val firstLineEnd = source.indexOf('\n', source.indexOf('+')) + 1
            val marked = scopesPerChar(source)
            val plain = scopesPerChar(control)
            assertEquals(label, plain.size, marked.size)
            assertEquals(label, plain.drop(firstLineEnd), marked.drop(firstLineEnd))
            val markedBelow = CarveMarkerScanner.scan(source)
                .filter { it.range.startOffset >= firstLineEnd }.map { it.key to it.range.startOffset }
            val plainBelow = CarveMarkerScanner.scan(control)
                .filter { it.range.startOffset >= firstLineEnd }.map { it.key to it.range.startOffset }
            assertEquals(label, plainBelow, markedBelow)
        }
    }

    @Test
    fun plusBeforeTextOrAfterAnIndentIsLiteral() {
        for (source in listOf("- + text\n", "- [ ] + text\n", "1. + text\n", "> + text\n", ":: t\n: + text\n", "[^a]: + text\n", "- a\n  +\n", "  +\n")) {
            assertTrue(source, markerOffsets(source).isEmpty())
            assertFalse(source, grammarMarks(source))
        }
    }

    @Test
    fun plusAfterAnOpenerInsideAFenceIsCode() {
        for (source in listOf("```\n- +\n```\n", "```\n> +\n```\n", "```\n: +\n```\n")) {
            assertTrue(source, markerOffsets(source).isEmpty())
            assertFalse(source, grammarMarks(source))
        }
    }
}
