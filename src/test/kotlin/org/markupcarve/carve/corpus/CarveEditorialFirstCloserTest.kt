package org.markupcarve.carve.corpus

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * An insertion or deletion has no atoms: it closes at its FIRST closer, and a
 * braced opener whose own closer lies past that one is text
 * (markup-carve/carve-grammars#485).
 *
 * Readings from the executable grammar at markup-carve/carve `0b2777fd`:
 * `{+a {-b+} c-} d+}` renders `<ins>a {-b</ins> c-} d+}`.
 */
class CarveEditorialFirstCloserTest {

    /** Every character [scope] covers. The editorial rules name their content, not their delimiters. */
    private fun scoped(src: String, scope: String): String =
        CarveTextMateTokenizer.tokenize(src)
            .filter { scope in it.scope.split(' ') }
            .joinToString("") { it.text }

    @Test
    fun anInsertionClosesAtItsFirstCloser() {
        assertEquals("a {-b", scoped("{+a {-b+} c-} d+}", "markup.inserted.carve"))
        assertEquals("", scoped("{+a {-b+} c-} d+}", "markup.deleted.carve"))
    }

    @Test
    fun aDeletionClosesAtItsFirstCloser() {
        assertEquals("a {*b", scoped("{-a {*b-} c*} d-}", "markup.deleted.carve"))
        assertEquals("", scoped("{-a {*b-} c*} d-}", "markup.bold.carve"))
    }

    @Test
    fun aBracedAtomThatClosesFirstStillNests() {
        assertEquals("b", scoped("{+a {-b-} c+}", "markup.deleted.carve"))
        assertEquals("a {-b-} c", scoped("{+a {-b-} c+}", "markup.inserted.carve"))
    }
}
