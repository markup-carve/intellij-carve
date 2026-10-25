package org.markupcarve.carve.editor

import com.intellij.codeInsight.highlighting.BraceMatchingUtil
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.lang.reflect.Proxy

/** Commenter, brace matching and bracket auto-close, through the real editor actions. */
class CarveEditorSupportTest : BasePlatformTestCase() {

    private fun configure(text: String) = myFixture.configureByText("doc.crv", text)

    fun testLineCommentTogglesAPercentPrefix() {
        configure("<caret>Some text\n")
        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_LINE)
        myFixture.checkResult("%% Some text\n")
        // The action moves the caret to the next line.
        myFixture.editor.caretModel.moveToOffset(0)
        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_LINE)
        myFixture.checkResult("Some text\n")
    }

    fun testCommentingALineThatStartsWithPercentCannotOpenACommentFence() {
        configure("<caret>%x\n")
        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_LINE)
        myFixture.checkResult("%% %x\n")
    }

    fun testBlockCommentUsesTheBracedForm() {
        configure("a <selection>b</selection> c\n")
        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_BLOCK)
        myFixture.checkResult("a {%b%} c\n")
    }

    private fun matchedOffset(text: String): Int {
        configure(text)
        return BraceMatchingUtil.getMatchedBraceOffset(myFixture.editor, true, myFixture.file)
    }

    fun testParenthesesInProseMatch() {
        val text = "Text (a [b] c) end\n"
        assertEquals(text.indexOf(')'), matchedOffset(text.replaceFirst("(", "<caret>(")))
    }

    fun testBracketsAndBracesMatch() {
        assertEquals("x [a (b) c] y".indexOf(']'), matchedOffset("x <caret>[a (b) c] y\n"))
        assertEquals("p {.c #d} q".indexOf('}'), matchedOffset("p <caret>{.c #d} q\n"))
    }

    fun testAnotherBracketKindDoesNotClose() {
        assertEquals("( ] )".lastIndexOf(')'), matchedOffset("<caret>( ] )\n"))
    }

    fun testAnEscapedBracketIsNotABrace() {
        assertEquals("(a \\) b)".lastIndexOf(')'), matchedOffset("<caret>(a \\) b)\n"))
    }

    fun testBracketsSplitIntoTheirOwnTokensWithoutGaps() {
        val text = "Text (a b) and [link](u) {.c}\n"
        configure(text)
        val it = (myFixture.editor as EditorEx).highlighter.createIterator(0)
        var expected = 0
        while (!it.atEnd()) {
            assertEquals(expected, it.start)
            assertTrue(it.end > it.start)
            val piece = text.substring(it.start, it.end)
            if (piece.any { c -> c in "()[]{}" }) assertEquals(1, piece.length)
            expected = it.end
            it.advance()
        }
        assertEquals(text.length, expected)
    }

    fun testHighlightingWithoutAnEditorDocumentKeepsTheTokenBounds() {
        val inner = Proxy.newProxyInstance(
            HighlighterIterator::class.java.classLoader,
            arrayOf(HighlighterIterator::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "atEnd" -> false
                "getStart" -> 2
                "getEnd" -> 7
                "getDocument" -> null
                else -> error("Unexpected iterator method: ${method.name}")
            }
        } as HighlighterIterator
        val iterator = BracketSplittingIterator(inner, 2)
        assertEquals(2, iterator.start)
        assertEquals(7, iterator.end)
    }

    fun testOpeningBracketsAutoClose() {
        configure("<caret>\n")
        myFixture.type("(")
        myFixture.checkResult("(<caret>)\n")
        myFixture.type("x)")
        myFixture.checkResult("(x)<caret>\n")
    }

    fun testSquareBracketAutoCloses() {
        configure("<caret>\n")
        myFixture.type("[")
        myFixture.checkResult("[<caret>]\n")
    }

    fun testCurlyBraceAutoCloses() {
        configure("<caret>\n")
        myFixture.type("{")
        myFixture.checkResult("{<caret>}\n")
    }

    fun testBackspaceRemovesAnEmptyPair() {
        configure("<caret>\n")
        myFixture.type("(")
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
        myFixture.checkResult("<caret>\n")
    }

    fun testEmphasisDelimitersDoNotAutoClose() {
        configure("<caret>\n")
        myFixture.type("/*_")
        myFixture.checkResult("/*_<caret>\n")
    }
}
