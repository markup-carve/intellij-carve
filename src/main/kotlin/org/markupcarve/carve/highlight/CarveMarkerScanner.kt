package org.markupcarve.carve.highlight

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange

/**
 * Finds Carve's structural markers and the colour each should get. Pure and line-oriented so
 * it can be unit-tested without an IDE; [CarveAnnotator] just turns the results into editor
 * annotations.
 *
 * Only *markers* are returned - never content. Inside a fenced code block nothing is scanned
 * except the fence delimiters themselves, so code keeps its TextMate colouring untouched.
 */
object CarveMarkerScanner {

    data class Span(val range: TextRange, val key: TextAttributesKey)

    private val FENCE = Regex("""^(\s*)(`{3,}|~{3,})""")
    // A valid opener: fence run, then an optional lang word, optional "title", optional [attrs].
    // Anything else (e.g. ```js title="x") is not a fence line, so it must not toggle fence state.
    private val FENCE_OPEN_INFO = Regex("""^\s*(`{3,}|~{3,})\s*=?[A-Za-z0-9_-]*\s*("[^"\n]*")?\s*(\[[^\]\n]*\])?\s*$""")
    // A closer is a bare fence run on its own line.
    private val FENCE_CLOSE = Regex("""^\s*(`{3,}|~{3,})\s*$""")
    // MARKER REQUIRES CONTENT: `#` and `#<space>` are prose, not headings
    // (markup-carve/carve#513). carve-rs renders both as `<p>#</p>`. The
    // separator is a space or tab, never a newline, and never any other Unicode
    // space: `#<space><NBSP>H` IS a heading, so the guard is a line-end
    // lookahead rather than `\S`.
    private val HEADING = Regex("""^(#{1,6})(?=[ \t])(?![ \t]*$)""")
    // A `>` marker takes a SPACE, or stands alone on its line. Not `>+`, and not
    // `\s`: verified against carve-rs, `>no space`, `>>x`, `>> x` and `>\tx` are
    // all paragraphs - nesting is written `> > x`, a space per marker, and a tab
    // does not separate (markup-carve/carve#525). Matching a run colored the
    // marker in `>>= operator` and `>=3 items`, which the language calls prose.
    private val QUOTE = Regex("""^[ \t]*(>)(?= |$)""")
    private val DIV = Regex("""^\s*(:{3,})""")
    // A marker may be GLUED to an attribute block, and then the required space comes
    // after the block, not after the marker (`1.{#x} item`, `-{#x} [x] done`). Both
    // patterns below demanded the space immediately, so the annotator left the marker
    // uncoloured on a line that IS a list item - the bundled grammar colours it
    // (markup-carve/intellij-carve#55).
    //
    // The block is spelled out rather than skipped with `\{[^}]*\}`: a value may hold a
    // `}` inside quotes and may escape its own quote, so a short run stops in the wrong
    // place and rejects `1.{title="a}b"} item`, which is a valid item (#54).
    //
    // The payload must be VALID attribute syntax, not merely brace-delimited: an invalid
    // one means the `{` is literal content and the line is prose. `1.{2=v} text`,
    // `1.{+a+} text` and `1.{bad!!} text` are all paragraphs, while `-{not attrs} text`
    // is a list item with two boolean attributes - checked against carve-js, and the two
    // bundled grammars disagree with each other on exactly this set. An identifier is
    // STRICT (spec PART 9 §14): a class, id or key starts with a letter or `_` and then
    // takes letters, digits, `_` and `-`. Nothing else - `1.{2=v}` (digit first),
    // `-{--flag}` and `1.{#-id}` (dash first) and `-{a:b}` (colon) are all paragraphs,
    // while `1.{data-x=y}`, `-{_k}` and `1.{#a-b}` are list items. The highlighting
    // grammars admit a colon in a key; carve-js does not, and it is the arbiter here.
    private const val ATTR_ITEM =
        """(?:[.#][A-Za-z_][\w-]*|[A-Za-z_][\w-]*""" +
            """(?:=(?:"(?:[^"\\\n]|\\.)*"|'(?:[^'\\\n]|\\.)*'|[^\s"'{}]+))?)"""
    private const val ATTR_BLOCK = """\{\s*(?:$ATTR_ITEM(?:\s+$ATTR_ITEM)*\s*)?\}"""
    // Content is still REQUIRED after the block: `1.{#x}` alone is a paragraph (#54).
    private val AFTER_MARKER = """(?:(?=[ \t])|(?=$ATTR_BLOCK[ \t]+[^ \t\n]))(?![ \t]*$)"""

