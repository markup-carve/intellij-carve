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
    private val ORDERED = Regex("""^(\s*)(\(?[0-9A-Za-z][.)])(?=\s)""")
    private val CONTINUATION = Regex("""^(\s*)(\+)(?=\s|$|.*\|)""")
    private val PIPE = Regex("""(?<!\\)\|""")

    fun scan(text: String): List<Span> {
        val spans = ArrayList<Span>()
        var inFence = false
        var fenceMarker: String? = null

        for (rawLine in splitKeepingOffsets(text)) {
            val (line, lineStart) = rawLine

            val fence = FENCE.find(line)
            if (fence != null) {
                val marker = fence.groupValues[2]
                val g = fence.groups[2]!!
                spans += Span(range(lineStart, g.range), CarveColors.FENCE_MARKER)
                if (!inFence) {
                    inFence = true; fenceMarker = marker.take(1)
                } else if (fenceMarker != null && marker.startsWith(fenceMarker!!)) {
                    inFence = false; fenceMarker = null
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

            // Table pipes: only when the line carries at least two (a real row), to avoid
            // colouring a stray `|` in prose.
            val pipes = PIPE.findAll(line).toList()
            if (pipes.size >= 2) {
                for (m in pipes) spans += Span(TextRange(lineStart + m.range.first, lineStart + m.range.first + 1), CarveColors.TABLE_PIPE)
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
