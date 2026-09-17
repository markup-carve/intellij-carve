package org.markupcarve.carve.highlight

import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.textmate.language.syntax.highlighting.TextMateSyntaxHighlighterFactory
import org.jetbrains.plugins.textmate.language.syntax.highlighting.TextMateHighlighter
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateElementType
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope

/** Finds the body tokens of Carve highlight spans using the same TextMate lexer as the editor. */
object CarveHighlightScanner {
    private val highlighters = ThreadLocal.withInitial { mutableMapOf<String, SyntaxHighlighter>() }

    fun scan(text: String, project: Project?, file: VirtualFile): List<TextRange> {
        val key = file.extension.orEmpty().lowercase()
        val cache = highlighters.get()
        val highlighter = cache[key] ?: TextMateSyntaxHighlighterFactory().getSyntaxHighlighter(project, file).also {
            // Bundle loading and reloads may temporarily return the plain fallback. Keeping it
            // would disable Carve highlights on this thread until the IDE restarts.
            if (it is TextMateHighlighter) cache[key] = it
        }
        return scan(text, highlighter)
    }

    internal fun scan(text: String, highlighter: SyntaxHighlighter): List<TextRange> {
        val ranges = ArrayList<TextRange>()
        val lexer = highlighter.highlightingLexer
        lexer.start(text)
        while (lexer.tokenType != null) {
            ProgressManager.checkCanceled()
            val token = lexer.tokenType
            if (
                token is TextMateElementType &&
                token.scope.contains(HIGHLIGHT_SCOPE) &&
                !token.scope.contains(HIGHLIGHT_DELIMITER_SCOPE) &&
                !token.scope.containsPrefix("comment.")
            ) {
                ranges += TextRange(lexer.tokenStart, lexer.tokenEnd)
            }
            lexer.advance()
        }
        return ranges
    }

    private fun TextMateScope.contains(name: String): Boolean {
        var current: TextMateScope? = this
        while (current != null) {
            if (current.scopeName.toString() == name) return true
            current = current.parent
        }
        return false
    }

    private fun TextMateScope.containsPrefix(prefix: String): Boolean {
        var current: TextMateScope? = this
        while (current != null) {
            if (current.scopeName.toString().startsWith(prefix)) return true
            current = current.parent
        }
        return false
    }

    private const val HIGHLIGHT_SCOPE = "markup.changed.carve"
    private const val HIGHLIGHT_DELIMITER_SCOPE = "keyword.control.highlight.carve"
}
