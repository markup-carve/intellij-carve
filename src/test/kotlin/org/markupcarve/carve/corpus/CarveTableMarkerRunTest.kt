package org.markupcarve.carve.corpus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A CELL'S MARKER RUN ENDS AT A SPACE (spec PART 9 §5 T11).
 *
 * The run is the kind marker `=`, then the alignment run, then an attribute
 * block, and a cell carrying any of the three must follow the run with one
 * literal space. Without it there is no run at all and every character of it is
 * ordinary cell content. The rule here was a bare `|=`, which took the marker
 * wherever it stood: `|=hot= is the reading |` is a data cell holding a
 * highlight span and was coloured as a header, and `|=^ Top |` is a data cell
 * whose text begins `=^` because a vertical marker never stands alone.
 *
 * Every shape below was measured through the engine bundled in this plugin
 * rather than read off the clause, including the two that decide the rule's
 * edges: `|=|` is content, because the closing pipe is not a terminator, and
 * `| a |< |` stays a colspan rather than becoming a left-aligned empty cell,
 * which is why the two span-marker rules keep their place ahead of these.
 *
 * BOTH DIRECTIONS on every shape, and assertions rather than goldens alone: the
 * corpus documents for this rule were classified as a measured false positive
 * exactly because a golden agrees with whatever the grammar does.
 */
class CarveTableMarkerRunTest {

    private val header = "keyword.control.table.header.carve"
    private val alignment = "keyword.control.table.alignment.carve"
    private val separator = "keyword.control.separator.table.carve"

    /** Every scope carried by the tokens covering [needle], joined into one string. */
    private fun scopesOf(src: String, needle: String): String {
        val at = src.indexOf(needle)
        assertTrue("Test input has no ${needle.replace("\n", "\\n")}", at >= 0)
        val end = at + needle.length
        val sb = StringBuilder()
        var offset = 0
        for (token in CarveTextMateTokenizer.tokenize(src)) {
            val start = offset
            offset += token.text.length
            if (start < end && offset > at) sb.append(token.scope).append(' ')
        }
        return sb.toString()
    }

    @Test
    fun aRunFollowedByASpaceIsAMarker() {
        val runs = listOf(
            "a header cell" to Triple("|= a |\n", "|=", header),
            "an empty header cell" to Triple("|= |\n", "|=", header),
            "a header with an attribute block" to Triple("|={#x} R |\n", "|=", header),
            "a right-top run" to Triple("|=>^ Qty |\n", ">^", alignment),
            "a centre run" to Triple("|=~ Item |\n", "~", alignment),
            "a left-bottom run" to Triple("|<v 12 |\n", "<v", alignment),
            "a centre-middle run" to Triple("|~~ both |\n", "~~", alignment),
            "an inherited horizontal run" to Triple("|?v Bottom |\n", "?v", alignment),
        )
        for ((what, case) in runs) {
            val (src, needle, scope) = case
            assertTrue("$what carries $scope: ${scopesOf(src, needle)}", scopesOf(src, needle).contains(scope))
        }
    }

    @Test
    fun aRunWithNoSpaceAfterItIsCellContent() {
        val content = listOf(
            "a glued kind marker" to ("|=a |\n" to "=a"),
            "a highlight span opening the cell" to ("|=hot= is the reading |\n" to "=hot="),
            "the closing pipe as a terminator" to ("|=| x |\n" to "="),
            "a glued attribute block" to ("|{#x}=R|\n" to "=R"),
            "a tab after the kind marker" to ("|=\th |\n" to "=\th"),
        )
        for ((what, case) in content) {
            val (src, needle) = case
            val scopes = scopesOf(src, needle)
            assertFalse("$what is cell content, not a header marker: $scopes", scopes.contains(header))
            assertFalse("$what is cell content, not an alignment run: $scopes", scopes.contains(alignment))
        }
    }

