package org.markupcarve.carve.corpus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A definition at a container's content column is still a definition.
 *
 * Upstream splits this out as `#definitions-in-container`, reachable only from
 * `#container-body`, because its own `#definitions` is anchored flush-left and
 * cannot see an indented line. This grammar's `#definitions` is anchored
 * `^\s*`, so the indented form and the flush-left form go through one rule and
 * tokenize identically - which is what `upstreamRulesCoveredLocally` claims for
 * that upstream rule, and what these assertions hold it to.
 *
 * WHY ASSERTIONS AND NOT ONLY THE GOLDEN, the point CarveMarkerLineQuoteTest
 * makes next door: a golden agrees with whatever the grammar currently does, so
 * on its own it cannot tell a rule that models the shape from one that never
 * reaches it. `definition-in-container.crv` carries the same shapes so the scope
 * chains stay visible in review; the equivalence itself is pinned here.
 *
 * BOTH DIRECTIONS. The indented line has to carry the definition scopes AND the
 * leading `*` has to stay out of the inline layer - upstream's rule exists
 * because that star is otherwise read as bold (markup-carve/vscode-carve#179) -
 * and a line that is not a definition has to carry none of it, or the probe
 * would pass a grammar that scopes every indented line.
 */
class CarveDefinitionInContainerTest {

    private val abbreviation = "meta.abbreviation.definition.carve"
    private val linkDefinition = "meta.link.reference.definition.carve"

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
    fun `an abbreviation definition at an item's body column is a definition`() {
        val src = "- item\n\n  *[HTML]: HyperText Markup Language\n"
        val scopes = scopesOf(src, "*[HTML]: HyperText Markup Language")
        assertTrue("indented abbreviation definition lost its rule: $scopes", scopes.contains(abbreviation))
        assertTrue("the term lost its scope: $scopes", scopes.contains("entity.name.abbreviation.carve"))
        assertFalse("the leading star was read as emphasis: $scopes", scopes.contains("markup.bold"))
    }

    @Test
    fun `a link reference definition at an item's body column is a definition`() {
        val src = "- item\n\n  [r]: /url\n"
        val scopes = scopesOf(src, "[r]: /url")
        assertTrue("indented link reference definition lost its rule: $scopes", scopes.contains(linkDefinition))
        assertTrue("the destination lost its scope: $scopes", scopes.contains("markup.underline.link.carve"))
    }

    @Test
    fun `the indented form tokenizes as the flush-left form does`() {
        for (line in listOf("*[HTML]: HyperText Markup Language", "[r]: /url")) {
            val flush = scopesOf("$line\n", line)
            val indented = scopesOf("- item\n\n  $line\n", line)
            assertTrue(
                "indent changed the scopes for `$line`:\n  flush-left: $flush\n  indented:   $indented",
                flush == indented,
            )
        }
    }

    @Test
    fun `the probe answers both ways`() {
        val scopes = scopesOf("- item\n\n  not a definition\n", "not a definition")
        assertFalse("an ordinary indented line scoped as a definition: $scopes", scopes.contains(abbreviation))
        assertFalse("an ordinary indented line scoped as a definition: $scopes", scopes.contains(linkDefinition))
    }
}
