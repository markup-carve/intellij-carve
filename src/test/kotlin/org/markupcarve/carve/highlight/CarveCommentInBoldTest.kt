package org.markupcarve.carve.highlight

import org.junit.Assert.assertEquals
import org.junit.Test
import org.markupcarve.carve.corpus.CarveTextMateTokenizer

/**
 * An unclosed `{%` inside a `*` run is text, so the run still closes at its `*`
 * (intellij-carve#158). A closed comment stays opaque and keeps winning over a
 * `*` inside it. Both readings were rendered through the executable grammar.
 *
 * Assertions rather than a golden: a fixture recorded before the fix pins the
 * wrong answer, which is what hid this one.
 */
class CarveCommentInBoldTest {

    private fun scoped(src: String, scope: String): String =
        CarveTextMateTokenizer.tokenize(src)
            .filter { it.scope.contains(scope) }
            .joinToString("") { it.text }

    @Test
    fun `an unclosed comment opener leaves the bold closer alone`() {
        assertEquals("", scoped("*a {% b* c", "comment.block.inline"))
        assertEquals("*a {% b*", scoped("*a {% b* c", "markup.bold"))
    }

    @Test
    fun `a comment outside bold still opens`() {
        assertEquals("{% b", scoped("a {% b", "comment.block.inline"))
    }

    @Test
    fun `a closed comment in bold keeps both scopes`() {
        assertEquals("{% b %}", scoped("*a {% b %} c*", "comment.block.inline"))
        assertEquals("*a {% b %} c*", scoped("*a {% b %} c*", "markup.bold"))
    }
}
