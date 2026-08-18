package org.markupcarve.carve.corpus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A BLOCK OPENER ON A LIST ITEM'S MARKER LINE OPENS THE BLOCK THERE.
 *
 * CarveMarkerLineQuoteTest covers `>` and CarveMarkerLineCommentFenceTest covers
 * `%%%`. This is the same shape for the rest of the line-anchored openers, added
 * together rather than one spelling at a time - a heading, a thematic break, a
 * div fence and a definition term. Measured against the engine bundled in this
 * plugin: `- # h` and `- [x] # h` render the heading inside the item, `- ---`
 * and `1. ---` render an `<hr>` inside it, `- [ ] ::: note` opens the admonition
 * inside it, and `1. :: term` opens a `<dl>` inside it.
 *
 * WHY THESE ARE ASSERTIONS AND NOT ONLY GOLDENS, the point CarveMarkerLineQuoteTest
 * makes next door: a golden agrees with whatever the grammar currently does, so on
 * its own it cannot tell a rule that models the shape from one that never reaches
 * it. This is not hypothetical here. The golden for corpus 363 was committed
 * pinning the broken answer - `# h` with no heading scope at all and `---` scoped
 * as the em-dash rule - and CI stayed green through it
 * (markup-carve/intellij-carve#77).
 *
 * The `>` on line one of that same document kept its quote scope throughout, which
 * is what made the gap visible: one position on one line, handled for one opener
 * and not for the others.
 *
 * BOTH DIRECTIONS on every shape. The payload has to take the block's scope AND
 * the marker has to keep its own list scope: a payload-only check passes a rule
 * that swallows the marker, and a marker-only check passes the broken grammar
 * unchanged.
 */
class CarveMarkerLineBlockOpenerTest {

    private val list = "markup.list."
    private val emDash = "constant.character.entity.typography.carve"

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

    /**
     * Every marker spelling the engine accepts on this line, with the tab-indented
     * variant included because it is the one the family kept missing: the marker's
     * own separator is a literal space, but the indent past it may be a tab, and
     * `- \t# h` is a heading inside the item where `-\t# h` is a paragraph.
     */
    private val markers = listOf(
        "a dash marker" to "- ",
        "a star marker" to "* ",
        "an ordered marker" to "1. ",
        "a bare dot marker" to ". ",
        "a marker run" to "- - ",
        "a task marker" to "- [ ] ",
        "a checked task marker" to "- [x] ",
        "a task marker under a run" to "- - [ ] ",
        "a tab past the separator" to "- \t",
    )

    /**
     * Collects EVERY failing shape rather than stopping at the first. A loop of
     * bare asserts reports one row whatever the state of the rest, which makes the
     * difference between "one spelling regressed" and "the rule is gone" invisible,
     * and that difference is exactly what a revert has to show.
     */
    private fun eachMarker(check: (String) -> String?) {
        val failures = markers.mapNotNull { (label, marker) -> check(marker)?.let { "$label: $it" } }
        assertTrue(
            "${failures.size} of ${markers.size} markers failed:\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    @Test
    fun aHeadingOnAMarkerLineIsAHeading() = eachMarker { marker ->
        val src = "$marker# heading\n\nafter\n"
        val text = scopesOf(src, "heading")
        val hashes = scopesOf(src, "#")
        when {
            !text.contains("markup.heading.carve") -> "the heading text is not in a heading, got $text"
            !text.contains("entity.name.section.carve") -> "the heading text is not a section name, got $text"
            !hashes.contains("keyword.control.heading.carve") -> "the `#` is not a heading marker, got $hashes"
            else -> null
        }
    }

    @Test
    fun aThematicBreakOnAMarkerLineIsNotAnEmDash() = eachMarker { marker ->
        val src = "$marker---\n\nafter\n"
        val run = scopesOf(src, "---")
        when {
            run.contains(emDash) -> "the run is still scoped as an em dash, got $run"
            !run.contains("keyword.control.thematic-break.carve") -> "the run is not a thematic break, got $run"
            else -> null
        }
    }

    @Test
    fun everyThematicBreakSpellingIsCovered() {
        for (run in listOf("---", "***", "___")) {
            val scopes = scopesOf("- [ ] $run\n", run)
            assertTrue(
                "`- [ ] $run` is not a thematic break, got $scopes",
                scopes.contains("keyword.control.thematic-break.carve"),
            )
        }
    }

    @Test
    fun aDivFenceOnAMarkerLineIsADiv() = eachMarker { marker ->
        val src = "$marker::: note\n\nbody\n\n:::\n"
        val fence = scopesOf(src, ":::")
        val kind = scopesOf(src, "note")
        when {
            !fence.contains("keyword.control.div.carve") -> "the fence is not a div fence, got $fence"
            !kind.contains("entity.name.type.div.carve") -> "the div kind is not scoped, got $kind"
            else -> null
        }
    }

    @Test
    fun aDefinitionTermOnAMarkerLineIsATerm() = eachMarker { marker ->
        val src = "$marker:: term\n:  definition\n"
        val scopes = scopesOf(src, "::")
        if (!scopes.contains("markup.list.definition.term.carve")) {
            "the `::` is not a definition term, got $scopes"
        } else {
            null
        }
    }

    @Test
    fun theMarkerKeepsItsOwnListScope() = eachMarker { marker ->
        val failures = listOf("# heading", "---", "::: note", ":: term").mapNotNull { payload ->
            val scopes = scopesOf("$marker$payload\n", marker.trimEnd())
            if (!scopes.contains(list)) "before `$payload` the marker lost its list scope, got $scopes" else null
        }
        failures.firstOrNull()
    }

    /*
     * The intended survivors. Without them a rule that fired on any `#` or `---`
     * after a marker would pass every shape above, and the em-dash rule the
     * thematic break was stolen by would be broken in the other direction.
     */
    @Test
    fun aMarkerLineThatIsNotABlockOpenerStaysAsItWas() {
        // `- #h` is a TAG in the engine (`<span class="tag">`), not a heading: a
        // heading marker needs a space after it.
        val tag = scopesOf("- #h\n", "#h")
        assertFalse("a glued `#` must not be a heading, got $tag", tag.contains("markup.heading.carve"))

        // The marker's own separator is a literal space. `-\t# h` is a paragraph.
        val tabbed = scopesOf("-\t# h\n", "# h")
        assertFalse("a tab separator must not open a heading, got $tabbed", tabbed.contains("markup.heading.carve"))

        // A run that is not at the start of the item's content is still prose, and
        // the em-dash rule must keep it.
        val prose = scopesOf("- a --- b\n", "---")
        assertTrue("a mid-line run must stay an em dash, got $prose", prose.contains(emDash))
        assertFalse(
            "a mid-line run must not become a thematic break, got $prose",
            prose.contains("keyword.control.thematic-break.carve"),
        )

        // `- - -` is nested lists in the engine, not a thematic break.
        val nested = scopesOf("- - -\n", "- - -")
        assertFalse(
            "`- - -` must not be a thematic break, got $nested",
            nested.contains("keyword.control.thematic-break.carve"),
        )

        // The single-colon DEFINITION line has no marker-line form: `- :  def` is
        // ordinary item text in the engine.
        val single = scopesOf("- :  def\n", ":  def")
        assertFalse(
            "`- :  def` must not be a definition term, got $single",
            single.contains("markup.list.definition.term.carve"),
        )
    }

    /*
     * The controls for the anchor. These are the line-anchored rules' own, and
     * they have to keep them: a marker-line rule that reached them would be
     * matching where no marker is.
     */
    @Test
    fun theLineAnchoredRulesAreUntouched() {
        val heading = scopesOf("# heading\n", "heading")
        assertTrue("a document-level heading lost its scope, got $heading", heading.contains("markup.heading.carve"))

        val break_ = scopesOf("---\n", "---")
        assertTrue(
            "a document-level thematic break lost its scope, got $break_",
            break_.contains("keyword.control.thematic-break.carve"),
        )

        val div = scopesOf("::: note\n\nbody\n\n:::\n", ":::")
        assertTrue("a document-level div lost its scope, got $div", div.contains("keyword.control.div.carve"))

        val term = scopesOf(":: term\n:  definition\n", "::")
        assertTrue(
            "a document-level definition term lost its scope, got $term",
            term.contains("markup.list.definition.term.carve"),
        )

        // Frontmatter still wins its own opener: a `---` at the very top of the
        // document with no marker in front of it is not this rule's business.
        val front = scopesOf("---\ntitle: t\n---\n\nbody\n", "title")
        assertFalse("frontmatter must not be scoped as a heading, got $front", front.contains("markup.heading.carve"))
    }

    /*
     * THE INDENTED CONTINUATION IS A DIFFERENT QUESTION and stays out of scope,
     * recorded here so it is not mistaken for this family. `- a` then `  # h` on
     * the NEXT line is a heading inside the item in the engine, and it takes no
     * heading scope here - but `  # h` at the top of a document is a PARAGRAPH,
     * because a top-level block opener must sit at column 0. This grammar has no
     * container model to tell those two apart (the reason `#block-quotes` gives
     * for matching the marker line whole), so anchoring the heading rule on ^ is
     * the safe side of that trade rather than an oversight. A marker LINE needs
     * no container model, which is what makes this family fixable and that one
     * not.
     */
    @Test
    fun anIndentedContinuationLineIsNotCovered() {
        val indented = scopesOf("- a\n  # heading\n", "heading")
        assertFalse(
            "an indented continuation now scopes as a heading - check `  # h` at the top of a " +
                "document is still a paragraph before accepting it, got $indented",
            indented.contains("markup.heading.carve"),
        )

        val top = scopesOf("  # heading\n", "heading")
        assertFalse("an indented heading at column 2 must stay prose, got $top", top.contains("markup.heading.carve"))
    }

    /*
     * The known limitation, asserted so it is a decision rather than a surprise:
     * an ATTRIBUTE-GLUED marker is not covered, because the block's width is not
     * whitespace and the prefix is. `-{#x} # h` IS a heading inside the item in
     * the engine. This is the same gap CarveMarkerLineQuoteTest records for `>`
     * and it is shared with every marker-line rule in this grammar; when it is
     * closed it will be closed for all of them at once, and this assertion is
     * what will flag that this test needs the new expectation.
     */
    @Test
    fun anAttributeGluedMarkerIsNotCoveredYet() {
        val scopes = scopesOf("-{#x} # h\n", "# h")
        assertFalse(
            "an attribute-glued marker now reaches the heading rule - close it for the whole " +
                "marker-line family and update this expectation, got $scopes",
            scopes.contains("markup.heading.carve"),
        )
    }
}
