package org.markupcarve.carve.corpus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The term alphabet, which this grammar inherited wrong from its source.
 *
 * `abbreviation_term = (letter | digit)+`, and the spec enumerates `letter` as
 * `a`..`z` plus `A`..`Z`. This copy - like the carve-grammars TextMate grammar
 * it was ported from - required the whole term to be uppercase, so
 * `*[dl]: definition list` and `*[9]: nine` were shown as ordinary paragraph
 * text while every engine treats them as definitions.
 *
 * The engines each had their own version of the same misreading: carve-js
 * required uppercase (carve-js#720), carve-php crashed the render on a
 * digit-only term (carve-php#880), and carve-rs accepted any Unicode
 * alphanumeric (carve-rs#660). carve-grammars#131 fixes the source grammar.
 *
 * WHY THE CORPUS TOKENS DID NOT CATCH IT: every abbreviation document in the
 * shared corpus uses an uppercase multi-letter term - HTML, CSS, A - which is
 * the single shape all of those readings agree on. The rows below are picked
 * for the opposite reason: each separates at least two of them.
 */
class CarveAbbreviationTermTest {

    private data class Term(val term: String, val defines: Boolean, val why: String)

    private val terms = listOf(
        Term("HTML", true, "the shape every reading already agreed on"),
        Term("dl", true, "lowercase"),
        Term("Wm", true, "mixed case"),
        Term("3D", true, "digit-leading"),
        Term("9", true, "a digit alone is a term"),
        Term("e.g.", false, "a dot is neither a letter nor a digit"),
        Term("HTTP API", false, "a space is neither a letter nor a digit"),
        Term("ss", true, "the ASCII spelling of the row below"),
        Term("ß", false, "letter is enumerated ASCII"),
    )

    private fun defines(term: String): Boolean =
        CarveTextMateTokenizer.tokenize("*[$term]: expansion here\n")
            .any { it.scope.contains("entity.name.abbreviation") }

    @Test
    fun `the term alphabet is letter or digit, and letter is ASCII`() {
        val failures = terms.mapNotNull { (term, want, why) ->
            val got = defines(term)
            if (got == want) {
                null
            } else {
                "*[$term]: is ${if (got) "a definition" else "plain text"}, " +
                    "expected ${if (want) "a definition" else "plain text"}   ($why)"
            }
        }
        assertEquals(failures.joinToString("\n", prefix = "\n"), 0, failures.size)
    }

    /**
     * A probe that can only ever answer "not a definition" passes every
     * rejecting row above and reports a green grammar it never read.
     */
    @Test
    fun `the probe answers both ways`() {
        assertTrue("the probe never finds a definition at all", defines("HTML"))
        assertFalse("the probe calls anything a definition", defines("!!"))
    }
}
