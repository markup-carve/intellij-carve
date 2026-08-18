package org.markupcarve.carve.corpus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A comment fence opened on a MARKER line hides its body, closes at its own
 * closer, and leaves the rest of the document alone.
 *
 * Spec PART 9 section 24 S2 with section 28 make a comment's body verbatim and
 * invisible WHEREVER the fence sits, so `- %%%` is a fence exactly as a `%%%`
 * on a line of its own is, and `> %%%` is too. Corpus 337 and corpus 70 pin the
 * two spellings, and carve-js at tree-sitter-carve's pin renders
 * `<ul><li></li></ul><p>[r][]</p>` for the first and
 * `<blockquote><p>q</p><p>body</p></blockquote>` for the second.
 *
 * WHY THESE ARE ASSERTIONS AND NOT ONLY A GOLDEN. The list spelling had its
 * category recorded as a SKIP here, with the reason "MEASURED FALSE POSITIVE:
 * the comment fence rule is anchored to the start of the line, so a fence opened
 * after a bullet does not open at all". The measurement was right and the
 * conclusion was not - the category asserts what the LANGUAGE does, and the
 * grammar was the side that was wrong - so a defect had been written down as a
 * deliberate omission and stopped counting as one (markup-carve/intellij-carve#74).
 * A golden generated from the grammar would have agreed with it either way.
 *
 * BOTH DIRECTIONS on every shape, because the failure ran in both at once: the
 * hidden definition came back as live syntax AND the real closer was taken for
 * an opener, which swallowed the paragraph below the item.
 */
class CarveMarkerLineCommentFenceTest {

    private fun tokensOfLinesContaining(src: String, needle: String): List<CarveTextMateTokenizer.Token> {
        val out = ArrayList<CarveTextMateTokenizer.Token>()
        var line = StringBuilder()
        val buffered = ArrayList<CarveTextMateTokenizer.Token>()
        for (token in CarveTextMateTokenizer.tokenize(src)) {
            line.append(token.text)
            buffered.add(token)
            if (token.text.endsWith("\n")) {
                if (line.contains(needle)) out.addAll(buffered)
                line = StringBuilder()
                buffered.clear()
            }
        }
        if (line.contains(needle)) out.addAll(buffered)
        return out
    }

    /**
     * Collected PER LINE rather than per token: without the fix the hidden
     * definition is split across several live tokens, so a per-token test finds
     * none holding the whole needle and reports "not tokenized" for what is
     * really "tokenized as live syntax". A diagnostic that names the wrong
     * failure is how a real one gets read past.
     */
    private fun isHidden(src: String, needle: String): Boolean {
        val tokens = tokensOfLinesContaining(src, needle)
        assertTrue("Test input has no line containing ${needle.replace("\n", "\\n")}", tokens.isNotEmpty())
        return tokens.any { it.scope.contains("comment.") }
    }

    private val listShapes = listOf(
        "a dash marker" to "- %%%\n  [r]: /url\n  %%%\n\n[r][]\n",
        "a star marker" to "* %%%\n  [r]: /url\n  %%%\n\n[r][]\n",
        "an ordered marker" to "1. %%%\n   [r]: /url\n   %%%\n\n[r][]\n",
        "a marker run" to "- - %%%\n    [r]: /url\n    %%%\n\n[r][]\n",
        "a task marker" to "- [ ] %%%\n      [r]: /url\n      %%%\n\n[r][]\n",
        "a wider fence" to "- %%%%\n  [r]: /url\n  %%%%\n\n[r][]\n",
        "an insignificant tail" to "- %%% TODO\n  [r]: /url\n  %%% end\n\n[r][]\n",
        // A fence one item deeper: the closer sits at the NESTED item's content
        // column, and the boundary below has to measure from the opener's own
        // indent rather than from the document's.
        "a fence one item deeper" to "- a\n  - %%%\n    [r]: /url\n    %%%\n\n[r][]\n",
    )

    private val quoteShapes = listOf(
        "a quote marker" to "> %%%\n> [r]: /url\n> %%%\n\n[r][]\n",
        "a nested quote marker" to "> > %%%\n> > [r]: /url\n> > %%%\n\n[r][]\n",
        "a wider quote fence" to "> %%%%\n> [r]: /url\n> %%%%\n\n[r][]\n",
        "an insignificant tail on a quote fence" to "> %%% TODO\n> [r]: /url\n> %%% end\n\n[r][]\n",
        "a quote inside a list item" to "- a\n  > %%%\n  > [r]: /url\n  > %%%\n\n[r][]\n",
        // A quote that itself opened on the item's marker line, which reaches
        // the fence through the marker-line quote rule rather than the
        // line-anchored one (markup-carve/intellij-carve#73).
        "a quote on the item's own marker line" to "- > %%%\n  > [r]: /url\n  > %%%\n\n[r][]\n",
        // The closer repeats the opener's width EXACTLY, so the `> %%%%` inside
        // this fence does not close it and the definition BELOW that run is
        // still hidden. Drop the width backreference and this shows up as the
        // definition scoping live - a direction a swallow-only check cannot see.
        "a wider run inside the fence" to "> %%%\n> a\n> %%%%\n> [r]: /url\n> %%%\n\n[r][]\n",
    )

    private fun eachShape(shapes: List<Pair<String, String>>, check: (String) -> String?) {
        val failures = shapes.mapNotNull { (label, src) -> check(src)?.let { "$label: $it" } }
        assertTrue(
            "${failures.size} of ${shapes.size} shapes failed:\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    @Test
    fun aFenceOnAListMarkerLineHidesItsBody() = eachShape(listShapes) { src ->
        if (!isHidden(src, "[r]: /url")) "the hidden definition is not inside a comment" else null
    }

    @Test
    fun aFenceOnAListMarkerLineClosesAtItsCloser() = eachShape(listShapes) { src ->
        if (isHidden(src, "[r][]")) "the comment ran past its closer" else null
    }

    @Test
    fun aFenceOnAListMarkerLineLeavesTheMarkerToTheListRules() = eachShape(listShapes) { src ->
        val marked = CarveTextMateTokenizer.tokenize(src).any { it.scope.contains("markup.list.") }
        if (!marked) "the list marker lost its list scope" else null
    }

    @Test
    fun aFenceOnAQuoteMarkerLineHidesItsBody() = eachShape(quoteShapes) { src ->
        if (!isHidden(src, "[r]: /url")) "the hidden definition is not inside a comment" else null
    }

    @Test
    fun aFenceOnAQuoteMarkerLineClosesAtItsCloser() = eachShape(quoteShapes) { src ->
        if (isHidden(src, "[r][]")) "the comment ran past its closer" else null
    }

    @Test
    fun aFenceOnAQuoteMarkerLineLeavesTheMarkerToTheQuoteRule() = eachShape(quoteShapes) { src ->
        val marked = CarveTextMateTokenizer.tokenize(src).any { it.scope.contains("markup.quote") }
        if (!marked) "the quote marker lost its quote scope" else null
    }

    @Test
    fun theQuoteContinuesAfterTheFenceCloses() {
        // Corpus 70's own shape: the fence opens BELOW quote content and the
        // quote goes on after the closer, so this checks the closer where the
        // shapes above check the opener.
        val src = "> q\n> %%%\n> x\n> %%%\n> body\n"
        assertTrue("the fence body must be hidden", isHidden(src, "> x"))
        assertFalse("the quote content above the fence stays visible", isHidden(src, "> q"))
        assertFalse("the quote continues after the closer", isHidden(src, "> body"))
    }

    @Test
    fun aColumn0LineEndsTheItemSoItIsNotTheCloser() {
        // Corpus 326-6, and the reason the list closer is `[ \t]+` and not
        // `[ \t]*`: a column-0 line ends the item and with it the open fence, so
        // `c` stays VISIBLE. Closing on any indent would have hidden a visible
        // paragraph to reveal one hidden line.
        // Spelled `visibleline` / `tailline` rather than the corpus's `c` and
        // `tail` for the reason the sibling table gives: a single letter is in
        // half the scope names a token stream carries, and the needle here is a
        // substring test over line TEXT.
        val src = "- %%%\nvisibleline\n%%%\ntailline\n"
        assertFalse("a column-0 line is not inside the fence", isHidden(src, "visibleline"))

        // `tail` is RECORDED, not asserted. The container boundary ends the
        // item's fence at `c` correctly, and then the COLUMN-0 `%%%` below it
        // opens `#comments`, which cannot require its closer up front - a
        // TextMate begin is matched against one line - and so runs to end of
        // document. There is no container boundary at document level to end it
        // at either, and a blank line is not one: a fence body spans blank lines
        // in the engine. The sibling grammars require the closer in the opener's
        // own pattern and decline it, which a TextMate grammar cannot do; the
        // same refusal is recorded in markup-carve/carve-grammars#260.
        //
        // Asserted the OTHER WAY ROUND so the record cannot rot: if this ever
        // stops swallowing, this test fails and the note has to come out.
        assertTrue(
            "an unclosed column-0 run is recorded as running to end of document; " +
                "if it no longer does, delete this assertion and assert `tail` visible instead",
            isHidden(src, "tailline"),
        )
    }

    @Test
    fun aSiblingItemAtTheOpenerSColumnEndsAnUnclosedFence() {
        // The boundary is the OPENER's indent, not column 0. `- outer` /
        // `  - %%%` / `  - sibling` renders as two nested items in the engine -
        // the unclosed run degrades to a line comment and the sibling is an
        // ordinary second item - and a column-0-only boundary swallowed it,
        // because a nested marker line never reaches column 0.
        val src = "- outer\n  - %%%\n  - sibling\n\nafter\n"
        assertFalse("a sibling item at the opener's own column is not inside the fence", isHidden(src, "sibling"))
        assertFalse("the document below stays visible", isHidden(src, "after"))
        assertTrue(
            "the sibling is still a list item",
            tokensOfLinesContaining(src, "sibling").any { it.scope.contains("markup.list.") },
        )
    }

    @Test
    fun aPercentRunGluedToInlineContentIsNotAFence() {
        // The fence must be preceded by the marker's own separator, not by any
        // earlier run on the line: `- /a/%%%` is a percent run at the end of an
        // emphasis run, not an opener, and treating it as one hid the rest of
        // the item.
        val src = "- /a/%%% x\n  b\n\nafter\n"
        assertFalse("the item body stays visible", isHidden(src, "  b"))
        assertFalse("the document below stays visible", isHidden(src, "after"))
    }
}
