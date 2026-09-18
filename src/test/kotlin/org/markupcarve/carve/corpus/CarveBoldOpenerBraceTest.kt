package org.markupcarve.carve.corpus

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `a *}b* c` is a bold run: a `}` right after the delimiter is content while a
 * closer follows, and refuses the opener only when none does
 * (markup-carve/carve-grammars#487).
 *
 * Readings from the executable grammar at markup-carve/carve `0b2777fd`.
 */
class CarveBoldOpenerBraceTest {

    /** Every character the bold scope covers, delimiters included. */
    private fun bold(src: String): String =
        CarveTextMateTokenizer.tokenize(src)
            .filter { "markup.bold.carve" in it.scope.split(' ') }
            .joinToString("") { it.text }

    @Test
    fun aClosingBraceAfterTheOpenerIsContentWhileACloserFollows() {
        assertEquals("*}b*", bold("a *}b* c"))
        assertEquals("*}b c*", bold("a *}b c* d"))
        assertEquals("*}*", bold("a *}* b"))
        assertEquals("*}a*", bold("*}a*"))
        assertEquals("*}y*", bold("x *}y* z"))
    }

    /** `#bold-in-run` is a second copy of the opener, reached only from inside another run. */
    @Test
    fun theSameHoldsForABoldInsideAnotherRun() {
        assertEquals("*}b*", bold("/a *}b* c/"))
        assertEquals("*}b*", bold("=a *}b* c="))
    }

    @Test
    fun withNoCloserTheBraceStillRefusesTheOpener() {
        assertEquals("", bold("a *}b"))
        assertEquals("", bold("a *} b"))
        assertEquals("{*{*x*}", bold("a{*{*x*}*}b"))
    }
}
