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

    // A definition stands at column 0 outside every container in Carve 0.2 (C2-B-081): the 0.2
    // reference engine renders each of these lines as item text.
    @Test
    fun `an abbreviation line at an item's body column is text`() {
        val scopes = scopesOf("- item\n\n  *[HTML]: HyperText Markup Language\n", "*[HTML]: HyperText Markup Language")
        assertFalse("the line carries a definition scope: $scopes", scopes.contains(abbreviation))
    }

    @Test
    fun `a link reference line at an item's body column is text`() {
        val scopes = scopesOf("- item\n\n  [r]: /url\n", "[r]: /url")
        assertFalse("the line carries a definition scope: $scopes", scopes.contains(linkDefinition))
    }

    @Test
    fun `a flush-left definition is still a definition`() {
        assertTrue(scopesOf("*[HTML]: HyperText Markup Language\n", "*[HTML]: HyperText Markup Language").contains(abbreviation))
        assertTrue(scopesOf("[r]: /url\n", "[r]: /url").contains(linkDefinition))
    }

    @Test
    fun `the probe answers both ways`() {
        val scopes = scopesOf("- item\n\n  not a definition\n", "not a definition")
        assertFalse("an ordinary indented line scoped as a definition: $scopes", scopes.contains(abbreviation))
        assertFalse("an ordinary indented line scoped as a definition: $scopes", scopes.contains(linkDefinition))
    }
}
