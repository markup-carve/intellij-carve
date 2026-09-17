package org.markupcarve.carve.corpus

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A bare delimiter never pairs across a link destination or an autolink (PART 9
 * section 9 E2a), so `/see [x](http://a.b/c) now/` is one italic run. Expected
 * readings come from the spec's layout oracle. Parentheses nested three deep and
 * labels nested five deep are not recognized, by design.
 */
class CarveOpaqueDestinationTest {

    /** The text of every token carrying [scope], delimiters left out. */
    private fun covered(src: String, scope: String): String =
        CarveTextMateTokenizer.tokenize(src)
            .filter { token ->
                val segments = token.scope.split(' ')
                scope in segments && segments.none { it.startsWith("keyword.control.") }
            }
            .joinToString("") { it.text }

    private val runs = listOf(
        "/" to "markup.italic.carve",
        "_" to "markup.underline.text.carve",
        "~" to "markup.deleted.carve",
        "=" to "markup.changed.carve",
        "*" to "markup.bold.carve",
    )

    private fun destinations(d: String) = listOf(
        "[x](http://a$d.b/c)",
        "[x](http://a.b/c$d)",
        "<http://a${d}b/c>",
        "<http://a.b/c$d>",
        "![x](a.png \"t${d}u\")",
        "![x](a.png 't${d}u')",
        "![x](a.png \"t$d\")",
        "![x](a.png 't$d')",
        "[x](foo(bar)${d}baz)",
        "[x](foo(bar$d))",
        "[x](foo\\)x${d}y)",
        "[x](foo(bar(\\x))${d}y)",
        "<${"a".repeat(40)}:x${d}y>",
        "<${"a".repeat(40)}:x$d>",
        "[](a$d)",
        "[x\\]](a$d)",
        "[x `]` y](a$d)",
        "[x {# ] #} y](a$d)",
        "[a [b [c]]](a$d)",
        "[x](a \"t\\\\\"$d\")",
        "[x](a\u00a0b$d)",
        "[x](a\u000cb$d)",
        "<x:\u00e9$d>",
        "<x:a${String(Character.toChars(0x1f600))}$d>",
    )

    /**
     * Shapes the spec does not read as a destination or an autolink, so the run
     * closes at the delimiter inside them (markup-carve/carve-grammars#454).
     */
    private fun closedDestinations(d: String) = listOf(
        "](a$d)",
        "\\[x](a$d)",
        "<a@b$d>",
        "<a@b.c$d>",
        "<x@a.b$d>",
        "[x]( \"t$d\")",
        "[x](a  \"t$d\")",
        "[x](a  't$d')",
        "[x](a\t\"t$d\")",
        "[x](a\\ b$d)",
        "<x:a\"$d>",
        "<x:a|$d>",
        "<x:a\u200b$d>",
        "<x:a${String(Character.toChars(0x110bd))}$d>",
    )

    @Test
    fun aDelimiterInsideTheDestinationDoesNotCloseTheRun() {
        val wrong = runs.flatMap { (d, scope) ->
            destinations(d).map { "${d}see $it now$d" to "see $it now" }
                .filter { (src, expected) -> covered(src, scope) != expected }
                .map { it.first }
        }
        assertEquals(emptyList<String>(), wrong)
    }

    @Test
    fun aShapeTheSpecDoesNotReadAsADestinationClosesTheRun() {
        val wrong = runs.flatMap { (d, scope) ->
            closedDestinations(d).map { "${d}see $it now$d" to "see ${it.substring(0, it.lastIndexOf(d))}" }
                .filter { (src, expected) -> covered(src, scope) != expected }
                .map { it.first }
        }
        assertEquals(emptyList<String>(), wrong)
    }

    @Test
    fun anEmailAutolinkTakesUnicodeLettersAndUnderscores() {
        for (address in listOf("<\u00e4_@b.cd>", "<a@b_c.de>")) {
            assertEquals("see $address now", covered("_see $address now_", "markup.underline.text.carve"))
        }
    }

    @Test
    fun boldItalicDoesNotReadAnEscapedBracketAsALabel() {
        assertEquals("see \\[x", covered("/*see \\[x*/](a) now*/", "markup.bold.italic.carve"))
    }

    @Test
    fun anEmailAutolinkIsOpaqueToo() {
        assertEquals("see <me_@x.y> now", covered("_see <me_@x.y> now_", "markup.underline.text.carve"))
    }

    /** Bold is left out: its begin/end rule opens without seeing a closer. */
    @Test
    fun aCloserInsideTheDestinationOpensNoRun() {
        val wrong = runs.dropLast(1).map { (d, scope) -> "${d}see [x](a$d) now" to scope }
            .filter { (src, scope) -> covered(src, scope).isNotEmpty() }
            .map { it.first }
        assertEquals(emptyList<String>(), wrong)
    }

    @Test
    fun boldItalicKeepsADelimiterInsideTheDestination() {
        assertEquals("see [x](a*/b) now", covered("/*see [x](a*/b) now*/", "markup.bold.italic.carve"))
    }

    @Test
    fun boldItalicDoesNotCloseInsideTheDestination() {
        assertEquals("", covered("/*see [x](a*/b) now", "markup.bold.italic.carve"))
    }
}
