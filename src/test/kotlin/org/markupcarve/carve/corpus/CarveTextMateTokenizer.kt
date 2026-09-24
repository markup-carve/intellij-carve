package org.markupcarve.carve.corpus

import com.intellij.util.containers.Interner
import org.jetbrains.plugins.textmate.language.TextMateLanguageDescriptor
import org.jetbrains.plugins.textmate.language.syntax.TextMateSyntaxTable
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateLexer
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope
import org.jetbrains.plugins.textmate.plist.JsonPlistReader
import org.jetbrains.plugins.textmate.plist.PListValue
import org.jetbrains.plugins.textmate.plist.Plist
import org.jetbrains.plugins.textmate.plist.PlistValueType
import java.io.File
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
        return loadDescriptor(plist, ROOT_SCOPE)
    }

    private fun loadDescriptor(plist: Plist, expectedRoot: String?): TextMateLanguageDescriptor {
        val table = TextMateSyntaxTable()
        val interner: Interner<CharSequence> = Interner.createInterner()
        val scopeName: CharSequence = table.loadSyntax(plist, interner)
            ?: error("Grammar declared no scopeName; expected $expectedRoot")
        if (expectedRoot != null) {
            check(scopeName.toString() == expectedRoot) {
                "Grammar root scope changed: expected $expectedRoot, got $scopeName"
            }
        }
        val root = table.getSyntax(scopeName)
        return TextMateLanguageDescriptor(scopeName, root)
    }

    private fun readPlist(file: File): Plist = file.inputStream().use { JsonPlistReader().read(it) }

    /**
     * A grammar other than the committed one - vscode-carve's copy, or a probing variant
     * of either. Its own [TextMateSyntaxTable], so two grammars that share the root scope
     * name do not collide in one JVM.
     */
    class Grammar internal constructor(
        private val descriptor: TextMateLanguageDescriptor,
    ) {
        val rootScope: String get() = descriptor.scopeName.toString()

        fun tokenize(text: String): List<Token> = tokenizeWith(descriptor, text)

        fun snapshot(text: String): String = render(tokenize(text))

        /**
         * The character ranges [rule] OWNS in [text], where [rule] was loaded through
         * [grammarProbing]: the ranges whose tokens carry [PROBE_SCOPE].
         */
        fun probedSpans(text: String): List<Span> {
            val spans = ArrayList<Span>()
            var offset = 0
            for (token in tokenize(text)) {
                val start = offset
                offset += token.text.length
                if (PROBE_SCOPE in token.scope.split(' ')) spans += Span(start, offset, token.text)
            }
            return spans
        }

        /** True when this grammar puts any scope beyond its root over [span]. */
        fun highlightsAnythingIn(text: String, span: Span): Boolean {
            var offset = 0
            for (token in tokenize(text)) {
                val start = offset
                offset += token.text.length
                if (start < span.end && offset > span.start && token.scope.trim() != rootScope) return true
            }
            return false
        }
    }

    /** A half-open character range of a fixture, with the text it covers. */
    data class Span(val start: Int, val end: Int, val text: String)

    /**
     * The scope a probed rule is rewritten to emit, chosen so no real grammar carries it.
     */
    const val PROBE_SCOPE: String = "zzcarve.probe.owner"

    /** Loads [file] as a TextMate grammar. */
    fun grammarFrom(file: File): Grammar = Grammar(loadDescriptor(readPlist(file), expectedRoot = null))

    /**
     * The committed grammar and [others], all in ONE [TextMateSyntaxTable], rooted at the
     * Carve grammar.
     *
     * This is the shape the IDE has at runtime: `TextMateServiceImpl` holds a single
     * syntax table and registers every enabled bundle into it, so a rule that includes
     * another grammar's scope - `source.js` inside a fenced code body - resolves against
     * whatever else is registered. One table per grammar, which every other entry point
     * here uses, cannot see that resolution at all: the include finds nothing and the
     * body stays flat, which is also what a stock IDE does for a language it has no
     * bundle for.
     */
    fun grammarsTogether(vararg others: File): Grammar {
        val table = TextMateSyntaxTable()
        val interner: Interner<CharSequence> = Interner.createInterner()
        val stream = CarveTextMateTokenizer::class.java.getResourceAsStream(GRAMMAR_RESOURCE)
            ?: error("Carve grammar not found on test classpath at $GRAMMAR_RESOURCE")
        val carveScope = stream.use { table.loadSyntax(JsonPlistReader().read(it), interner) }
            ?: error("Carve grammar declared no scopeName")
        others.forEach { file ->
            table.loadSyntax(readPlist(file), interner) ?: error("${file.name} declared no scopeName")
        }
        return Grammar(TextMateLanguageDescriptor(carveScope, table.getSyntax(carveScope)))
    }

    /**
     * Loads [file] with every scope name inside repository rule [rule] replaced by
     * [PROBE_SCOPE], so the tokens that rule produced can be told apart from every
     * other rule's.
     *
     * ATTRIBUTION, NOT SCOPE MATCHING. Asking which text a rule covers by looking for
     * the scopes it declares answers a different question: `heading-on-marker-line`
     * declares `markup.list.unnumbered.carve`, which `lists` declares too, so that
     * reading calls every bullet in the corpus part of the construct. A scope name
     * never affects what a regex matches, so renaming one leaves the tokenization
     * identical and marks exactly the tokens this rule won - in real competition with
     * every other rule, which deleting it would not preserve.
     */
    fun grammarProbing(file: File, rule: String): Grammar {
        val root = readPlist(file)
        val repository = root.getPlistValue("repository")?.plist
            ?: error("${file.name} has no repository, so no rule to probe")
        check(repository.contains(rule)) { "${file.name} has no repository rule '$rule' to probe" }
        val probedRepository =
            Plist(
                repository.entries().associate { (name, value) ->
                    name to if (name == rule) probeScopes(value) else value
                },
            )
        val probedRoot =
            Plist(
                root.entries().associate { (key, value) ->
                    key to if (key == "repository") PListValue(probedRepository, PlistValueType.DICT) else value
                },
            )
        return Grammar(loadDescriptor(probedRoot, expectedRoot = null))
    }

    private fun probeScopes(value: PListValue): PListValue =
        when (value.type) {
            PlistValueType.DICT ->
                PListValue(
                    Plist(
                        value.plist.entries().associate { (key, child) ->
                            key to
                                if ((key == "name" || key == "contentName") && child.type == PlistValueType.STRING) {
                                    PListValue(PROBE_SCOPE, PlistValueType.STRING)
                                } else {
                                    probeScopes(child)
                                }
                        },
                    ),
                    PlistValueType.DICT,
                )
            PlistValueType.ARRAY -> PListValue(value.array.map { probeScopes(it) }, PlistValueType.ARRAY)
            else -> value
        }

    /** A single highlighting token: the source text it covers and its full TextMate scope. */
    data class Token(val text: String, val scope: String)

    /**
     * Tokenizes [text] and returns the ordered token stream. Whitespace-only
     * tokens that carry only the root scope are kept so the snapshot is faithful
     * to what the engine produces line by line.
     */
    fun tokenize(text: String): List<Token> = tokenizeWith(descriptor, text)

    private fun tokenizeWith(descriptor: TextMateLanguageDescriptor, text: String): List<Token> {
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
                tokens += Token(text.substring(t.startOffset, end), scopeChain(t.scope))
            }
            if (guard++ > guardLimit) {
                error("Lexer made no progress on input (possible grammar loop); aborting after $guard iterations")
            }
        }
        return tokens
    }

    /**
     * The token's full scope chain, root first, one space between segments.
     *
     * Spelled out here rather than taken from [TextMateScope.toString]. That method is
     * not a contract: the platform rewrote `TextMateScope` from Java to Kotlin in 2025.1
     * and the new `toString` concatenates the segments with NO separator, so every
     * golden in this repository - 453 of the 618 tests - failed on a scope chain that
     * reads `text.carvemeta.attributes.carve`. The chain itself did not change; only the
     * rendering of it did. Walking the parent links is the same walk the old
     * implementation did, and it cannot be changed out from under the goldens.
     */
    private fun scopeChain(scope: TextMateScope): String {
        val names = ArrayList<CharSequence>()
        var current: TextMateScope? = scope
        while (current != null) {
            current.scopeName?.let { names += it }
            current = current.parent
        }
        return names.asReversed().joinToString(" ").trim()
    }

    /**
     * Renders the token stream as a stable, human-reviewable snapshot. Each line is
     *   <visible-text> -> <scope>
     * with newlines and tabs escaped so the golden file stays single-line per token.
     */
    fun snapshot(text: String): String = render(tokenize(text))

    private fun render(tokens: List<Token>): String {
        val sb = StringBuilder()
        for (token in tokens) {
            sb.append(escape(token.text)).append(" -> ").append(token.scope).append('\n')
        }
        return sb.toString()
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
}
