package org.markupcarve.carve.corpus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The bundled grammar classifies the shared block battery the way every other
 * Carve grammar does.
 *
 * This grammar is a port of carve-grammars' TextMate one, and the two have
 * drifted. The last time, the same rule was fixed in six copies and this one
 * silently missed it: the PR merged, the changelog said so, and the grammar
 * kept colouring `-`, `1.` and `::` as markers. It was found by someone
 * comparing the copies by hand (#46).
 *
 * `battery/block-battery.json` is that comparison made routine - carve-grammars'
 * table, vendored, with a drift check keeping the copy honest. `want` is what
 * carve-rs renders.
 */
class CarveBlockBatteryTest {

    private data class Shape(val src: String, val want: String, val why: String?)

    /**
     * Reduce a token's scopes to one block classification, the same reduction
     * carve-grammars uses. The alternatives are not cosmetic: Prism spells it
     * `definition-term` and TextMate `list.definition.term`, and matching only
     * one form let a negative pass while the grammar still highlighted.
     */
    private fun classify(scopes: String): String = when {
        Regex("heading|section").containsMatchIn(scopes) -> "heading"
        scopes.contains("caption") -> "caption"
        scopes.contains("quote") -> "quote"
        Regex("definition[.-]term|list\\.definition").containsMatchIn(scopes) -> "deflist"
        Regex("list|bullet").containsMatchIn(scopes) -> "list"
        else -> "none"
    }

    /**
     * A trailing line keeps the shape off the last line of the document, where a
     * `$`-anchored rule behaves differently. Only the first line's tokens are
     * read - the battery is about classification at column 1 - by consuming
     * tokens until their combined text covers that line.
     */
    private fun classifyLine(src: String): String {
        val scopes = StringBuilder()
        var consumed = 0
        for (token in CarveTextMateTokenizer.tokenize("$src\nafter\n")) {
            if (consumed >= src.length) break
            scopes.append(token.scope).append(' ')
            consumed += token.text.length
        }
        return classify(scopes.toString())
    }

    private fun shapes(): List<Shape> {
        val file = File("src/test/resources/battery/block-battery.json")
        assertTrue(
            "Vendored battery missing at ${file.path}; copy it from carve-grammars.",
            file.isFile,
        )
        // Hand-parsed rather than pulling in a JSON library for one file: the
        // shape is fixed and generated, and a dependency here would be the only
        // one in the test source set.
        val text = file.readText()
        val entries = Regex("""\{[^{}]*"src"[^{}]*}""").findAll(text)
        return entries.map { match ->
            val body = match.value
            fun field(name: String): String? =
                Regex("\"$name\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                    .find(body)?.groupValues?.get(1)
                    ?.replace("\\t", "\t")
                    ?.replace("\\n", "\n")
                    ?.replace("\\\"", "\"")
                    ?.replace("\\\\", "\\")
            Shape(
                src = field("src") ?: error("battery entry without src: $body"),
                want = field("want") ?: error("battery entry without want: $body"),
                why = field("why"),
            )
        }.toList()
    }

    @Test
    fun bundledGrammarAgreesWithTheSharedBattery() {
        val all = shapes()
        assertTrue("Vendored battery parsed as empty", all.size >= 25)

        val failures = all.mapNotNull { shape ->
            val got = classifyLine(shape.src)
            if (got == shape.want) {
                null
            } else {
                "  ${shape.src.replace("\t", "\\t")}: want=${shape.want} got=$got" +
                    (shape.why?.let { "   ($it)" } ?: "")
            }
        }

        assertEquals(
            "\n" + failures.joinToString("\n") +
                "\n\nThe battery records what carve-rs renders. " +
                "Change the grammar, not the battery.\n",
            emptyList<String>(),
            failures,
        )
    }
}
