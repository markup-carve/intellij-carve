package org.markupcarve.carve.corpus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The caption half of what `CarveHeadingInlineTest` pins for headings.
 *
 * Five capture lists reach the inline rules: three heading spellings and two caption
 * spellings. #222 wired `#inline-code` into all five and left the caption side untested,
 * and its six assertions all closed their code spans, so nothing could see that an
 * unpartnered run pushed lexer state a capture never pops (markup-carve/carve#2682). #221
 * replaced that reach with line-bounded rules on all five. These cases hold the caption
 * spellings to the same bar, and add the blank-line tail the heading cases do not use -
 * `#inline-code`'s begin/end fallback ends on a blank line, which is the boundary where a
 * capture's own scopes were left on the stack.
 */
class CarveCaptionInlineTest {

    /** Every spelling that reaches a caption capture, with and without a preceding table. */
    private val prefixes = listOf("^ ", "> ^ ", "| a |\n^ ", "> | b |\n> ^ ")

    private val captionScopes = listOf("markup.table.caption.carve", "string.unquoted.caption.carve")

    private fun covered(source: String, scope: String): String =
        CarveTextMateTokenizer.tokenize(source)
            .filter { scope in it.scope.split(' ') }
            .joinToString("") { it.text }

    private fun scopesOnFollowingLines(source: String): Set<String> =
        CarveTextMateTokenizer.tokenize(source)
            .filter { it.text.contains("plain") }
            .flatMap { it.scope.split(' ') }
            .toSet()

    @Test
    fun anUnclosedInlineRegionStaysOnItsCaptionLine() {
        val openers = listOf(
            "`unclosed", "``unclosed", "`unclosed{=html}", "!`unclosed", "{% unclosed",
            "*unclosed", "/unclosed", "_unclosed", "=unclosed", "~unclosed",
            "{=unclosed", "{+unclosed", "{#unclosed", "^[unclosed", "*a `unclosed*", "*a {% unclosed",
        )
        for (prefix in prefixes) {
            for (opener in openers) {
                // Both tails: the blank line is where a begin/end fallback ends, and the
                // bare newline is where it does not.
                for (tail in listOf("\nplain\n", "\n\nplain\n")) {
                    val source = "$prefix$opener$tail"
                    val leaked = scopesOnFollowingLines(source)
                        .filter { it in captionScopes || it == "markup.raw.inline.carve" }
                    assertTrue(
                        "${source.replace("\n", "\\n")} leaked $leaked onto a following line",
                        leaked.isEmpty(),
                    )
                }
            }
        }
    }

    @Test
    fun theHarnessSeesACaptionScopeWhereOneBelongs() {
        // A negative assertion is worthless from a harness that never reports the scope.
        for (prefix in prefixes) {
            val source = "$prefix`unclosed\n\nplain\n"
            assertTrue(
                source.replace("\n", "\\n"),
                covered(source, "markup.table.caption.carve").contains("unclosed"),
            )
        }
    }

    @Test
    fun anUnclosedCodeSpanStillOwnsTheRestOfItsCaptionLine() {
        // The remedy is not "stop reaching verbatim rules": the open run keeps its payload,
        // percent run included.
        for (prefix in prefixes) {
            val source = "${prefix}cap `x %% b\n\nplain\n"
            assertEquals(source.replace("\n", "\\n"), "x %% b", covered(source, "markup.raw.inline.content.carve"))
            assertEquals(source.replace("\n", "\\n"), "", covered(source, "comment.line.percent.carve"))
        }
    }

    @Test
    fun aClosedCodeSpanInACaptionKeepsItsPayloadAndItsTrailingComment() {
        for (prefix in prefixes) {
            val payload = "${prefix}cap `x %% b` c\n"
            assertEquals(payload.replace("\n", "\\n"), "x %% b", covered(payload, "markup.raw.inline.content.carve"))
            assertEquals(payload.replace("\n", "\\n"), "", covered(payload, "comment.line.percent.carve"))

            val commented = "${prefix}cap `q` b %% hidden\n"
            assertEquals(commented.replace("\n", "\\n"), "%% hidden", covered(commented, "comment.line.percent.carve"))
        }
    }

    @Test
    fun controlsCarryNoLeak() {
        val sources = listOf(
            "^ cap `x` b\n\nplain\n",
            "^ cap b\n\nplain\n",
            "a `unclosed\n\nplain\n",
        )
        for (source in sources) {
            assertFalse(
                source.replace("\n", "\\n"),
                scopesOnFollowingLines(source).any { it in captionScopes },
            )
        }
    }
}
