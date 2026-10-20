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
        val source = "- a\n+\n"
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
}
