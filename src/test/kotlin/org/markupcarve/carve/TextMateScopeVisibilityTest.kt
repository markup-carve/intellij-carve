package org.markupcarve.carve

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A TextMate scope only gets a color in IntelliJ if it starts with one of the generic roots
 * that `TextMateDefaultColorsProvider` maps. Everything else renders as plain text no matter
 * how correctly the grammar tokenizes it - `TextMateTheme` only adds *language-specific*
 * entries (`.css`, `.coffee`, ...) which can never match a `.carve` scope.
 *
 * That is precisely how the structural markers (`+` continuation, `::` / `:` definition
 * lists, `:::` divs) ended up looking like literal text: they were correctly scoped under
 * `markup.list.*` / `punctuation.definition.*`, neither of which IntelliJ maps.
 *
 * This test pins the rule: every scope carrying a *marker* must sit on a mapped root.
 */
class TextMateScopeVisibilityTest {

    @Test
    fun everyMarkerScopeUsesAnIntelliJMappedRoot() {
        val grammar = javaClass.classLoader
            .getResourceAsStream("textmate/carve.tmLanguage.json")!!
            .bufferedReader()
            .readText()

        val scopes = SCOPE_PATTERN.findAll(grammar)
            .map { it.groupValues[1] }
            .toSortedSet()

        assertTrue("no scopes found - grammar resource missing?", scopes.isNotEmpty())

        val invisible = scopes
            .filterNot { scope -> ALLOWED_UNCOLORED.containsMatchIn(scope) }
            .filterNot { scope -> MAPPED_ROOTS.any { scope == it || scope.startsWith("$it.") } }

        assertTrue(
            "These scopes are not on a root IntelliJ colors, so they will render as plain " +
                "text. Re-scope them onto a mapped root (structural markers -> " +
                "keyword.operator.*):\n" + invisible.joinToString("\n") { "  $it" },
            invisible.isEmpty(),
        )
    }

    private companion object {
        val SCOPE_PATTERN = Regex(""""name"\s*:\s*"([a-z][^"]*\.carve)"""")

        /** Generic roots mapped by IntelliJ's TextMateDefaultColorsProvider. */
        val MAPPED_ROOTS = listOf(
            "comment.block", "comment.documentation", "comment.line",
            "constant.character.entity", "constant.character.escape",
            "constant.number", "constant.numeric",
            "entity.name", "entity.name.class", "entity.name.function",
            "entity.other.attribute-name",
            "invalid.deprecated",
            "keyword.operator",
            "markup.bold", "markup.changed", "markup.deleted", "markup.heading",
            "markup.inserted", "markup.italic", "markup.underline",
            "meta.tag",
            "punctuation.definition.tag",
            "storage.type",
            "support.function", "support.type",
            "variable.parameter",
        )

        /**
         * Allowed to be uncolored: `meta.*` are structural containers (their children carry
         * the color, per TextMate convention), and these content scopes have no mappable root
         * in IntelliJ at all - only their delimiters can be colored.
         */
        val ALLOWED_UNCOLORED = Regex(
            "^(meta\\.|string\\.|markup\\.(raw|quote|subscript|superscript|other\\.math|callout\\.annotation))",
        )
    }
}
