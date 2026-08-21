package org.markupcarve.carve.corpus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Smart typography: what converts, what stays literal, and what a brace pair is.
 *
 * Four rules land together because the grammar carried one stale set of
 * alternatives for all of them, and every shape below was measured through the
 * engine bundled in this plugin rather than read off the clause.
 *
 * A HYPHEN RUN OPENING A WORD AFTER WHITESPACE IS A FLAG (spec PART 9 §8,
 * markup-carve/carve#1443). `git log --oneline` rendered `git log –oneline`,
 * silently and in the output only. The guard is left-flanking-only on purpose:
 * requiring whitespace on both sides would take `pages 1--10` and
 * `the Mon--Fri window` with it, and a numeric range is the reason en dashes
 * exist.
 *
 * THE DOUBLED RUN IS THE CANONICAL ARROW in both families (carve#1442). `=>`
 * is removed rather than deprecated - `key => value` and `Some(x) => x` are
 * prose about code - while `<=` keeps its comparison reading, which is what
 * forced the left double arrow to grow a character.
 *
 * AN EMPTY BRACE PAIR IS NOT A CONSTRUCT (carve#1447). An opener that meets its
 * own closer with nothing between them opened nothing, and the harm is not the
 * empty element: the author's four characters vanish from the output. `{--}` is
 * the single pair that becomes something, the braced en dash, and it reaches
 * exactly the position the flanking rule refuses.
 */
class CarveTypographyTest {

    private val typography = "constant.character.entity.typography.carve"

    /** Every scope carried by the tokens covering [needle] at [from], joined into one string. */
    private fun scopesOf(src: String, needle: String, from: Int = 0): String {
        val at = src.indexOf(needle, from)
        assertTrue("Test input has no $needle", at >= 0)
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

    private fun converts(what: String, src: String, needle: String) {
        val scopes = scopesOf(src, needle)
        assertTrue("$what converts, so `$needle` carries the typography scope: $scopes", scopes.contains(typography))
    }

    private fun literal(what: String, src: String, needle: String) {
        val scopes = scopesOf(src, needle)
        assertFalse("$what stays literal, so `$needle` carries no typography scope: $scopes", scopes.contains(typography))
    }

    @Test
    fun aHyphenRunOpeningAWordAfterWhitespaceIsAFlag() {
        literal("a long CLI flag", "git log --oneline\n", "--oneline")
        literal("a hyphenated flag", "run --force-with-lease now\n", "--force-with-lease")
        literal("a triple run before a word", "x ---That\n", "---That")
    }

    @Test
    fun everyOtherPositionStillConverts() {
        converts("a numeric range", "pages 1--10\n", "--")
        converts("a name range", "the Mon--Fri window\n", "--")
        converts("a trailing run", "a---- b\n", "----")
        converts("a spaced run", "a -- b\n", "--")
        converts("a run after a word", "a-- b\n", "--")
        converts("the opener of an HTML comment", "<!-- a comment\n", "--")
    }

    @Test
    fun theDoubledRunIsTheCanonicalArrow() {
        for (arrow in listOf("<--", "-->", "<-->", "<==", "==>", "<=>")) {
            converts("the canonical arrow `$arrow`", "a $arrow b\n", arrow)
        }
        for (arrow in listOf("<-", "->", "<->")) {
            converts("the deprecated arrow `$arrow`", "a $arrow b\n", arrow)
        }
    }

    @Test
    fun theImplicationArrowIsGoneAndTheComparisonStays() {
        literal("`=>` in prose about code", "key => value\n", "=>")
        converts("`<=` as a comparison", "p <= q\n", "<=")
        converts("`>=`", "r >= s\n", ">=")
        converts("`!=`", "x != y\n", "!=")
    }

    @Test
    fun anEmptyBracePairIsNotAConstruct() {
        val src = "Empty pairs are text: {//} {**} {__} {~~} {^^} {,,} {==} {++} {##}.\n"
        for (pair in listOf("{//}", "{**}", "{__}", "{~~}", "{^^}", "{,,}", "{==}", "{++}", "{##}")) {
            val scopes = scopesOf(src, pair)
            assertFalse(
                "`$pair` renders literally, so it opens no construct: $scopes",
                scopes.contains("markup.") || scopes.contains("comment.block.critic"),
            )
        }
    }

    @Test
    fun aPairThatHoldsSomethingIsStillTheConstruct() {
        val held = mapOf(
            "{/i/}" to "markup.italic",
            "{*b*}" to "markup.bold",
            "{~s~}" to "markup.deleted",
            "{+ins+}" to "markup.inserted",
            "{# c #}" to "comment.block.critic",
            "{---}" to "markup.deleted",
            "{-x-}" to "markup.deleted",
        )
        for ((pair, scope) in held) {
            val src = "Held: $pair\n"
            assertTrue("`$pair` is still a construct: ${scopesOf(src, pair)}", scopesOf(src, pair).contains(scope))
        }
    }

    @Test
    fun theBracedHyphenPairIsAnEnDash() {
        val src = "a {--} b, x{--}y, {--}start\n"
        for (from in listOf(0, src.indexOf("x{--}"), src.indexOf("{--}start"))) {
            val scopes = scopesOf(src, "{--}", from)
            assertTrue("`{--}` is the braced en dash wherever it stands: $scopes", scopes.contains(typography))
            assertFalse("`{--}` is not a deletion of nothing: $scopes", scopes.contains("markup.deleted"))
        }
    }
}
