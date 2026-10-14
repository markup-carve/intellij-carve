package org.markupcarve.carve.editor

import com.intellij.codeInsight.highlighting.XmlAwareBraceMatcher
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType

/**
 * Matches `()`, `[]` and `{}` by character. Token types cannot tell the brackets apart
 * (TextMate gives prose one token type for everything), so each bracket kind is reported
 * as a "tag" and the platform only pairs brackets of the same kind.
 *
 * Relies on [CarveEditorHighlighterProvider] splitting every bracket into its own
 * one-character token.
 */
class CarveBraceMatcher : XmlAwareBraceMatcher {

    override fun getBraceTokenGroupId(tokenType: IElementType): Int = GROUP

    override fun isLBraceToken(iterator: HighlighterIterator, fileText: CharSequence, fileType: FileType): Boolean =
        bracketAt(iterator, fileText)?.let { it in OPENERS } ?: false

    override fun isRBraceToken(iterator: HighlighterIterator, fileText: CharSequence, fileType: FileType): Boolean =
        bracketAt(iterator, fileText)?.let { it in CLOSERS } ?: false

    override fun isPairBraces(tokenType1: IElementType, tokenType2: IElementType): Boolean = true

    override fun isStructuralBrace(iterator: HighlighterIterator, text: CharSequence, fileType: FileType): Boolean = false

    // Both brackets of a pair share the surrounding token type; the platform needs a non-null answer.
    override fun getOppositeBraceTokenType(type: IElementType): IElementType = type

    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean = true

    override fun getCodeConstructStart(file: PsiFile, openingBraceOffset: Int): Int = openingBraceOffset

    // Non-strict: the platform then skips a bracket of another kind instead of failing.
    override fun isStrictTagMatching(fileType: FileType, braceGroupId: Int): Boolean = false

    override fun areTagsCaseSensitive(fileType: FileType, braceGroupId: Int): Boolean = true

    override fun getTagName(fileText: CharSequence, iterator: HighlighterIterator): String? =
        when (bracketAt(iterator, fileText)) {
            '(', ')' -> "()"
            '[', ']' -> "[]"
            '{', '}' -> "{}"
            else -> null
        }

    private fun bracketAt(iterator: HighlighterIterator, text: CharSequence): Char? {
        val start = iterator.start
        if (iterator.end - start != 1 || start >= text.length) return null
        val c = text[start]
        if (c !in OPENERS && c !in CLOSERS) return null
        // An odd run of backslashes escapes the bracket.
        var backslashes = 0
        var i = start - 1
        while (i >= 0 && text[i] == '\\') { backslashes++; i-- }
        return if (backslashes % 2 == 0) c else null
    }

    companion object {
        private const val GROUP = 1
        internal const val OPENERS = "([{"
        internal const val CLOSERS = ")]}"
        internal const val BRACKETS = OPENERS + CLOSERS
    }
}
