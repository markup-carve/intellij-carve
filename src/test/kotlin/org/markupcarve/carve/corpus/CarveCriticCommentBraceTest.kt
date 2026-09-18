package org.markupcarve.carve.corpus

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * An editorial comment's payload is literal, so a `}` inside it does not end it,
 * and a `{#` glued to a construct and closed by a plain `}` is an attribute
 * block (markup-carve/carve-grammars#490).
 *
 * Each row is the text scoped as a comment, delimiters included, or null, read
 * off the executable grammar at markup-carve/carve `0b2777fd`.
 */
class CarveCriticCommentBraceTest {

    private fun comment(src: String): String? =
        CarveTextMateTokenizer.tokenize(src)
            .filter { "comment" in it.scope }
            .joinToString("") { it.text }
            .ifEmpty { null }

    @Test
    fun aClosingBraceInThePayloadDoesNotEndTheComment() {
        assertEquals("{# b} c #}", comment("a {# b} c #} d"))
        assertEquals("{#id} y #}", comment("x{#id} y #} z"))
        assertEquals("{#} b #}", comment("a {#} b #} c"))
        assertEquals("{# b} c #}", comment("*a {# b} c #} d*"))
        assertEquals("{# b #}", comment("a {# b #} c"))
    }

    @Test
    fun anAttributeBlockIsStillAnAttributeBlockAndAnEmptyPairIsText() {
        assertEquals(null, comment("x{#id .c} y"))
        assertEquals(null, comment("*x*{#id} y #} z"))
        assertEquals(null, comment("[t]{#id} and #} z"))
        assertEquals(null, comment("a {##} b"))
    }

    @Test
    fun aCommentGluedToAConstructOpensWhenNoPlainBraceCloses() {
        assertEquals("{#a#}", comment("*x*{#a#} y"))
        assertEquals("{#a#}", comment("[t]{#a#} y"))
        assertEquals("{#a#}", comment("x{#a#} y"))
    }
}
