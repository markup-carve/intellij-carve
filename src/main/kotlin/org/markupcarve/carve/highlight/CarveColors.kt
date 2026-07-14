package org.markupcarve.carve.highlight

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey

/**
 * Editor colours for Carve's structural markers.
 *
 * TextMate provides the base highlighting for Carve files, but it collapses every structural
 * marker (`#`, `-`, `+`, `:::`, `|`, `>`, fences) onto a single "keyword" colour - so they all
 * look identical. This layer re-colours just those markers, one distinct, user-customizable
 * colour each, and leaves everything else (code, strings, comments, emphasis, links) to
 * TextMate so the file still looks familiar.
 *
 * Each default is a *semantic* [DefaultLanguageHighlighterColors] key rather than a hard-coded
 * colour, so the markers take their hue from whatever editor scheme is active (and a fence
 * marker deliberately defaults to the same colour family as code, an id to a field colour, and
 * so on - familiar by construction). Users override any of them in
 * Settings | Editor | Color Scheme | Carve.
 */
object CarveColors {
    private fun key(externalName: String, fallback: TextAttributesKey): TextAttributesKey =
        TextAttributesKey.createTextAttributesKey(externalName, fallback)

    /** `#` .. `######` heading markers. */
    val HEADING_MARKER = key("CARVE_HEADING_MARKER", DefaultLanguageHighlighterColors.KEYWORD)

    /** `-` `*` bullet and `1.` `a)` ordered-list markers. */
    val LIST_MARKER = key("CARVE_LIST_MARKER", DefaultLanguageHighlighterColors.NUMBER)

    /** Leading `+` list/table continuation marker. */
    val CONTINUATION_MARKER = key("CARVE_CONTINUATION_MARKER", DefaultLanguageHighlighterColors.METADATA)

    /** `:::` div and admonition fences. */
    val DIV_MARKER = key("CARVE_DIV_MARKER", DefaultLanguageHighlighterColors.MARKUP_TAG)

    /** `|` table cell separators. */
    val TABLE_PIPE = key("CARVE_TABLE_PIPE", DefaultLanguageHighlighterColors.OPERATION_SIGN)

    /** `>` blockquote markers. */
    val QUOTE_MARKER = key("CARVE_QUOTE_MARKER", DefaultLanguageHighlighterColors.DOC_COMMENT_TAG)

    /** ` ``` ` / `~~~` fenced-code delimiters. Defaults to the code/string colour family. */
    val FENCE_MARKER = key("CARVE_FENCE_MARKER", DefaultLanguageHighlighterColors.STRING)
}
