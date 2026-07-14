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
    private val HEADING = Regex("""^(#{1,6})(?=\s|$)""")
    private val QUOTE = Regex("""^\s*(>+)""")
    private val DIV = Regex("""^\s*(:{3,})""")
    private val BULLET = Regex("""^(\s*)([-*])(?=\s)""")
    // Multi-digit / multi-letter ordered markers: `10.`, `iv)`, `a)` - as the grammar allows.
    private val ORDERED = Regex("""^(\s*)(\(?[0-9]+[.)]|\(?[A-Za-z]+[.)])(?=\s)""")
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
                    spans += Span(range(lineStart, g.range), CarveColors.FENCE_MARKER)
                    inFence = true; fenceChar = marker[0]; fenceLen = marker.length
                } else if (marker[0] == fenceChar && marker.length >= fenceLen) {
                    // A same-char run at least as long as the opener closes the block; a shorter
                    // run is code content, so only the real closer is a fence marker.
                    spans += Span(range(lineStart, g.range), CarveColors.FENCE_MARKER)
                    inFence = false; fenceChar = null; fenceLen = 0
                }
                continue
            }
            if (inFence) continue

            HEADING.find(line)?.let { spans += Span(range(lineStart, it.groups[1]!!.range), CarveColors.HEADING_MARKER) }
            QUOTE.find(line)?.let { spans += Span(range(lineStart, it.groups[1]!!.range), CarveColors.QUOTE_MARKER) }
            DIV.find(line)?.let { spans += Span(range(lineStart, it.groups[1]!!.range), CarveColors.DIV_MARKER) }
            CONTINUATION.find(line)?.let { spans += Span(range(lineStart, it.groups[2]!!.range), CarveColors.CONTINUATION_MARKER) }
            BULLET.find(line)?.let { spans += Span(range(lineStart, it.groups[2]!!.range), CarveColors.LIST_MARKER) }
            ORDERED.find(line)?.let { spans += Span(range(lineStart, it.groups[2]!!.range), CarveColors.LIST_MARKER) }

            // Table pipes: only on a line shaped like a table row (leading/trailing pipe, or a
            // `+ ... |` continuation), so a stray `|` in prose is left alone.
            if (TABLE_ROW.containsMatchIn(line)) {
                for (m in PIPE.findAll(line)) {
                    spans += Span(TextRange(lineStart + m.range.first, lineStart + m.range.first + 1), CarveColors.TABLE_PIPE)
                }
            }

        }
        return spans
    }

    private fun range(lineStart: Int, r: IntRange): TextRange =
        TextRange(lineStart + r.first, lineStart + r.last + 1)

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
