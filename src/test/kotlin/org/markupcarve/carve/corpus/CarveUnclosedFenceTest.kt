package org.markupcarve.carve.corpus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WHAT AN UNCLOSED FENCE DOES, PER FENCE, MEASURED RATHER THAN ASSUMED.
 *
 * Document-swallowing is the worst thing a grammar can do - everything below one
 * typo is mis-scoped - so the obvious instinct on seeing one runaway is to give
 * every fence a boundary. Measured against the engine bundled in this plugin,
 * that instinct is wrong for three of the four fences:
 *
 *   `%%%`  unclosed  ->  the opener DEGRADES to a line comment and nothing is
 *                        hidden. `%%%` / `body` / `` / `after` renders
 *                        `<p>body</p><p>after</p>`.
 *   ```    unclosed  ->  the code block RUNS TO THE END OF THE DOCUMENT.
 *                        `` ```js `` / `code` / `` / `after` renders one
 *                        `<pre><code>` holding `code`, a blank line and `after`.
 *   ~~~    unclosed  ->  the same.
 *   :::    unclosed  ->  the div STAYS OPEN to the end of the document, so
 *                        `after` is inside the `<aside>`.
 *
 * So `#code-blocks` running to the end of the document is CORRECT and must not
 * be "fixed" by analogy with the comment fence; that is the regression this file
 * exists to catch. `#divs` is a line `match` that scopes only the opener, so it
 * has no runaway to bound either way.
 *
 * THE ONE ROW THAT IS WRONG IS `%%%` AT THE TOP LEVEL, and it cannot be fixed in
 * a TextMate grammar. The engine's rule is not local: whether the opener is a
 * fence or a line comment depends on whether a closer appears LATER, and a
 * `begin` pattern is matched against one line. Measured directly - a `begin` of
 * `^\s*(%{3,})[^\n]*$(?=[\s\S]*?^\s*\1(?!%))` opens the fence on NEITHER
 * `%%%`/`body`/`%%%` nor `%%%`/`body`, because the lookahead never sees past the
 * current line. All three TextMate grammars in the org carry the identical rule
 * with no fallback (carve-grammars `block_comment`, vscode-carve `#comments`),
 * which is what an expressiveness limit looks like rather than an oversight in
 * one of them.
 *
 * Nor is there a safe early boundary to approximate it with. A comment body may
 * hold a blank line, a column-0 line, a heading and a code fence, and the closer
 * may sit at a different indent from the opener - each of those is asserted
 * below, and each one rules out the boundary that would otherwise be reachable.
 *
 * WHERE A BOUNDARY IS AVAILABLE IT IS ALREADY THERE. A fence on a list item's or
 * a block quote's marker line falls out of its container, and #74 and #76 gave
 * both of those rules exactly that. Those two are the controls here: they are
 * what a bounded runaway looks like, they must keep working, and they are what
 * distinguishes "no boundary was written" from "no boundary exists".
 */
class CarveUnclosedFenceTest {

