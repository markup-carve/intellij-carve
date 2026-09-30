package org.markupcarve.carve.highlight

import org.junit.Assert.assertEquals
import org.junit.Test
import org.markupcarve.carve.corpus.CarveTextMateTokenizer

/**
 * A TextMate capture reaches nothing but the rules it lists. The heading and caption
 * captures listed `#trailing-comment` alone, so no verbatim rule was available inside a
 * title or a caption and `(?<=[ \t])%%.*$` matched between backticks - scoping the rest
 * of the line, closing backtick included, as a comment (intellij-carve#217 for the
 * heading, markup-carve/carve#2682 for the caption).
 *
 * Engine readings this pins, byte-identical across carve-js and carve-php:
 * `# a ` + a backticked `x %% b` + ` c` renders `<h1>a <code>x %% b</code> c</h1>`, and
 * the caption form renders `<figcaption>cap <code>x %% b</code> c</figcaption>`. A real
 * trailing marker is still stripped in both.
 *
 * One assertion group per test: a failure inside a test stops it, and the later
 * readings would then never run.
 */
class CarveCaptureCodeSpanTest {

    private fun scoped(src: String, scope: String): String =
        CarveTextMateTokenizer.tokenize(src)
            .filter { it.scope.contains(scope) }
            .joinToString("") { it.text }

    private val headingSpellings = mapOf(
        "flush left" to "# a `x %% b` c\n",
        "behind a quote marker" to "> # a `x %% b` c\n",
        "on a list item's marker line" to "- # a `x %% b` c\n",
    )

    private val captionSpellings = mapOf(
        "flush left" to "![i](p.png)\n^ cap `x %% b` c\n",
        "behind a quote marker" to "> ^ cap `x %% b` c\n",
    )

    @Test
    fun `a percent run inside a heading's code span is code content`() {
        for ((spelling, src) in headingSpellings) {
            assertEquals("$spelling: payload is code content", "x %% b", scoped(src, "markup.raw.inline.content"))
            assertEquals("$spelling: nothing is a comment", "", scoped(src, "comment.line.percent"))
        }
    }

    @Test
    fun `a real trailing comment on a heading is still a comment`() {
        val lines = mapOf(
            "plain" to "# a %% hidden\n",
            "after an earlier backtick pair" to "# a `q` b %% hidden\n",
            "separated by a tab" to "# a\t%% hidden\n",
        )
        for ((spelling, src) in lines) {
            assertEquals("$spelling", "%% hidden", scoped(src, "comment.line.percent"))
        }
    }

    @Test
    fun `a percent run inside a caption's code span is code content`() {
        for ((spelling, src) in captionSpellings) {
            assertEquals("$spelling: payload is code content", "x %% b", scoped(src, "markup.raw.inline.content"))
            assertEquals("$spelling: nothing is a comment", "", scoped(src, "comment.line.percent"))
        }
    }

    @Test
    fun `a real trailing comment on a caption is still a comment`() {
        val lines = mapOf(
            "plain" to "![i](p.png)\n^ cap %% hidden\n",
            "after an earlier backtick pair" to "![i](p.png)\n^ cap `q` b %% hidden\n",
        )
        for ((spelling, src) in lines) {
            assertEquals("$spelling", "%% hidden", scoped(src, "comment.line.percent"))
        }
    }

    @Test
    fun `the same span in an ordinary paragraph is unchanged`() {
        // Separates "the captures are broken" from "code spans are broken": this reading was
        // already right, and stays right.
        val src = "a `x %% b` c\n"
        assertEquals("x %% b", scoped(src, "markup.raw.inline.content"))
        assertEquals("", scoped(src, "comment.line.percent"))
    }

    @Test
    fun `a caption marker with no figure keeps the reading it had`() {
        // The engines render a `^ ` line that pairs with no figure as a PARAGRAPH. A
        // line-local TextMate rule cannot see whether a figure precedes, so this grammar
        // scopes the line as a caption either way, and did so before this change. Pinned
        // here so the capture fix is not the place that starts or stops scoping prose as a
        // caption: only the payload moves.
        val src = "^ cap `x %% b` c\n"
        assertEquals("^", scoped(src, "keyword.control.caption"))
        assertEquals("x %% b", scoped(src, "markup.raw.inline.content"))
        assertEquals("", scoped(src, "comment.line.percent"))
    }
}
