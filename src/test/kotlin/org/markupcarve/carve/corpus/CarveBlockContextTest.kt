package org.markupcarve.carve.corpus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Block markers that depend on the line before them, each shape rendered through the
 * bundled carve-js first.
 *
 * A list marker, a `^ ` caption and a lone `+` do not interrupt a paragraph, so after a
 * paragraph line they are text. A list item holds its lazy lines, so a marker after one
 * is a sibling item. A `^ ` line after a heading is text, blank lines between or not.
 * Behind `> ` the same block markers open blocks, and a table row needs its closing pipe
 * in both places.
 */
class CarveBlockContextTest {

    private val bullet = "markup.list.unnumbered.carve"
    private val numbered = "markup.list.numbered.carve"
    private val caption = "markup.table.caption.carve"
    private val continuation = "keyword.control.list.continuation.carve"
    private val tableRow = "markup.table.row.carve"
    private val div = "markup.other.div.carve"

    private fun scopesOf(src: String, needle: String, grammar: CarveTextMateTokenizer.Grammar? = null): String {
        val at = src.lastIndexOf(needle)
        assertTrue("Test input has no ${needle.replace("\n", "\\n")}", at >= 0)
        val end = at + needle.length
        val sb = StringBuilder()
        var offset = 0
        val tokens = grammar?.tokenize(src) ?: CarveTextMateTokenizer.tokenize(src)
        for (token in tokens) {
            val start = offset
            offset += token.text.length
            if (start < end && offset > at) sb.append(token.scope).append(' ')
        }
        return sb.toString()
    }

    @Test
    fun aMarkerLineAfterAParagraphIsText() {
        val shapes = listOf(
            "para\n- not a list\n" to ("- not" to bullet),
            "para\n* not a list\n" to ("* not" to bullet),
            "para\n1. not a list\n" to ("1." to numbered),
            "para\na. not a list\n" to ("a." to numbered),
            "para\n^ not a caption\n" to ("^ not" to caption),
            "para\n+\n" to ("+" to continuation),
            "para\n- one\n- two\n" to ("- two" to bullet),
        )
        for ((src, case) in shapes) {
            val (needle, scope) = case
            val scopes = scopesOf(src, needle)
            assertFalse("${src.replace("\n", "\\n")}: $needle is paragraph text, got $scopes", scopes.contains(scope))
        }
    }

    @Test
    fun aMarkerLineAfterABlankLineStillOpensItsBlock() {
        assertTrue(scopesOf("para\n\n- item\n", "-").contains(bullet))
        assertTrue(scopesOf("para\n\n1. item\n", "1.").contains(numbered))
        assertTrue(scopesOf("| a |\n^ cap\n", "^").contains(caption))
        assertTrue(scopesOf("![alt](a.png)\n^ cap\n", "^").contains(caption))
    }

    @Test
    fun aParagraphEndsAtALineThatInterruptsIt() {
        assertTrue(scopesOf("para\n# Heading\n", "#").contains("markup.heading.carve"))
        assertTrue(scopesOf("para\n> quote\n", ">").contains("keyword.control.quote.carve"))
        assertTrue(scopesOf("para\n| a | b |\n", "| b").contains(tableRow))
    }

    @Test
    fun aListItemHoldsItsLazyLines() {
        assertTrue(scopesOf("- a\nlazy\n- b\n", "- b").contains(bullet))
        assertFalse(scopesOf("- a\n^ lazy\n", "^").contains(caption))
        assertFalse(scopesOf("- a\nlazy\n^ lazy too\n", "^").contains(caption))
        assertTrue(scopesOf("- a\n  b\n  - c\n", "- c").contains(bullet))
    }

    @Test
    fun aCaptionLineAfterAHeadingIsText() {
        assertFalse(scopesOf("# Head\n^ text\n", "^").contains(caption))
        assertFalse(scopesOf("# Head\n\n^ text\n", "^").contains(caption))
        assertFalse(scopesOf("# Head\n^ text\n- held\n", "- held").contains(bullet))
        assertTrue(scopesOf("# Head\n- item\n", "-").contains(bullet))
    }

    @Test
    fun blockMarkersBehindAQuotePrefix() {
        assertTrue(scopesOf("> - item\n", "-").contains("keyword.control.list.begin.carve"))
        assertTrue(scopesOf("> 1. item\n", "1.").contains("keyword.control.list.begin.carve"))
        assertTrue(scopesOf("> - [x] done\n", "[x]").contains("keyword.control.task-list.carve"))
        assertTrue(scopesOf("> :: term\n", "::").contains("markup.list.definition.term.carve"))
        assertTrue(scopesOf("> : description\n", ":").contains("markup.list.definition.carve"))
        assertTrue(scopesOf("> ::: note\n> body\n> :::\n", "note").contains("entity.name.type.div.carve"))
        assertTrue(scopesOf("> > - nested\n", "-").contains("keyword.control.list.begin.carve"))
    }

    @Test
    fun aTableRowNeedsItsClosingPipe() {
        assertFalse(scopesOf("| a | b\n", "| b").contains(tableRow))
        assertFalse(scopesOf("> | a | b\n", "| b").contains(tableRow))
        assertFalse(scopesOf("| a | b | %% note\n", "| b").contains(tableRow))
        assertTrue(scopesOf("| a | b |\n", "| b").contains(tableRow))
        assertTrue(scopesOf("| a | b |{.x}\n", "| b").contains(tableRow))
        assertTrue(scopesOf("> | a | b |\n", "| b").contains(tableRow))
        assertTrue(scopesOf("- | a | b |\n", "| b").contains(tableRow))
    }

    @Test
    fun anAdmonitionOpenerWithAFreeTitleIsScoped() {
        assertTrue(scopesOf("::: note Title here\nx\n:::\n", "note").contains("entity.name.type.div.carve"))
        assertTrue(scopesOf("::: note Title here\nx\n:::\n", "Title").contains(div))
        assertFalse(scopesOf("::: {.sidebar}\nx\n:::\n", "::: {").contains(div))
    }

    @Test
    fun aQuotedFenceScopesItsQuoteMarkerAndItsLanguage() {
        val src = "> ```js\n> let a = \"s\";\n> ```\nafter\n"
        val withJs = CarveTextMateTokenizer.grammarsTogether(File("src/test/resources/grammars/source.js.tmLanguage.json"))
        val marker = scopesOf(src.substringBefore("let"), "> ", withJs)
        assertTrue("the body line's `>` is a quote marker", scopesOf(src, "> let", withJs).contains("keyword.control.quote.carve"))
        assertTrue(marker.contains("keyword.control.quote.carve"))
        val body = scopesOf(src, "let", withJs)
        assertTrue("the body is JavaScript: $body", body.contains("meta.embedded.block.javascript") && body.contains("keyword.control.stub.js"))
        assertFalse(scopesOf(src, "let", withJs).contains("keyword.control.quote.carve"))
        assertTrue(scopesOf(src, "> ```\n", withJs).contains("keyword.control.raw.end.carve"))
        assertFalse(scopesOf(src, "after", withJs).contains("markup.raw.block.fenced.code.carve"))
    }
}
