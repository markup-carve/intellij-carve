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
    private val HEADING = Regex("""^(#{1,6})(?=\s|$)""")
    private val QUOTE = Regex("""^\s*(>+)""")
    private val DIV = Regex("""^\s*(:{3,})""")
    // Bullet chain, including marker-line nested bullets (`- - item`): each `-`/`*` is a marker.
    private val BULLET = Regex("""^(\s*)([-*](?:\s+[-*])*)(?=\s)""")
    // Ordered markers the spec recognizes: digits or letters (roman included), then `.` or `)`
    // - `1.`, `10.`, `1)`, `a.`, `iv)`. A parenthesized `(1)` is prose and must stay literal.
    private val ORDERED = Regex("""^(\s*)([0-9]+[.)]|[A-Za-z]+[.)])(?=\s)""")
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
