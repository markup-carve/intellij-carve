package org.markupcarve.carve.lang

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.lexer.LexerBase
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.PsiFileBase
import org.markupcarve.carve.CarveFileType
import org.markupcarve.carve.CarveLanguage

/**
 * A deliberately minimal PSI for Carve files.
 *
 * Carve is highlighted by the TextMate engine, not a hand-written lexer, so there is no need
 * to build a real syntax tree. But an [com.intellij.lang.annotation.Annotator] only runs on
 * files whose PSI language is Carve, and without any ParserDefinition a `.crv` file is treated
 * as plain text. So this lexes the whole file into one token and wraps it in a single PSI node
 * - just enough for the marker annotator ([org.markupcarve.carve.highlight.CarveAnnotator]) to
 * receive the file and colour it. TextMate keeps providing the base editor highlighting; the
 * annotator layers marker colours on top.
 */
class CarveParserDefinition : ParserDefinition {

    override fun createLexer(project: Project?): Lexer = CarveLexer()

    override fun createParser(project: Project?): PsiParser = PsiParser { root, builder ->
        val mark = builder.mark()
        while (!builder.eof()) builder.advanceLexer()
        mark.done(root)
        builder.treeBuilt
    }

    override fun getFileNodeType(): IFileElementType = FILE

    override fun getCommentTokens(): TokenSet = TokenSet.EMPTY

    override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY

    override fun createElement(node: ASTNode): PsiElement = ASTWrapperPsiElement(node)

    override fun createFile(viewProvider: FileViewProvider): PsiFile = CarvePsiFile(viewProvider)

    companion object {
        val CONTENT: IElementType = IElementType("CARVE_CONTENT", CarveLanguage)
        val FILE: IFileElementType = IFileElementType(CarveLanguage)
    }
}

/** Emits the entire file as a single [CarveParserDefinition.CONTENT] token. */
private class CarveLexer : LexerBase() {
    private var buffer: CharSequence = ""
    private var startOffset = 0
    private var endOffset = 0
    private var done = false

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.startOffset = startOffset
        this.endOffset = endOffset
        this.done = startOffset >= endOffset
    }

    override fun getState(): Int = 0
    override fun getTokenType(): IElementType? = if (done) null else CarveParserDefinition.CONTENT
    override fun getTokenStart(): Int = startOffset
    override fun getTokenEnd(): Int = endOffset
    override fun advance() {
        done = true
        startOffset = endOffset
    }

    override fun getBufferSequence(): CharSequence = buffer
    override fun getBufferEnd(): Int = endOffset
}

/** The PSI file node for `.crv`; its language is Carve, so Carve annotators run on it. */
class CarvePsiFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, CarveLanguage) {
    override fun getFileType() = CarveFileType
    override fun toString(): String = "Carve File"
}
