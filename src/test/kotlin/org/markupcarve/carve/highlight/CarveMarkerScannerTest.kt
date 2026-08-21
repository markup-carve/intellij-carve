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

    /**
     * A `>` marker takes a space, or stands alone on its line. Verified against
     * carve-rs: every shape below except the last two renders as a paragraph.
     * `>>` is not a nested marker - that is written `> > x`, a space per marker -
     * and a TAB does not separate (markup-carve/carve#525).
     */
    /**
     * MARKER REQUIRES CONTENT (markup-carve/carve#513).  A marker alone on its
     * line is prose, and trailing whitespace is not content: carve-rs renders
     * both `#` and `#<space>` as `<p>#</p>`.
     *
     * The separator is a space or tab, never any other Unicode space, so a
     * heading whose content starts with NBSP is still a heading.
     */
    @Test
    fun blockMarkersNeedContent() {
        val none = emptyList<Pair<String, TextAttributesKey>>()
        assertEquals(none, covered("#\n"))
        assertEquals(none, covered("# \n"))
        assertEquals(none, covered("-\n"))
        assertEquals(none, covered("- \n"))
        assertEquals(none, covered("1.\n"))
        assertEquals(none, covered("1. \n"))

        assertEquals(listOf("#" to CarveColors.HEADING_MARKER), covered("# H\n"))
        assertEquals(listOf("-" to CarveColors.LIST_MARKER), covered("- item\n"))
        assertEquals(listOf("1." to CarveColors.LIST_MARKER), covered("1. item\n"))
        assertEquals(listOf("#" to CarveColors.HEADING_MARKER), covered("# \u00a0Title\n"))
    }

    @Test
    fun quoteMarkerNeedsASpace() {
        assertEquals(emptyList<Pair<String, TextAttributesKey>>(), covered(">no space\n"))
        assertEquals(emptyList<Pair<String, TextAttributesKey>>(), covered(">>x\n"))
        assertEquals(emptyList<Pair<String, TextAttributesKey>>(), covered(">> x\n"))
        assertEquals(emptyList<Pair<String, TextAttributesKey>>(), covered(">\tx\n"))
        assertEquals(listOf(">" to CarveColors.QUOTE_MARKER), covered(">\n"))
        assertEquals(listOf(">" to CarveColors.QUOTE_MARKER), covered("> real quote\n"))
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
        // Digit run or single letter, with `.` or `)`.
        assertEquals(listOf("10." to CarveColors.LIST_MARKER), covered("10. item\n"))
        assertEquals(listOf("1)" to CarveColors.LIST_MARKER), covered("1) item\n"))
        assertEquals(listOf("a." to CarveColors.LIST_MARKER), covered("a. item\n"))
        // Prose must not be recoloured: a multi-letter word, or a parenthesized counter.
        assertTrue(scan("Note. This is prose\n").isEmpty())
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

    /**
     * A marker may be GLUED to an attribute block, and then the required space comes
     * after the block. Both patterns demanded the space immediately, so the annotator
     * left the marker uncoloured on a line that IS a list item - the bundled grammar
     * colours it (markup-carve/intellij-carve#55). Every shape below was run through
     * carve-js.
     */
    @Test
    fun markerGluedToAnAttributeBlock() {
        assertEquals(listOf("1." to CarveColors.LIST_MARKER), covered("1.{#x} item\n"))
        assertEquals(listOf("-" to CarveColors.LIST_MARKER), covered("-{#x} item\n"))
        assertEquals(listOf("-" to CarveColors.LIST_MARKER), covered("-{#x} [x] done\n"))
        assertEquals(listOf("." to CarveColors.LIST_MARKER), covered(".{#x} item\n"))
    }

    @Test
    fun anAttributeValueMayHoldABrace() {
        // The block is spelled out rather than skipped with a `\{[^}]*\}` run: a quoted
        // value may contain `}` and may escape its own quote, and both of these are list
        // items. A short run stops at the inner brace and loses the marker.
        assertEquals(listOf("1." to CarveColors.LIST_MARKER), covered("1.{title=\"a}b\"} item\n"))
        assertEquals(listOf("1." to CarveColors.LIST_MARKER), covered("1.{title='a}b'} item\n"))
        assertEquals(listOf("1." to CarveColors.LIST_MARKER), covered("1.{title=\"a\\\"b\"} item\n"))
    }

    @Test
    fun anAttributeBlockWithNoContentAfterItIsNotAMarker() {
        // MARKER REQUIRES CONTENT applies past the block too: `1.{#x}` alone is a
        // paragraph (#54), and two glued blocks are a paragraph even with content.
        assertEquals(emptyList<Pair<String, TextAttributesKey>>(), covered("1.{#x}\n"))
        assertEquals(emptyList<Pair<String, TextAttributesKey>>(), covered("-{#x}\n"))
        assertEquals(emptyList<Pair<String, TextAttributesKey>>(), covered("1.{#x}{.y} item\n"))
    }

    /**
     * The bare dot continues an ordered sequence and is the only marker allowed to drop
     * its value (markup-carve/carve#472). The grammar carried it; this did not.
     */
    @Test
    fun bareDotIsAMarker() {
        assertEquals(listOf("." to CarveColors.LIST_MARKER), covered(". first\n"))
        // Not a marker without the separator, and not with nothing after it.
        assertEquals(emptyList<Pair<String, TextAttributesKey>>(), covered(".5 million\n"))
        assertEquals(emptyList<Pair<String, TextAttributesKey>>(), covered(". \n"))
    }

    /**
     * Roman runs, which the grammar carried and this did not. A run is CASE-CONSISTENT:
     * `ivx.` and `IVX.` are lists, `Vim.` and `Mix.` are paragraphs. Checked against
     * carve-js, which is also why this does not copy the grammar's single mixed-case
     * class - that colours `Vim. text` as a list.
     */
    @Test
    fun romanRunsAreMarkersWhenTheCaseIsConsistent() {
        assertEquals(listOf("iv." to CarveColors.LIST_MARKER), covered("iv. fourth\n"))
        assertEquals(listOf("IV." to CarveColors.LIST_MARKER), covered("IV. fourth\n"))
        assertEquals(listOf("xi)" to CarveColors.LIST_MARKER), covered("xi) eleventh\n"))
        assertEquals(emptyList<Pair<String, TextAttributesKey>>(), covered("Vim. text\n"))
        assertEquals(emptyList<Pair<String, TextAttributesKey>>(), covered("Mix. text\n"))
        // A multi-letter non-roman word was already prose and stays prose.
        assertEquals(emptyList<Pair<String, TextAttributesKey>>(), covered("Note. text\n"))
    }

    /**
     * An invalid payload means the `{` is literal content and the line is prose - a
     * brace-delimited run is not enough. Checked against carve-js; the two bundled
     * TextMate grammars disagree with each other on this set, and neither is right.
     */
    @Test
    fun anInvalidGluedPayloadIsNotAMarker() {
        assertEquals(emptyList<Pair<String, TextAttributesKey>>(), covered("1.{2=v} text\n"))
        assertEquals(emptyList<Pair<String, TextAttributesKey>>(), covered("1.{bad!!} text\n"))
        // `{+a+}` is an insertion span, not attributes.
        assertEquals(emptyList<Pair<String, TextAttributesKey>>(), covered("1.{+a+} text\n"))
        // The other direction: bare keys ARE attributes (two booleans), so this IS a
        // list item and the marker is coloured.
        assertEquals(listOf("-" to CarveColors.LIST_MARKER), covered("-{not attrs} text\n"))
        // An empty block is valid and attaches nothing.
        assertEquals(listOf("-" to CarveColors.LIST_MARKER), covered("-{} text\n"))
    }

    /**
     * The identifier is STRICT (spec PART 9 §14). Every shape here was run through
     * carve-js; the dash-first and colon forms are paragraphs, which is easy to get
     * wrong in both directions - the highlighting grammars admit a colon in a key.
     */
    @Test
    fun theGluedPayloadUsesStrictIdentifiers() {
        assertEquals(listOf("1." to CarveColors.LIST_MARKER), covered("1.{data-x=y} item\n"))
        // A BARE boolean may not start with `_` (spec PART 9 §14, markup-carve/carve#1450):
        // `{_x_}` is a forced underline, so the bare attribute reading gave the collision up.
        // Measured through the bundled engine: `-{_k} item` is a paragraph and `-{_k=1} item`
        // is a list item, because only the bare form is narrowed.
        assertEquals(emptyList<Pair<String, TextAttributesKey>>(), covered("-{_k} item\n"))
        assertEquals(listOf("-" to CarveColors.LIST_MARKER), covered("-{_k=1} item\n"))
        assertEquals(listOf("-" to CarveColors.LIST_MARKER), covered("-{#_id} item\n"))
        assertEquals(listOf("1." to CarveColors.LIST_MARKER), covered("1.{#a-b} item\n"))
        assertEquals(emptyList<Pair<String, TextAttributesKey>>(), covered("-{--flag} item\n"))
        assertEquals(emptyList<Pair<String, TextAttributesKey>>(), covered("1.{#-id} item\n"))
        assertEquals(emptyList<Pair<String, TextAttributesKey>>(), covered("-{a:b} item\n"))
        assertEquals(emptyList<Pair<String, TextAttributesKey>>(), covered("-{a:b=v} item\n"))
    }

    @Test
    fun theUnattributedFormsAreUntouched() {
        // The boundary: widening the guard must not change the plain markers.
        assertEquals(listOf("-" to CarveColors.LIST_MARKER), covered("- item\n"))
        assertEquals(listOf("1." to CarveColors.LIST_MARKER), covered("1. item\n"))
        assertEquals(emptyList<Pair<String, TextAttributesKey>>(), covered("1.\n"))
        assertEquals(emptyList<Pair<String, TextAttributesKey>>(), covered("- \n"))
    }
}
