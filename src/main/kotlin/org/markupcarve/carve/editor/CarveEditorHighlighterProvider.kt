package org.markupcarve.carve.editor

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.editor.highlighter.HighlighterClient
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileTypes.EditorHighlighterProvider
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.textmate.language.syntax.highlighting.TextMateEditorHighlighterProvider

/**
 * The TextMate editor highlighter, with every `()[]{}` character reported as a token of
 * its own. Brace matching works on highlighter tokens, and TextMate gives a run of prose
 * one token, so without the split [CarveBraceMatcher] never sees a bracket. Lexing,
 * incremental updates and colours stay entirely with the TextMate highlighter.
 */
class CarveEditorHighlighterProvider : EditorHighlighterProvider {
    override fun getEditorHighlighter(
        project: Project?,
        fileType: FileType,
        virtualFile: VirtualFile?,
        colors: EditorColorsScheme,
    ): EditorHighlighter =
        BracketSplittingHighlighter(
            TextMateEditorHighlighterProvider().getEditorHighlighter(project, fileType, virtualFile, colors),
        )
}

internal class BracketSplittingHighlighter(val delegate: EditorHighlighter) : EditorHighlighter, PrioritizedDocumentListener {

    override fun createIterator(startOffset: Int): HighlighterIterator =
        BracketSplittingIterator(delegate.createIterator(startOffset), startOffset)

    override fun setText(text: CharSequence) = delegate.setText(text)
    override fun setEditor(editor: HighlighterClient) = delegate.setEditor(editor)
    override fun setColorScheme(scheme: EditorColorsScheme) = delegate.setColorScheme(scheme)
    override fun beforeDocumentChange(event: DocumentEvent) = delegate.beforeDocumentChange(event)
    override fun documentChanged(event: DocumentEvent) = delegate.documentChanged(event)
    override fun bulkUpdateStarting(document: Document) = delegate.bulkUpdateStarting(document)
    override fun bulkUpdateFinished(document: Document) = delegate.bulkUpdateFinished(document)
    override fun getPriority(): Int = (delegate as? PrioritizedDocumentListener)?.priority ?: 0
    override fun toString(): String = "BracketSplittingHighlighter($delegate)"
}

/** Walks the delegate's tokens, cutting each one around the bracket characters it holds. */
internal class BracketSplittingIterator(
    private val inner: HighlighterIterator,
    startOffset: Int,
) : HighlighterIterator {

    // Piece boundaries of the current inner token: [bounds[i], bounds[i + 1]).
    private var bounds = IntArray(0)
    private var piece = 0

    init {
        if (!inner.atEnd()) {
            computeBounds()
            piece = 0
            while (piece < bounds.size - 2 && bounds[piece + 1] <= startOffset) piece++
        }
    }

    private fun computeBounds() {
        val start = inner.start
        val end = inner.end
        val text = inner.document.immutableCharSequence
        val cuts = ArrayList<Int>()
        cuts += start
        for (i in start until minOf(end, text.length)) {
            if (text[i] in CarveBraceMatcher.BRACKETS) {
                if (cuts.last() != i) cuts += i
                cuts += i + 1
            }
        }
        if (cuts.size == 1 || cuts.last() != end) cuts += end
        bounds = cuts.toIntArray()
    }

    override fun getStart(): Int = bounds[piece]
    override fun getEnd(): Int = bounds[piece + 1]
    override fun getTextAttributes(): TextAttributes = inner.textAttributes
    override fun getTextAttributesKeys(): Array<TextAttributesKey> = inner.textAttributesKeys
    override fun getTokenType(): IElementType = inner.tokenType
    override fun getDocument(): Document = inner.document
    override fun atEnd(): Boolean = inner.atEnd()

    override fun advance() {
        if (piece < bounds.size - 2) {
            piece++
            return
        }
        inner.advance()
        if (!inner.atEnd()) {
            computeBounds()
            piece = 0
        }
    }

    override fun retreat() {
        if (piece > 0) {
            piece--
            return
        }
        inner.retreat()
        if (!inner.atEnd()) {
            computeBounds()
            piece = bounds.size - 2
        }
    }
}