    @Test
    fun aVerticalMarkerNeverStandsAlone() {
        val invalid = listOf(
            "a vertical marker after the kind marker" to ("|=^ Top |\n" to "=^"),
            "a lone inherit marker" to ("|? lone |\n" to "?"),
            "a reversed pair" to ("|v? reversed |\n" to "v?"),
            "an inherit marker with no vertical" to ("|?< wrong |\n" to "?<"),
            "two vertical-first markers" to ("|^< axes |\n" to "^<"),
            "a reverse-order alignment pair" to ("|=v> Reverse |\n" to "=v>"),
        )
        for ((what, case) in invalid) {
            val (src, needle) = case
            val scopes = scopesOf(src, needle)
            assertFalse("$what is ordinary content, not an alignment run: $scopes", scopes.contains(alignment))
            assertFalse("$what carries no header marker either: $scopes", scopes.contains(header))
        }
    }

    @Test
    fun theSpanMarkersKeepTheirPlace() {
        val src = "| a |< |\n"
        val scopes = scopesOf(src, "<")
        assertFalse("a cell holding only `<` is a colspan, not a left-aligned run: $scopes", scopes.contains(alignment))
        val rowspan = scopesOf("| a |^ |\n", "^")
        assertFalse("a cell holding only `^` is a rowspan: $rowspan", rowspan.contains(alignment))
    }

    /**
     * The block a marker carries must be the attribute production, not any
     * brace-delimited run. An invalid payload means the brace is content and
     * there is no run at all - the same ruling the glued list markers take, and
     * the reason a cell's block is spelled out in full rather than skipped with
     * a `\{[^}]*\}`.
     */
    @Test
    fun anInvalidAttributePayloadIsCellContent() {
        val invalid = listOf(
            "an editorial insertion" to ("|={+a+} x |\n" to "={+a+}"),
            "a numeric key" to ("|>{2=v} x |\n" to ">{2=v}"),
            "two glued classes" to ("|={#a#b} x |\n" to "={#a#b}"),
            "a key with no value" to ("|={a=} x |\n" to "={a=}"),
            "an empty block" to ("|={} x |\n" to "={}"),
            "an empty block on an alignment marker" to ("|>{} x |\n" to ">{}"),
        )
        for ((what, case) in invalid) {
            val (src, needle) = case
            val scopes = scopesOf(src, needle)
            assertFalse("$what is cell content, not a header marker: $scopes", scopes.contains(header))
            assertFalse("$what is cell content, not an alignment run: $scopes", scopes.contains(alignment))
        }
    }

    /**
     * The near miss. Refusing an invalid payload must not refuse a valid one,
     * and `{not attrs}` is the shape that looks wrong and is not: two boolean
     * attributes, which the engine renders as a header carrying both.
     */
    @Test
    fun aValidAttributePayloadStillCarriesItsRun() {
        val valid = listOf(
            "two boolean attributes" to Triple("|={not attrs} x |\n", "|=", header),
            "an id" to Triple("|={#ok} x |\n", "|=", header),
            "a language" to Triple("|={:en-GB} x |\n", "|=", header),
            "a class on an alignment marker" to Triple("|>{.num} 9 |\n", ">", alignment),
            "a quoted value holding a brace" to Triple("|<{title=\"a}b\"} x |\n", "<", alignment),
        )
        for ((what, case) in valid) {
            val (src, needle, scope) = case
            assertTrue("$what keeps its run: ${scopesOf(src, needle)}", scopesOf(src, needle).contains(scope))
        }
    }

    @Test
    fun aCellWithNoRunIsUnchanged() {
        for (src in listOf("| a |\n", "|a|\n", "||\n")) {
            val scopes = scopesOf(src, "|")
            assertTrue("`${src.trim()}` still opens a row: $scopes", scopes.contains(separator))
            assertFalse("`${src.trim()}` carries no marker run: $scopes", scopes.contains(header))
        }
    }
}
