package org.markupcarve.carve.corpus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * THE TWO MULTI-LINE BLOCK OPENERS ON A LIST ITEM'S MARKER LINE.
 *
 * CarveMarkerLineQuoteTest covers `>`, CarveMarkerLineCommentFenceTest covers
 * `%%%`, and CarveMarkerLineBlockOpenerTest covers `#`, `---`, `:::` and `::`.
 * A code fence and a table reach the same line and are the last two members of
 * the family (markup-carve/intellij-carve#80). They were held back from #79
 * because they are `begin`/`end` rules rather than line matches, and one of them
 * genuinely needed its own end condition.
 *
 * Measured against the engine bundled in this plugin:
 *
 *   `- ```js` / `  code` / `  ``` `   ->  the <pre><code> is inside the item
 *   `- |= a |` / `  | b |`            ->  the <table> is inside the item, and
 *                                         the following row joins it
 *
 * THE TWO NEEDED DIFFERENT AMOUNTS OF WORK, which is the part worth keeping. A
 * code fence needed the split #74 and #76 worked out for the comment fence: an
 * INDENTED closer, because it sits at the item's content column while
 * `#code-blocks`'s own closer is column-0 only, plus a zero-width container
 * boundary reconstructed from the opener's own indent so an unclosed opener ends
 * with the item instead of running to the end of the document.
 *
 * A table needed none of it. `#tables`'s row rule is a ONE-LINE begin/end whose
 * `end` is `$`, so there is nothing to run away, and the rows under the marker
 * line were already matched - only the first row, the one sharing the marker's
 * line, was missing. Assuming the two were the same amount of work is what made
 * this look like a whole PR's worth of container reasoning twice over.
 *
 * BOTH DIRECTIONS on every shape, as next door: the payload has to take the
 * block's scope AND the marker has to keep its own list scope.
 */
class CarveMarkerLineMultilineOpenerTest {

    private val code = "markup.raw.block.fenced.code.carve"
    private val row = "markup.table.row.carve"
    private val list = "markup.list."

    /** Every scope carried by the tokens covering [needle], joined into one string. */
    private fun scopesOf(src: String, needle: String): String = scopesAt(src, src.indexOf(needle), needle)

    /** The same, for the LAST occurrence - an opener and its closer are the same text. */
    private fun lastScopesOf(src: String, needle: String): String =
        scopesAt(src, src.lastIndexOf(needle), needle)

    private fun scopesAt(src: String, at: Int, needle: String): String {
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

    /** marker, and the column its content sits at. */
    private val markers = listOf(
        "a dash marker" to ("- " to "  "),
        "a star marker" to ("* " to "  "),
        "an ordered marker" to ("1. " to "   "),
        "a bare dot marker" to (". " to "  "),
        "a marker run" to ("- - " to "    "),
        "a task marker" to ("- [ ] " to "  "),
        "a checked task marker" to ("- [x] " to "  "),
        "a tab past the separator" to ("- \t" to "  "),
    )

    private fun eachMarker(check: (String, String) -> String?) {
        val failures = markers.mapNotNull { (label, m) -> check(m.first, m.second)?.let { "$label: $it" } }
        assertTrue(
            "${failures.size} of ${markers.size} markers failed:\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    @Test
    fun aCodeFenceOnAMarkerLineOpensACodeBlock() = eachMarker { marker, indent ->
        val src = "$marker```js\n${indent}code\n$indent```\n\nafter\n"
        val fence = scopesOf(src, "```")
        val language = scopesOf(src, "js")
        val body = scopesOf(src, "code")
        when {
            !fence.contains("keyword.control.raw.begin.carve") -> "the fence is not a code fence, got $fence"
            !language.contains("entity.name.type.language.carve") -> "the language is not scoped, got $language"
            !body.contains(code) -> "the body is not inside the code block, got $body"
            else -> null
        }
    }

    @Test
    fun aTildeFenceReachesItToo() = eachMarker { marker, indent ->
        val src = "$marker~~~\n${indent}code\n$indent~~~\n\nafter\n"
        val body = scopesOf(src, "code")
        if (!body.contains(code)) "the body is not inside the code block, got $body" else null
    }

    /**
     * The closer branch, in both directions. Asserting only that `after` is outside
     * the block does NOT test it: the container boundary ends the block at the blank
     * line anyway, so a rule whose closer branch is anchored at column 0 - and which
     * therefore never recognizes the item-column closer at all - passes that check
     * with the closer swallowed as body. Measured: that mutant survived until the
     * closer's own scope was asserted here.
     */
    @Test
    fun theCodeBlockEndsAtItsCloser() = eachMarker { marker, indent ->
        val src = "$marker```js\n${indent}code\n$indent```\n\nafter\n"
        val closer = lastScopesOf(src, "```")
        val after = scopesOf(src, "after")
        when {
            !closer.contains("keyword.control.raw.end.carve") ->
                "the closer at the item's column is not the closer, got $closer"
            after.contains(code) -> "the block ran past its closer, got $after"
            else -> null
        }
    }

    /**
     * The second end branch. An unclosed opener ends WITH THE ITEM here, which is
     * what the engine does - and deliberately not what `#code-blocks` does at the
     * top level, where running to the end of the document IS the engine's answer
     * (markup-carve/intellij-carve#81, CarveUnclosedFenceTest).
     */
    @Test
    fun anUnclosedCodeFenceEndsWithTheItem() = eachMarker { marker, indent ->
        val src = "$marker```js\n${indent}code\n\nafter\n"
        val body = scopesOf(src, "code")
        val after = scopesOf(src, "after")
        when {
            !body.contains(code) -> "the body is not inside the code block, got $body"
            after.contains(code) -> "the unclosed block ran past the item, got $after"
            else -> null
        }
    }

    /**
     * The boundary is the OPENER'S indent, not column 0. A fence on a nested
     * item's marker line must not swallow that item's own sibling, which is the
     * gap #76 closed for the comment fence and the reason this rule captures the
     * indent rather than assuming the document's.
     */
    @Test
    fun aFenceOnANestedItemDoesNotSwallowItsSibling() {
        val src = "- outer\n  - ```js\n    code\n  - sibling\n"
        assertTrue("the nested fence did not open, got ${scopesOf(src, "code")}", scopesOf(src, "code").contains(code))
        assertFalse(
            "the nested fence swallowed its sibling, got ${scopesOf(src, "sibling")}",
            scopesOf(src, "sibling").contains(code),
        )
    }

    @Test
    fun aTableRowOnAMarkerLineIsATableRow() = eachMarker { marker, indent ->
        val src = "$marker|= a |\n$indent| b |\n\nafter\n"
        val header = scopesOf(src, "|=")
        val first = scopesOf(src, " a ")
        val second = scopesOf(src, " b ")
        when {
            !header.contains("keyword.control.table.header.carve") -> "the header marker is not scoped, got $header"
            !first.contains(row) -> "the marker-line row is not a table row, got $first"
            !second.contains(row) -> "the following row lost its scope, got $second"
            else -> null
        }
    }

    @Test
    fun theMarkerKeepsItsOwnListScope() = eachMarker { marker, indent ->
        val failures = listOf("```js\n${indent}code\n$indent```", "|= a |").mapNotNull { payload ->
            val src = "$marker$payload\n"
            val scopes = scopesOf(src, marker.trimEnd())
            if (!scopes.contains(list)) "the marker lost its list scope, got $scopes" else null
        }
        failures.firstOrNull()
    }

    /*
     * The intended survivors and the controls. The marker's own separator is a
     * literal space, an attribute-glued marker is not covered (the same gap the
     * rest of the family records), and both line-anchored rules keep their own.
     */
    @Test
    fun theSurvivorsAndTheControls() {
        val tabbed = scopesOf("-\t```js\ncode\n", "```")
        assertFalse("a tab separator must not open a fence in the item, got $tabbed", tabbed.contains(code))

        val glued = scopesOf("-{#x} ```js\n  code\n  ```\n", "code")
        assertFalse(
            "an attribute-glued marker now reaches the fence rule - close it for the whole " +
                "marker-line family and update this expectation, got $glued",
            glued.contains(code),
        )

        val document = scopesOf("```js\ncode\n```\n", "code")
        assertTrue("a document-level code block lost its scope, got $document", document.contains(code))

        val table = scopesOf("|= a |\n| b |\n", " a ")
        assertTrue("a document-level table row lost its scope, got $table", table.contains(row))
    }
}
