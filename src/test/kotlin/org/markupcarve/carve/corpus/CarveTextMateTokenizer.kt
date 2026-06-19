package org.markupcarve.carve.corpus

import com.intellij.util.containers.Interner
import org.jetbrains.plugins.textmate.language.TextMateLanguageDescriptor
import org.jetbrains.plugins.textmate.language.syntax.TextMateSyntaxTable
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateLexer
import org.jetbrains.plugins.textmate.plist.JsonPlistReader
import java.util.ArrayDeque

/**
 * Drives the IDE's own TextMate engine (org.jetbrains.plugins.textmate) over the
 * committed Carve grammar, exactly as editor highlighting does at runtime, but
 * without spinning up a full IDE Application. Loading the grammar plist into a
 * [TextMateSyntaxTable] and running [TextMateLexer] is the same code path the
 * plugin's [org.markupcarve.carve.CarveTextMateBundleProvider] feeds at runtime,
 * so the token stream snapshotted here is the highlighting users actually see.
 */
object CarveTextMateTokenizer {

    /** Resource path of the committed grammar inside the production jar. */
    private const val GRAMMAR_RESOURCE = "/textmate/carve.tmLanguage.json"

    /** The grammar's root scope; every token scope is rooted here. */
    const val ROOT_SCOPE: String = "text.carve"

    private val descriptor: TextMateLanguageDescriptor by lazy { loadDescriptor() }

    private fun loadDescriptor(): TextMateLanguageDescriptor {
        val stream = CarveTextMateTokenizer::class.java.getResourceAsStream(GRAMMAR_RESOURCE)
            ?: error("Carve grammar not found on test classpath at $GRAMMAR_RESOURCE")
        val plist = stream.use { JsonPlistReader().read(it) }
        val table = TextMateSyntaxTable()
        val interner: Interner<CharSequence> = Interner.createInterner()
        val scopeName: CharSequence = table.loadSyntax(plist, interner)
            ?: error("Grammar declared no scopeName; expected $ROOT_SCOPE")
        check(scopeName.toString() == ROOT_SCOPE) {
            "Grammar root scope changed: expected $ROOT_SCOPE, got $scopeName"
        }
        val root = table.getSyntax(scopeName)
        return TextMateLanguageDescriptor(scopeName, root)
    }

    /** A single highlighting token: the source text it covers and its full TextMate scope. */
    data class Token(val text: String, val scope: String)

    /**
     * Tokenizes [text] and returns the ordered token stream. Whitespace-only
     * tokens that carry only the root scope are kept so the snapshot is faithful
     * to what the engine produces line by line.
     */
    fun tokenize(text: String): List<Token> {
        val lexer = TextMateLexer(descriptor, Int.MAX_VALUE)
        lexer.init(text, 0)
        val queue = ArrayDeque<TextMateLexer.Token>()
        val tokens = ArrayList<Token>()
        var guard = 0
        val guardLimit = text.length * 4 + 1024
        while (lexer.currentOffset < text.length) {
            lexer.advanceLine(queue)
            while (queue.isNotEmpty()) {
                val t = queue.poll()
                val end = minOf(t.endOffset, text.length)
                if (t.startOffset >= end) continue
                tokens += Token(text.substring(t.startOffset, end), t.scope.toString())
            }
            if (guard++ > guardLimit) {
                error("Lexer made no progress on input (possible grammar loop); aborting after $guard iterations")
            }
        }
        return tokens
    }

    /**
     * Renders the token stream as a stable, human-reviewable snapshot. Each line is
     *   <visible-text> -> <scope>
     * with newlines and tabs escaped so the golden file stays single-line per token.
     */
    fun snapshot(text: String): String {
        val sb = StringBuilder()
        for (token in tokenize(text)) {
            sb.append(escape(token.text)).append(" -> ").append(token.scope).append('\n')
        }
        return sb.toString()
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
}
