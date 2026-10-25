package org.markupcarve.carve.highlight

import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.textmate.language.syntax.highlighting.TextMateSyntaxHighlighterFactory
import org.jetbrains.plugins.textmate.language.syntax.highlighting.TextMateHighlighter
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateElementType
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope

/** Finds highlight bodies and table markers using the same TextMate lexer as the editor. */
object CarveHighlightScanner {
    private val highlighters = ThreadLocal.withInitial { mutableMapOf<String, SyntaxHighlighter>() }

    data class ScanResult(val highlights: List<TextRange>, val tableMarkers: List<CarveMarkerScanner.Span>)

    fun scanWithTableMarkers(text: String, project: Project?, file: VirtualFile): ScanResult {
        val key = file.extension.orEmpty().lowercase()
        val cache = highlighters.get()
        val highlighter = cache[key] ?: TextMateSyntaxHighlighterFactory().getSyntaxHighlighter(project, file).also {
            // Bundle loading and reloads may temporarily return the plain fallback. Keeping it
            // would disable Carve highlights on this thread until the IDE restarts.
            if (it is TextMateHighlighter) cache[key] = it
        }
        return scanWithTableMarkers(text, highlighter)
    }

    internal fun scan(text: String, highlighter: SyntaxHighlighter): List<TextRange> =
        scanWithTableMarkers(text, highlighter).highlights

    internal fun scanWithTableMarkers(text: String, highlighter: SyntaxHighlighter): ScanResult {
        val ranges = ArrayList<TextRange>()
        val tableMarkers = ArrayList<CarveMarkerScanner.Span>()
        val lexer = highlighter.highlightingLexer
        lexer.start(text)
        while (lexer.tokenType != null) {
            ProgressManager.checkCanceled()
            val token = lexer.tokenType
            if (token is TextMateElementType && !token.scope.containsPrefix("comment.")) {
                if (token.scope.contains(HIGHLIGHT_SCOPE) && !token.scope.contains(HIGHLIGHT_DELIMITER_SCOPE)) {
                    ranges += TextRange(lexer.tokenStart, lexer.tokenEnd)
                }
                tableKey(token.scope)?.let { key ->
                    // The grammar's bare-header capture is `|=` as one token. The pipe has
                    // its own marker colour from CarveMarkerScanner; colour only the `=`.
                    val start = lexer.tokenStart + if (key == CarveColors.TABLE_HEADER && text[lexer.tokenStart] == '|') 1 else 0
                    if (start < lexer.tokenEnd) {
                        tableMarkers += CarveMarkerScanner.Span(TextRange(start, lexer.tokenEnd), key)
                    }
                }
            }
            lexer.advance()
        }
        return ScanResult(ranges, tableMarkers)
    }

    private fun tableKey(scope: TextMateScope): TextAttributesKey? = when {
        scope.contains("keyword.control.table.header.carve") -> CarveColors.TABLE_HEADER
        scope.contains("keyword.control.table.alignment.carve") -> CarveColors.TABLE_ALIGNMENT
        scope.contains("keyword.control.table.rowspan.carve") ||
            scope.contains("keyword.control.table.colspan.carve") -> CarveColors.TABLE_SPAN
        scope.contains("keyword.control.table.separator.carve") -> CarveColors.TABLE_PIPE
        else -> null
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
