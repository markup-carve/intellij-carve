package org.markupcarve.carve.highlight

import com.intellij.lexer.LexerBase
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateElementType
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope
import org.junit.Assert.assertEquals
import org.junit.Test
import org.markupcarve.carve.corpus.CarveTextMateTokenizer

class CarveHighlightScannerTest {

    @Test
    fun `only bare and braced highlight bodies receive the mark`() {
        val source = "plain =x= and {=y=} and `=code=` and {~old~>new~} and {=a {% note %} b=}"
        val tokens = CarveTextMateTokenizer.tokenize(source)
        val highlighter = object : SyntaxHighlighterBase() {
            override fun getHighlightingLexer() = SnapshotLexer(tokens.map { it.text to it.scope })
            override fun getTokenHighlights(tokenType: IElementType?) = TextAttributesKey.EMPTY_ARRAY
        }

        val marked = CarveHighlightScanner.scan(source, highlighter)
            .map { source.substring(it.startOffset, it.endOffset) }

        assertEquals(listOf("x", "y", "a ", " b"), marked)
    }

    private class SnapshotLexer(private val tokens: List<Pair<String, String>>) : LexerBase() {
        private lateinit var input: CharSequence
        private var index = 0
        private var offset = 0

        override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
            input = buffer
            index = 0
            offset = startOffset
        }

        override fun getState() = 0

        override fun getTokenType(): IElementType? = tokens.getOrNull(index)?.second?.let { scope ->
            val chain = scope.split(' ').fold(TextMateScope.EMPTY) { parent, name -> parent.add(name) }
            TextMateElementType(chain)
        }

        override fun getTokenStart() = offset

        override fun getTokenEnd() = offset + (tokens.getOrNull(index)?.first?.length ?: 0)

        override fun advance() {
            offset = tokenEnd
            index++
        }

        override fun getBufferSequence() = input

        override fun getBufferEnd() = input.length
    }
}
