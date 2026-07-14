package org.markupcarve.carve

import org.jetbrains.plugins.textmate.language.syntax.highlighting.TextMateTheme
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins how our TextMate scopes actually paint, using the real platform theme rather than a
 * hand-copied table. Two things make this easy to get wrong, and both did:
 *
 * 1. **The lookup is a substring match, not an exact one.** `TextMateHighlighter` walks the
 *    theme's rules and keeps those the token's scope *contains*. Probing
 *    `TextMateTheme.getTextAttributesKey(scope)` directly is an exact map lookup and answers
 *    "TEXT" for every `...carve` scope, which is misleading.
 * 2. **Resolving to a key is not the same as being visible.** `keyword.operator` resolves to
 *    `DEFAULT_OPERATION_SIGN`, which IntelliJ paints in the *plain text colour* - so markers
 *    scoped there look exactly like unhighlighted text. `keyword` resolves to
 *    `DEFAULT_KEYWORD` (bold, coloured), which is what a structural marker needs.
 */
class TextMateColorResolutionTest {

    /** The rule the highlighter would settle on: the most specific one contained in the scope. */
    private fun resolvedKey(scope: String): String? =
        TextMateTheme.INSTANCE.rules
            .map { it.toString() }
            .filter { scope.contains(it) }
            .maxByOrNull { it.length }
            ?.let { TextMateTheme.INSTANCE.getTextAttributesKey(it)?.externalName }

    @Test
    fun structuralMarkersPaintDistinctlyFromPlainText() {
        val markers = listOf(
            "keyword.control.list.continuation.carve", // + continuation
            "keyword.control.list.begin.carve", // bullets, ::
            "keyword.control.div.carve", // :::
            "keyword.control.separator.table.carve", // table |
            "keyword.control.heading.carve", // #
            "entity.name.tag.definition.term.carve", // definition-list term
            "entity.other.attribute-name.id.carve", // {#id}
            "variable.parameter.tag.carve", // #hashtag
            "string.quoted.double.attribute.carve", // attribute value
            "markup.heading.carve",
        )

        val invisible = markers.filter { resolvedKey(it) in PLAIN_KEYS }
        assertTrue(
            "These scopes resolve to a key IntelliJ paints in the plain-text colour, so the " +
                "marker stays invisible no matter how well the grammar tokenizes it:\n" +
                invisible.joinToString("\n") { "  $it -> ${resolvedKey(it)}" },
            invisible.isEmpty(),
        )
    }

    @Test
    fun keywordOperatorIsNotUsedForMarkers() {
        // Guards the regression this test was written for: keyword.operator looks like plain text.
        val grammar = javaClass.classLoader
            .getResourceAsStream("textmate/carve.tmLanguage.json")!!
            .bufferedReader().readText()

        assertTrue(
            "keyword.operator.* resolves to DEFAULT_OPERATION_SIGN, which IntelliJ paints in the " +
                "plain text colour - use keyword.control.* (DEFAULT_KEYWORD) for markers instead.",
            !grammar.contains("keyword.operator."),
        )
    }

    private companion object {
        /** Keys IntelliJ paints in (or indistinguishably close to) the plain text colour. */
        val PLAIN_KEYS = setOf(null, "TEXT", "DEFAULT_OPERATION_SIGN", "DEFAULT_DOT", "DEFAULT_IDENTIFIER")
    }
}