    private val comment = "comment.block.carve"
    private val code = "markup.raw.block.fenced.code.carve"

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
     * A CLOSED comment fence hides its body, at every spelling the engine accepts.
     *
     * This is the half that any attempt at the unclosed case has to keep. Each row
     * is also a boundary that is NOT available: a body may span a blank line, hold
     * a column-0 line under an indented opener, hold a heading and a nested code
     * fence, and the closer need not sit at the opener's indent. Anything short
     * enough to bound a runaway would cut one of these fences in half.
     */
    @Test
    fun aClosedCommentFenceHidesItsBody() {
        val shapes = listOf(
            "at column 0" to "%%%\nbody\n%%%\n\nafter\n",
            "indented" to "  %%%\n  body\n  %%%\n\nafter\n",
            "an indented opener with a column-0 body" to "  %%%\nbody\n%%%\n\nafter\n",
            "a column-0 opener with an indented closer" to "%%%\nbody\n  %%%\n\nafter\n",
            "a body spanning a blank line" to "%%%\na\n\nbody\n%%%\n\nafter\n",
            "a wider fence" to "%%%% TODO\nbody\n%%%%\n\nafter\n",
            "a body holding a heading and a code fence" to "%%%\n# h\nbody\n```js\nx\n```\n%%%\n\nafter\n",
        )
        val failures = shapes.mapNotNull { (label, src) ->
            val body = scopesOf(src, "body")
            val after = scopesOf(src, "after")
            when {
                !body.contains(comment) -> "$label: the body is not hidden, got $body"
                after.contains(comment) -> "$label: the fence ran past its closer, got $after"
                else -> null
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * An unclosed CODE fence running to the end of the document is the engine's
     * answer, not a bug shaped like the comment one. Pinned so the comment fence's
     * defect is not "fixed" here by analogy.
     */
    @Test
    fun anUnclosedCodeFenceRunsToTheEndOfTheDocument() {
        for (fence in listOf("```js", "~~~")) {
            val src = "$fence\ncode\n\nafter\n"
            val after = scopesOf(src, "after")
            assertTrue(
                "an unclosed `$fence` stopped early - the engine keeps it open to the end of " +
                    "the document, so check the engine before changing this, got $after",
                after.contains(code),
            )
        }
    }

    /**
     * The div rule is a line `match` on the opener, so div content is never scoped
     * as div whether the fence closes or not, and there is no runaway to bound.
     * Asserted so a boundary is not added to a rule that has nothing to bound.
     */
    @Test
    fun anUnclosedDivFenceHasNoRunawayToBound() {
        val src = "::: note\nbody\n\nafter\n"
        assertTrue(
            "the div opener lost its scope, got ${scopesOf(src, ":::")}",
            scopesOf(src, ":::").contains("keyword.control.div.carve"),
        )
        assertFalse(
            "div content is now scoped as div - the engine does keep the div open here, but " +
                "this rule has never scoped content and widening it is a separate decision, " +
                "got ${scopesOf(src, "after")}",
            scopesOf(src, "after").contains("markup.other.div.carve"),
        )
    }

    /**
     * The controls. Both marker-line variants bound their runaway to the container
     * they sit in, which is what #74 and #76 put there, and it is the only place a
     * boundary is available. If these ever stop bounding, the top-level limitation
     * below stops being a limitation and becomes an excuse.
     */
    @Test
    fun anUnclosedFenceOnAMarkerLineIsBoundedByItsContainer() {
        val item = "- %%%\n  body\n\nafter\n"
        assertTrue("the item's fence did not open, got ${scopesOf(item, "body")}", scopesOf(item, "body").contains(comment))
        assertFalse("the item's fence ran past the item, got ${scopesOf(item, "after")}", scopesOf(item, "after").contains(comment))

        val quote = "> %%%\n> body\n\nafter\n"
        assertTrue("the quote's fence did not open, got ${scopesOf(quote, "body")}", scopesOf(quote, "body").contains(comment))
        assertFalse("the quote's fence ran past the quote, got ${scopesOf(quote, "after")}", scopesOf(quote, "after").contains(comment))
    }

    /**
     * THE LIMITATION, asserted rather than left to be rediscovered. An unclosed
     * `%%%` at the top level greys out the rest of the document; the engine
     * degrades the opener to a line comment and greys nothing.
     *
     * If this test starts failing, the limitation has been lifted and that is good
     * news - but check it was lifted rather than traded: `aClosedCommentFenceHidesItsBody`
     * above must still pass, because every boundary short enough to stop the
     * runaway also cuts one of those seven fences in half.
     */
    @Test
    fun anUnclosedCommentFenceAtTheTopLevelStillGreysTheRest() {
        val src = "%%%\nbody\n\nafter\n"
        val after = scopesOf(src, "after")
        assertTrue(
            "an unclosed top-level `%%%` no longer greys the rest of the document. That is the " +
                "engine's behavior and the right outcome - confirm aClosedCommentFenceHidesItsBody " +
                "still passes, then delete this test rather than inverting it, got $after",
            after.contains(comment),
        )
    }
}