    // Bullet chain, including marker-line nested bullets (`- - item`): each `-`/`*` is a marker.
    private val BULLET = Regex("""^([ \t]*)([-*](?:[ \t]+[-*])*)$AFTER_MARKER""")
    // Ordered markers: a digit run, a single letter or a roman run, then `.` or `)` -
    // `1.`, `10.`, `1)`, `a.`, `b)`, `iv.`, `XI)` - plus the BARE DOT, which continues an
    // ordered sequence and is the only marker allowed to drop its value
    // (markup-carve/carve#472). A multi-letter non-roman word (`Note.`) is prose, and a
    // parenthesized `(1)` is not a marker; both stay literal.
    //
    // Roman and the bare dot were missing here while the bundled grammar carried both,
    // so the annotator disagreed with the highlighter on `iv. fourth` and `. first`.
    // Verified against carve-js: both open an `<ol>`, and `Note. text` stays a paragraph.
    //
    // A roman run is CASE-CONSISTENT: two classes, not one `[ivxlcdmIVXLCDM]`. Mixed case
    // is prose - `Vim. text` and `Mix. text` are paragraphs where `ivx.` and `IVX.` are
    // lists, checked against carve-js. The bundled grammar uses the mixed class and
    // colours `Vim. text` as a list; this deliberately does not copy that.
    private val ORDERED = Regex(
        """^([ \t]*)([0-9]+[.)]|[A-Za-z][.)]|[ivxlcdm]+[.)]|[IVXLCDM]+[.)]|\.)$AFTER_MARKER""",
    )
    // Continuation is a LONE `+` line, or a `+ ... |` table-continuation row - NOT `+ prose`.
    private val CONTINUATION = Regex("""^(\s*)(\+)(?=\s*$|.*\|)""")
    private val PIPE = Regex("""(?<!\\)\|""")
    // A table row starts or ends with a pipe (standard leading/trailing `|`), or is a `+ ... |`
    // continuation row. Prose like `choose a | b | c` matches none of these.
    private val TABLE_ROW = Regex("""^\s*(\||\+.*\|)|\|\s*$""")

    fun scan(text: String): List<Span> {
        val spans = ArrayList<Span>()
        var inFence = false
        var fenceChar: Char? = null
        var fenceLen = 0

        for (rawLine in splitKeepingOffsets(text)) {
            val (line, lineStart) = rawLine

            val fence = FENCE.find(line)
            if (fence != null) {
                val marker = fence.groupValues[2]
                val g = fence.groups[2]!!
                if (!inFence) {
                    // Only a well-formed opener starts a fenced block; a malformed one
                    // (```js title="x") is prose and must not suppress markers below it.
                    if (FENCE_OPEN_INFO.matches(line)) {
                        spans += Span(range(lineStart, g.range), CarveColors.FENCE_MARKER)
                        inFence = true; fenceChar = marker[0]; fenceLen = marker.length
                        continue
                    }
                } else if (FENCE_CLOSE.matches(line) && marker[0] == fenceChar && marker.length >= fenceLen) {
                    // A bare same-char run at least as long as the opener closes the block; a
                    // shorter run, or one with trailing content, is code - not a closer.
                    spans += Span(range(lineStart, g.range), CarveColors.FENCE_MARKER)
                    inFence = false; fenceChar = null; fenceLen = 0
                    continue
                }
            }
            if (inFence) continue

            HEADING.find(line)?.let { spans += Span(range(lineStart, it.groups[1]!!.range), CarveColors.HEADING_MARKER) }
            QUOTE.find(line)?.let { spans += Span(range(lineStart, it.groups[1]!!.range), CarveColors.QUOTE_MARKER) }
            DIV.find(line)?.let { spans += Span(range(lineStart, it.groups[1]!!.range), CarveColors.DIV_MARKER) }
            CONTINUATION.find(line)?.let { spans += Span(range(lineStart, it.groups[2]!!.range), CarveColors.CONTINUATION_MARKER) }
            BULLET.find(line)?.let { m ->
                val chain = m.groups[2]!!.range
                for (i in chain.first..chain.last) {
                    if (line[i] == '-' || line[i] == '*') {
                        spans += Span(TextRange(lineStart + i, lineStart + i + 1), CarveColors.LIST_MARKER)
                    }
                }
            }
            ORDERED.find(line)?.let { spans += Span(range(lineStart, it.groups[2]!!.range), CarveColors.LIST_MARKER) }

            // Table pipes: only on a line shaped like a table row (leading/trailing pipe, or a
            // `+ ... |` continuation), and never a `|` inside an inline `code` span - that pipe
            // is cell content and belongs to TextMate's code colour.
            if (TABLE_ROW.containsMatchIn(line)) {
                for (m in PIPE.findAll(line)) {
                    if (insideInlineCode(line, m.range.first)) continue
                    spans += Span(TextRange(lineStart + m.range.first, lineStart + m.range.first + 1), CarveColors.TABLE_PIPE)
                }
            }

        }
        return spans
    }

    private fun range(lineStart: Int, r: IntRange): TextRange =
        TextRange(lineStart + r.first, lineStart + r.last + 1)

    /** True when [index] on [line] falls inside a backtick inline-code span. */
    private fun insideInlineCode(line: String, index: Int): Boolean {
        var inCode = false
        for (i in 0 until index) if (line[i] == '`') inCode = !inCode
        return inCode
    }

    /** Splits into (lineText, absoluteStartOffset) pairs; newline handling is `\n`-based. */
    private fun splitKeepingOffsets(text: String): List<Pair<String, Int>> {
        val out = ArrayList<Pair<String, Int>>()
        var start = 0
        var i = 0
        while (i <= text.length) {
            if (i == text.length || text[i] == '\n') {
                out += (if (i > start && text[i - 1] == '\r') text.substring(start, i - 1) else text.substring(start, i)) to start
                start = i + 1
            }
            i++
        }
        return out
    }
}
