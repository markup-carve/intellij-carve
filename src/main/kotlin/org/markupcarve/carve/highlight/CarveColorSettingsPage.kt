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
            AttributesDescriptor("Task open ([ ], [_])", CarveColors.TASK_OPEN),
            AttributesDescriptor("Task in progress ([/])", CarveColors.TASK_PROGRESS),
            AttributesDescriptor("Task blocked ([!])", CarveColors.TASK_BLOCKED),
            AttributesDescriptor("Task done ([x])", CarveColors.TASK_DONE),
            AttributesDescriptor("Task dropped ([-])", CarveColors.TASK_DROPPED),
            AttributesDescriptor("Task deferred ([>])", CarveColors.TASK_DEFERRED),
            AttributesDescriptor("Task uncertain ([?])", CarveColors.TASK_UNCERTAIN),
            AttributesDescriptor("Continuation marker (+)", CarveColors.CONTINUATION_MARKER),
            AttributesDescriptor("Div / admonition (:::)", CarveColors.DIV_MARKER),
            AttributesDescriptor("Table boundary (|, ---)", CarveColors.TABLE_PIPE),
            AttributesDescriptor("Table header (=)", CarveColors.TABLE_HEADER),
            AttributesDescriptor("Table alignment (<, >, ~, ?)", CarveColors.TABLE_ALIGNMENT),
            AttributesDescriptor("Table span (^, <)", CarveColors.TABLE_SPAN),
            AttributesDescriptor("Blockquote marker (>)", CarveColors.QUOTE_MARKER),
            AttributesDescriptor("Code fence (``` ~~~)", CarveColors.FENCE_MARKER),
            AttributesDescriptor("Highlight (=...=)", CarveColors.HIGHLIGHT),
        )

        val TAGS = mapOf(
            "h" to CarveColors.HEADING_MARKER,
            "li" to CarveColors.LIST_MARKER,
            "taskOpen" to CarveColors.TASK_OPEN,
            "taskProgress" to CarveColors.TASK_PROGRESS,
            "taskBlocked" to CarveColors.TASK_BLOCKED,
            "taskDone" to CarveColors.TASK_DONE,
            "taskDropped" to CarveColors.TASK_DROPPED,
            "taskDeferred" to CarveColors.TASK_DEFERRED,
            "taskUncertain" to CarveColors.TASK_UNCERTAIN,
            "cont" to CarveColors.CONTINUATION_MARKER,
            "div" to CarveColors.DIV_MARKER,
            "pipe" to CarveColors.TABLE_PIPE,
            "tableHeader" to CarveColors.TABLE_HEADER,
            "tableAlignment" to CarveColors.TABLE_ALIGNMENT,
            "tableSpan" to CarveColors.TABLE_SPAN,
            "q" to CarveColors.QUOTE_MARKER,
            "fence" to CarveColors.FENCE_MARKER,
            "mark" to CarveColors.HIGHLIGHT,
        )

        val DEMO = """
            <h>#</h> Heading

            <li>-</li> a bullet item
            <li>1.</li> an ordered item
            <li>-</li> [<taskOpen> </taskOpen>] open
            <li>-</li> [<taskOpen>_</taskOpen>] open alias
            <li>-</li> [<taskProgress>/</taskProgress>] in progress
            <li>-</li> [<taskBlocked>!</taskBlocked>] blocked
            <li>-</li> [<taskDone>x</taskDone>] done
            <li>-</li> [<taskDropped>-</taskDropped>] dropped
            <li>-</li> [<taskDeferred>></taskDeferred>] deferred
            <li>-</li> [<taskUncertain>?</taskUncertain>] uncertain
            <cont>+</cont> a continuation line

            <q>></q> a blockquote

            This <mark>=highlight=</mark> stays visible on light and dark themes.

            <div>:::</div> note
            Inside an admonition.
            <div>:::</div>

            <pipe>|</pipe><tableHeader>=</tableHeader><tableAlignment>~</tableAlignment> Name <pipe>|</pipe><tableHeader>=</tableHeader> Role <pipe>|</pipe>
            <pipe>|</pipe> Ada  <pipe>|</pipe> Dev  <pipe>|</pipe>
            <pipe>|</pipe> Ada  <pipe>|</pipe><tableSpan>^</tableSpan> <pipe>|</pipe>

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
