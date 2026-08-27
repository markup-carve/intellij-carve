package org.markupcarve.carve.corpus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins construct identity where a broader sibling rule can otherwise appear to cover it. */
class CarveConstructAttributionTest {
    private fun scopesOf(src: String, needle: String): String {
        val at = src.indexOf(needle)
        assertTrue("Test input has no $needle", at >= 0)
        val end = at + needle.length
        var offset = 0
        return buildString {
            for (token in CarveTextMateTokenizer.tokenize(src)) {
                val start = offset
                offset += token.text.length
                if (start < end && offset > at) append(token.scope).append(' ')
            }
        }
    }

    @Test
    fun referenceImagesAreImagesRatherThanLinks() {
        for (src in listOf("![alt][ref]\n", "![alt][]\n")) {
            val scopes = scopesOf(src, "alt")
            assertTrue("reference image lost image identity: $scopes", scopes.contains("meta.image.reference.carve"))
            assertFalse("reference image was colored as a link: $scopes", scopes.contains("meta.link.reference.carve"))
        }
    }

    @Test
    fun footnoteDefinitionsAreNotLinkDefinitions() {
        val scopes = scopesOf("[^note]: body text\n", "body")
        assertTrue("footnote definition lost its identity: $scopes", scopes.contains("meta.footnote.definition.carve"))
        assertFalse("footnote body was colored as a URL: $scopes", scopes.contains("markup.underline.link.carve"))
    }

    @Test
    fun symbolsCarryTheirOwnScope() {
        val scopes = scopesOf("A :warning: symbol.\n", ":warning:")
        assertTrue("symbol lost its identity: $scopes", scopes.contains("constant.language.symbol.carve"))
    }
}
