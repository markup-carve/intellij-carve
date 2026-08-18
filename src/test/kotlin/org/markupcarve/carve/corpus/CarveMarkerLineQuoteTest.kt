package org.markupcarve.carve.corpus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A block quote opened on a list item's MARKER line takes the rest of that line.
 *
 * `- > quoted` opens a quote inside the item. Measured against carve-js at
 * tree-sitter-carve's pin, `- > quoted` renders
 * `<ul><li><blockquote><p>quoted</p></blockquote></li></ul>`, and every marker
 * spelling reaches it - `1. > x`, `* > x`, `- [ ] > x`, `- - > x` - with a
 * marker run after it nesting, `- > > x` being a quote inside a quote.
 *
 * WHY THESE ARE ASSERTIONS AND NOT ONLY GOLDENS, the same point
 * CarveCompositeFigureTest makes next door: a golden agrees with whatever the
 * grammar currently does, so on its own it cannot tell a rule that models the
 * shape from one that never reaches it. The three goldens this shape had were
 * committed pinning the broken answer, with the `>` carrying no scope at all,
 * and CI stayed green through it.
 *
 * BOTH DIRECTIONS on every shape. The quoted run has to carry a quote scope AND
 * the block past the item has to carry none: a quote-scope-only check passes a
 * rule that runs away past the item, and a marker-scope-only check passes the
 * broken grammar unchanged.
 *
 * The oracle is tree-sitter-carve (markup-carve/tree-sitter-carve#218), which
 * puts the `block_quote` inside `list_item_content` beside the `list_marker_*`
 * rather than over it - the split this grammar makes too, with the marker
 * keeping its list scopes and only the content past the `>` scoped as quote.
 */
class CarveMarkerLineQuoteTest {

    private val quote = "markup.quote.carve"
    private val quoteMarker = "keyword.control.quote.carve"
    private val list = "markup.list."

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

    private val shapes = listOf(
        "a dash marker" to "- > quoted\n\nafter\n",
        "a star marker" to "* > quoted\n\nafter\n",
        "an ordered marker" to "1. > quoted\n\nafter\n",
        "a bare dot marker" to ". > quoted\n\nafter\n",
        "a marker run" to "- - > quoted\n\nafter\n",
        "a task marker" to "- [ ] > quoted\n\nafter\n",
        "a quote run on a marker line" to "- > > quoted\n\nafter\n",
    )

    /**
     * Collects EVERY failing shape rather than stopping at the first. A loop of
     * bare asserts reports one row whatever the state of the rest, which makes
     * the difference between "one spelling regressed" and "the rule is gone"
     * invisible - and that difference is exactly what a revert has to show.
     */
    private fun eachShape(check: (String, String) -> String?) {
        val failures = shapes.mapNotNull { (label, src) -> check(label, src)?.let { "$label: $it" } }
        assertTrue(
            "${failures.size} of ${shapes.size} shapes failed:\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    @Test
    fun aQuoteOnAMarkerLineTakesTheRestOfTheLine() = eachShape { _, src ->
        val quoted = scopesOf(src, "quoted")
        val marker = scopesOf(src, ">")
        when {
            !quoted.contains(quote) -> "the quoted run is not inside the quote, got $quoted"
            !marker.contains(quoteMarker) -> "the marker does not scope as a quote marker, got $marker"
            else -> null
        }
    }

    @Test
    fun theQuoteEndsAtTheItem() = eachShape { _, src ->
        val after = scopesOf(src, "after")
        if (after.contains(quote)) "the quote ran past the item, got $after" else null
    }

    @Test
    fun theMarkerKeepsItsOwnListScope() = eachShape { _, src ->
        val marker = scopesOf(src, src.substringBefore(">"))
        if (!marker.contains(list)) "the list marker lost its list scope, got $marker" else null
    }

    /*
     * The intended survivors. A marker separator is a literal SPACE, so none of
     * these opens a quote: the engine renders `- >notquoted` as the item text
     * `&gt;notquoted`, and a TAB on either separator leaves the whole line as
     * prose. Without them a rule that accepted any `>` after a marker would pass
     * every shape above.
     */
    @Test
    fun aMarkerLineThatIsNotAQuoteStaysProse() {
        val survivors = listOf(
            "a glued marker" to "- >notquoted\n",
            "a tab after the quote marker" to "- >\tnotquoted\n",
            "a tab after the list marker" to "-\t> notquoted\n",
            "a tab after an ordered marker" to "1.\t> notquoted\n",
        )
        for ((label, src) in survivors) {
            val scopes = scopesOf(src, "notquoted")
            assertFalse("$label: must not be scoped as a quote, got $scopes", scopes.contains(quote))
            assertFalse("$label: must carry no quote marker either, got $scopes", scopes.contains(quoteMarker))
        }
    }

    /*
     * The controls for the anchor. Both of these are the line-anchored quote
     * rule's own, and it has to keep them: a marker-line rule that reached them
     * would be matching where no marker is.
     */
    @Test
    fun aQuoteOnItsOwnLineIsUntouched() {
        val indented = scopesOf("- a\n  > quoted\n", "quoted")
        assertTrue("an indented quote line lost its scope, got $indented", indented.contains(quote))

        val document = scopesOf("> quoted\n", "quoted")
        assertTrue("a document-level quote lost its scope, got $document", document.contains(quote))
    }

    /*
     * A heading is where a scan-position-anchored rule would misfire: `# > x` is
     * a heading whose text is `&gt; x` in the engine, and a `\G`-anchored quote
     * rule at the top level colours the `> x` there, because `\G` sits right
     * after the heading marker's own match. This grammar has no container model
     * to scope such a rule to, which is why the marker line is matched whole.
     */
    @Test
    fun aQuoteMarkerInsideAHeadingIsNotAQuote() {
        val scopes = scopesOf("# > x\n", "> x")
        assertFalse("a heading's text must not scope as a quote, got $scopes", scopes.contains(quote))
        assertFalse("...nor carry a quote marker, got $scopes", scopes.contains(quoteMarker))
    }
}
