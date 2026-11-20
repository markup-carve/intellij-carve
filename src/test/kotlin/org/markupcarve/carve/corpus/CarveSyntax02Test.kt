package org.markupcarve.carve.corpus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Carve 0.2 source changes, asserted on the TextMate grammar. Each case is a spec example or
 * upgrade entry from markup-carve/carve `next2` (`spec/0.2/UPGRADING.md`, `syntax-blocks.md`,
 * `syntax-inline.md`); the readings were checked against the 0.2 reference engine.
 */
class CarveSyntax02Test {

    private val heading = "markup.heading.carve"
    private val thematicBreak = "meta.separator.thematic-break.carve"
    private val code = "markup.raw.block.fenced.code.carve"
    private val comment = "comment.block.carve"

    private fun covered(src: String, scope: String): String =
        CarveTextMateTokenizer.tokenize(src).filter { it.scope.contains(scope) }.joinToString("") { it.text }

    private fun has(src: String, scope: String) = covered(src, scope).isNotEmpty()

    private fun label(src: String) = src.replace("\n", "\\n")

    @Test
    fun theZeroTwoSpellingsCarryTheirScopes() {
        for ((src, scope) in listOf(
            "= Heading\n" to heading,
            "== Heading\n" to heading,
            "***\n" to thematicBreak,
            "```js\nx\n```\n" to code,
            "a {_underlined_} b\n" to "markup.underline.text.carve",
            "{~struck~}\n" to "markup.deleted.carve",
            "{=marked=}\n" to "markup.changed.carve",
            "|={>} a |\n" to "keyword.control.table.alignment.carve",
            "a. one\nb. two\n" to "markup.list.numbered.carve",
            "text\n\n# Heading\n" to heading,
            "- item\n\n= Heading\n" to heading,
            "text\n%%%\nhidden\n%%%\n" to comment,
        )) {
            assertTrue("${label(src)} carries $scope", has(src, scope))
        }
    }

    @Test
    fun theZeroOneSpellingsAreText() {
        for ((src, scope) in listOf(
            "a\n\n---\n" to thematicBreak, // U-044
            "a\n\n___\n" to thematicBreak, // U-044
            "~~~js\nx\n~~~\n" to code, // U-116
            "_underlined_\n" to "markup.underline.text.carve", // U-065
            "~struck~\n" to "markup.deleted.carve", // U-066
            "=marked=\n" to "markup.changed.carve", // U-067
            "|=> a | b |\n" to "keyword.control.table.alignment.carve", // U-056
            "| a | b |\n|---|--:|\n" to "keyword.control.table.alignment.carve", // U-055
            "- first\n+\n> second\n" to "continuation", // U-014
            "text\n# Heading\n" to heading, // U-001
            "Some text\n> quote\n" to "keyword.control.quote.carve", // U-002
            "Some text\n[ref]: https://example.com\n" to "meta.link.reference.definition.carve", // U-005
            " [a]: /u\n" to "meta.link.reference.definition.carve", // U-039
            "- a\n\n  [r]: /u\n" to "meta.link.reference.definition.carve", // C2-B-081
        )) {
            assertFalse("${label(src)} carries no $scope", has(src, scope))
        }
    }

    @Test
    fun aCommentFenceClosesOnlyOnItsPercentRun() {
        // U-050: a closer followed by text is comment content.
        assertTrue(covered("%%%\nhidden\n%%% visible?\nafter\n%%%\n", comment).contains("after"))
        assertFalse(covered("%%%\nhidden\n%%%  \nafter\n", comment).contains("after"))
    }

    @Test
    fun aSubstitutionIsBracedStrikeText() {
        // U-069: `{~old~>new~}` renders as `<s>old~&gt;new</s>`.
        assertTrue(covered("{~old~>new~}\n", "markup.deleted.carve").contains("old"))
        assertFalse(has("{~old~>new~}\n", "markup.inserted.carve"))
    }

    @Test
    fun onlyTheZeroTwoInterruptersEndAParagraph() {
        // U-013: a table row, a fence and a star break still interrupt.
        assertTrue(has("text\n| a |\n", "markup.table.row.carve"))
        assertTrue(has("text\n***\n", thematicBreak))
        assertTrue(has("text\n```\nx\n```\n", code))
    }
}
