package org.markupcarve.carve.corpus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarveHeadingInlineTest {
    private val prefixes = listOf("# ", "###### ", "> # ", "> > # ", ">   # ", "- # ", "- [x] # ", "1. # ", "- - # ")

    private fun covered(source: String, scope: String): String =
        CarveTextMateTokenizer.tokenize(source)
            .filter { scope in it.scope.split(' ') }
            .joinToString("") { it.text }

    @Test
    fun codeOwnsPercentRunsInEveryHeadingPath() {
        for (prefix in prefixes) {
            for (ticks in listOf("`", "``")) {
                val source = "${prefix}a ${ticks}x %% b${ticks} c %% hidden\n"
                assertEquals(source, "x %% b", covered(source, "markup.raw.inline.content.carve"))
                assertEquals(source, "%% hidden", covered(source, "comment.line.percent.carve"))
                assertTrue(source, covered(source, "markup.heading.carve").contains(" c "))
            }
        }
    }

    @Test
    fun genuineCommentsAndAdjacentPercentRunsStayDistinct() {
        for (prefix in prefixes) {
            assertEquals("%% hidden", covered("${prefix}a\t%% hidden\n", "comment.line.percent.carve"))
            assertEquals("", covered("${prefix}a%% literal\n", "comment.line.percent.carve"))
            assertEquals("", covered("${prefix}a `x %% b` c\n", "comment.line.percent.carve"))
            assertEquals("x %% b", covered("${prefix}a `x %% b\n", "markup.raw.inline.content.carve"))
        }
    }

    @Test
    fun closedInlineCommentsLeaveFollowingHeadingTextVisible() {
        for (prefix in prefixes) {
            for (comment in listOf("{%%}", "{% hidden %}")) {
                val source = "${prefix}a $comment b %% hidden\n"
                assertEquals(source, comment, covered(source, "comment.block.inline.carve"))
                assertEquals(source, "%% hidden", covered(source, "comment.line.percent.carve"))
                assertTrue(source, covered(source, "entity.name.section.carve").contains(" b "))
            }
        }
    }

    @Test
    fun headingsRetainOtherInlineScopes() {
        val examples = listOf(
            "*bold*" to "markup.bold.carve",
            "/italic/" to "markup.italic.carve",
            "^[note]" to "meta.footnote.inline.carve",
            "{+added+}" to "markup.inserted.critic.carve",
            "[label](https://example.com)" to "meta.link.inline.carve",
            "<https://example.com>" to "markup.underline.link.carve",
            "@name" to "entity.name.mention.carve",
            "#tag" to "entity.name.tag.hashtag.carve",
            "`raw`{=html}" to "markup.raw.inline.passthrough.carve",
            "!`literal %% text`" to "markup.raw.inline.literal.carve",
            "\$`x %% y`" to "markup.other.math.inline.carve",
            "\\*escaped" to "constant.character.escape.carve",
            "</#id>" to "markup.underline.link.cross-reference.carve",
            "[^note]" to "entity.name.reference.footnote.carve",
            "{# editorial #}" to "comment.block.critic.carve",
            "[span]{.class}" to "meta.attributes.carve",
        )
        for (prefix in prefixes) {
            for ((text, scope) in examples) {
                val source = "$prefix$text\n"
                assertTrue("$source must carry $scope", covered(source, scope).isNotEmpty())
                assertTrue(source, covered(source, "markup.heading.carve").contains(text))
            }
        }
    }

    @Test
    fun inlineRegionsStayOnTheirHeadingLine() {
        for (prefix in prefixes) {
            for (text in listOf("`unclosed", "{% unclosed", "*unclosed", "/unclosed", "{=unclosed", "!`unclosed", "*a `unclosed*", "^[unclosed", "{+unclosed", "{#unclosed", "*a {% unclosed")) {
                val tokens = CarveTextMateTokenizer.tokenize("$prefix$text\nplain\n")
                assertFalse(tokens.toString(), tokens.filter { it.text.contains("plain") }
                    .any { "markup.heading.carve" in it.scope.split(' ') || "markup.raw.inline.carve" in it.scope.split(' ') })
            }
        }
    }

    @Test
    fun backtickRunsAreNeverMatchedFromASuffix() {
        for (prefix in listOf("a ", "# a ", "> # a ", "- # a ")) {
            val source = "${prefix}```x`` b"
            assertEquals(source, "x`` b", covered(source, "markup.raw.inline.content.carve"))
            assertEquals(source, "```", covered(source, "keyword.control.raw.carve"))
        }
    }

    @Test
    fun emptyCommentsWorkInParagraphsAndHeadings() {
        for (prefix in listOf("a ", "# a ")) {
            assertEquals("{%%}", covered("${prefix}{%%} b", "comment.block.inline.carve"))
        }
    }

    @Test
    fun headingTextDoesNotRunBlockOrTrailingAttributeRules() {
        for (prefix in prefixes) {
            for (text in listOf("> text", "---", "{#id .class}", "^ caption", "a <1>")) {
                val tokens = CarveTextMateTokenizer.tokenize("$prefix$text\n")
                val payload = tokens.filter { "entity.name.section.carve" in it.scope.split(' ') }
                assertFalse(tokens.toString(), payload.any {
                    it.scope.contains("meta.attributes.carve") || it.scope.contains("meta.separator.thematic-break.carve") ||
                        it.scope.contains("markup.table.caption.carve") || it.scope.contains("markup.callout.marker.carve")
                })
            }
        }
    }
}
