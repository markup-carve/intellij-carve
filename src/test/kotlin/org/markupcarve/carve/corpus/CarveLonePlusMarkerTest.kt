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
        // The grammar cannot see the item or the next line, so a lone `+` line stays text there.
        assertFalse(grammarMarks(source))
    }

    @Test
    fun indentedPlusIsText() {
        for (source in listOf("- a\n  +\n", "- a\n  - b\n  +\n", "- a\n\t+\n", "- a\n>   +\n")) {
            assertTrue(source, markerOffsets(source).isEmpty())
            assertFalse(source, grammarMarks(source))
        }
    }

    @Test
    fun fenceLeftOfTheItemEndsIt() {
        for (source in listOf("- item\n# Heading\n+\ntext\n", "- item\n---\n+\ntext\n", "- item\n| a |\n+\ntext\n", "- item\n::: note\nx\n:::\n+\ntext\n", "- item\n```\ncode\n```\n+\ntext\n", "> - item\n> ```\n> code\n> ```\n> +\n> text\n")) {
            assertTrue(source, markerOffsets(source).isEmpty())
        }
        // A fence inside the item keeps it open.
        val inside = "- item\n\n  ```\n  code\n  ```\n+\ntext\n"
        assertEquals(listOf(inside.lastIndexOf('+')), markerOffsets(inside))
    }

    @Test
    fun quotedPlusAfterALazyLineKeepsItsOwner() {
        // The lazy `b` continues the inner quote, which the `+` then attaches `c` to.
        val inner = "> - a\n> > q\n>   b\n> +\n> c\n"
        assertEquals(listOf(inner.lastIndexOf('+')), markerOffsets(inner))
        // A quote inside the item keeps the item open for the `+`.
        val item = "> - a\n>   > q\n>   b\n> +\n> c\n"
        assertEquals(listOf(item.lastIndexOf('+')), markerOffsets(item))
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
        "quote" to "> +\nflush\n",
        "nested quote" to "> > +\n> b\nplain\n",
        "description body" to ":: t\n: +\n: b\nplain\n",
        "footnote definition" to "[^a]: +\n  b\nplain\n",
        "stacked bullets" to "- - +\n  - b\nplain\n",
        "open task item" to "- [ ] +\n- b\n> q\nplain\n",
        "checked task item" to "- [x] +\n- b\nplain\n",
    )

    private val grammarTextForms = setOf("quote", "nested quote", "footnote definition")

    @Test
    fun plusAfterAnOpenerIsMarker() {
        for ((label, source) in afterOpener) {
            val plus = source.indexOf('+')
            assertEquals(label, listOf(plus), markerOffsets(source))
            // The grammar scopes only a `+` right after a list or description marker.
            assertEquals(label, label !in grammarTextForms, grammarMarks(source))
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
    fun gluedQuotePrefixPlusIsText() {
        // Each `>` takes one space, so these are not quote lines (markup-carve/carve#3421).
        val glued = listOf(
            ">+\nq\n", ">>+\nq\n", "> >+\n> q\n", ">> +\nq\n",
            "> - a\n>>+\n> b\n", "> - a\n> >+\n> b\n", "> - a\n>+\n> b\n", "- >+\n  b\n",
        )
        // A quote's first-block `+` with no block at an eligible column is text too.
        for (source in listOf("> +\n flush\n", "> +\n> x\n", "> > +\n> > b\n", "> > +\n  b\n", "- > > +\n  > q\n", "- > +\n- s\n", "[^a]: +\n- a\n")) {
            assertTrue(source, markerOffsets(source).isEmpty())
        }
        for (source in glued) {
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
