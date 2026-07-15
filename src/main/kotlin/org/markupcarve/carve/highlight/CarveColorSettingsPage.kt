package org.markupcarve.carve.highlight

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.intellij.psi.tree.IElementType
import org.markupcarve.carve.CarveIcons
import javax.swing.Icon

/**
 * Settings | Editor | Color Scheme | Carve.
 *
 * Lets users recolour each structural marker. The demo text is annotated by matching the
 * `<name>` tags below to [CarveColors] keys, so the preview updates live as colours change.
 */
class CarveColorSettingsPage : ColorSettingsPage {

    override fun getDisplayName(): String = "Carve"

    override fun getIcon(): Icon = CarveIcons.FILE

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    // No custom lexer highlighting in this preview - all colour comes from the tagged demo text.
    override fun getHighlighter(): SyntaxHighlighter = EMPTY_HIGHLIGHTER

    override fun getDemoText(): String = DEMO

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = TAGS

    private companion object {
        val DESCRIPTORS = arrayOf(
            AttributesDescriptor("Heading marker (#)", CarveColors.HEADING_MARKER),
            AttributesDescriptor("List marker (-, *, 1.)", CarveColors.LIST_MARKER),
            AttributesDescriptor("Continuation marker (+)", CarveColors.CONTINUATION_MARKER),
            AttributesDescriptor("Div / admonition (:::)", CarveColors.DIV_MARKER),
            AttributesDescriptor("Table pipe (|)", CarveColors.TABLE_PIPE),
            AttributesDescriptor("Blockquote marker (>)", CarveColors.QUOTE_MARKER),
            AttributesDescriptor("Code fence (``` ~~~)", CarveColors.FENCE_MARKER),
        )

        val TAGS = mapOf(
            "h" to CarveColors.HEADING_MARKER,
            "li" to CarveColors.LIST_MARKER,
            "cont" to CarveColors.CONTINUATION_MARKER,
            "div" to CarveColors.DIV_MARKER,
            "pipe" to CarveColors.TABLE_PIPE,
            "q" to CarveColors.QUOTE_MARKER,
            "fence" to CarveColors.FENCE_MARKER,
        )

        val DEMO = """
            <h>#</h> Heading

            <li>-</li> a bullet item
            <li>1.</li> an ordered item
            <cont>+</cont> a continuation line

            <q>></q> a blockquote

            <div>:::</div> note
            Inside an admonition.
            <div>:::</div>

            <pipe>|</pipe> Name <pipe>|</pipe> Role <pipe>|</pipe>
            <pipe>|</pipe> Ada  <pipe>|</pipe> Dev  <pipe>|</pipe>

            <fence>```</fence> js
            const x = 1
            <fence>```</fence>
        """.trimIndent()

        val EMPTY_HIGHLIGHTER: SyntaxHighlighter = object : SyntaxHighlighterBase() {
            override fun getHighlightingLexer() = com.intellij.lexer.EmptyLexer()
            override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> =
                TextAttributesKey.EMPTY_ARRAY
        }
    }
}
