package org.markupcarve.carve.corpus

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A bare delimiter never pairs across a link destination or an autolink (PART 9
 * section 9 E2a), so `/see [x](http://a.b/c) now/` is one italic run. Expected
 * readings come from the spec's layout oracle. Parentheses nested three deep are
 * not recognized, by design.
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
