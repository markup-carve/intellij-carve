package org.markupcarve.carve.corpus

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Long lines full of unclosed emphasis openers tokenize in bounded time.
 *
 * Each opener's lookahead scans to the end of the line for its closer, so a line of N
 * openers costs N scans, and braced openers nested in each other multiply that again.
 * The cheap `[^\n]*?` closer pre-check in front of each lookahead rejects an opener with
 * no closer before the expensive scan starts. Before it, `{*a {/b ` x400 took about 30 s
 * and `/a ` x2000 several seconds; the budget sits well above the fixed cost on a loaded
 * machine and well below the unfixed one, which grows quadratically or worse with the line.
 */
class CarveLongLineTimingTest {

    private val budgetMs = 8_000L

    private fun assertFast(what: String, text: String) {
        // A short line of the same shape first, so the measurement excludes regex compilation and JIT warm-up.
        CarveTextMateTokenizer.tokenize(text.take(200) + "\n")
        val start = System.nanoTime()
        CarveTextMateTokenizer.tokenize(text)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("$what (${text.length} chars) took $ms ms, budget $budgetMs ms", ms < budgetMs)
    }

    @Test
    fun nestedBracedOpenersWithoutClosers() {
        assertFast("{*a {/b  x400", "{*a {/b ".repeat(400) + "\n")
        assertFast("{=a {,b {^c  x300", "{=a {,b {^c ".repeat(300) + "\n")
        assertFast("{+a {-b {~c  x300", "{+a {-b {~c ".repeat(300) + "\n")
    }

    @Test
    fun bareOpenersWithoutClosers() {
        for (opener in listOf("*", "/", "_", "~", "=")) {
            assertFast("$opener" + "a  x4000", "${opener}a ".repeat(4000) + "\n")
        }
    }

    @Test
    fun openersInLineBoundedContexts() {
        assertFast("heading of *a ", "# " + "*a ".repeat(4000) + "\n")
        assertFast("list item of /a ", "- " + "/a ".repeat(4000) + "\n")
        assertFast("caption of {*a {/b ", "^ " + "{*a {/b ".repeat(400) + "\n")
    }
}
