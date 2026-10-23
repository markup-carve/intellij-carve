package org.markupcarve.carve.corpus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A `^ ` line that opens the document has no host above it, so the engines render it as
 * a paragraph (carve-grammars e49bb46). Every later caption line keeps its caption scope.
 */
class CarveDocumentStartCaptionTest {

    private fun caption(tokens: List<CarveTextMateTokenizer.Token>): String =
        tokens.filter { "markup.table.caption.carve" in it.scope.split(' ') }.joinToString("") { it.text }

    private fun caption(src: String): String = caption(CarveTextMateTokenizer.tokenize(src))

    @Test
    fun aCaretLineThatOpensTheDocumentIsText() {
        assertEquals("", caption("^ cap\nafter\n"))
        assertEquals("", caption("^ cap *b*\n"))
        assertEquals("", caption("  ^ cap\n"))
    }

    @Test
    fun caretLinesRightAfterTheFirstStayText() {
        assertEquals("", caption("^ a\n^ b\n"))
        assertEquals("", caption("^ a\n\n^ b\n"))
    }

    @Test
    fun aCaptionLaterInTheDocumentIsStillACaption() {
        assertEquals("^ cap", caption("| a |\n^ cap\n"))
        assertEquals("^ cap", caption("![i](p.png)\n^ cap\n"))
        assertEquals("^ cap", caption("text\n\n| a |\n^ cap\n"))
        assertEquals("^ b", caption("^ a\n| t |\n^ b\n"))
    }

    @Test
    fun theFirstLineStillTakesItsInlineScopes() {
        val scopes = CarveTextMateTokenizer.tokenize("^ cap *b*\n").flatMap { it.scope.split(' ') }
        assertTrue(scopes.any { it.startsWith("markup.bold") })
    }

    @Test
    fun aCarveFenceInAListItemStillCloses() {
        // The body must not include the root patterns, whose document rule never ends.
        val tokens = CarveTextMateTokenizer.tokenize("- item\n  ```carve\n  x\n  ```\n- next *b*\n")
        val after = tokens.dropWhile { !it.text.contains("next") }
        assertTrue(after.none { "meta.embedded.block.carve" in it.scope.split(' ') })
        assertTrue(after.any { it.scope.split(' ').any { s -> s.startsWith("markup.bold") } })
    }
}
