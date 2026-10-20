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
    // A valid opener: fence run, then an optional lang token, optional "title", optional [attrs].
    // Anything else (e.g. ```js title="x") is not a fence line, so it must not toggle fence state.
    // The token class is the spec's `language_info`: letters, digits and `- _ + # . /`, so
    // `c++`, `c#`, `f#`, `asp.net` and `text/html` open fences.
    private val FENCE_OPEN_INFO = Regex("""^\s*(`{3,}|~{3,})\s*=?[A-Za-z0-9_+#./-]*\s*("[^"\n]*")?\s*(\[[^\]\n]*\])?\s*$""")
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
    // while `1.{data-x=y}` and `1.{#a-b}` are list items. The highlighting
    // grammars admit a colon in a key; carve-js does not, and it is the arbiter here.
    //
    // A BOOLEAN attribute may not start with `_` (spec PART 9 §14, markup-carve/carve#1450):
    // the bare form gave the collision up to the forced underline, so `{_x_}` is `<u>x</u>`
    // rather than an attribute named `_x_`. Only the bare form is narrowed - `-{_k=1} x`,
    // `-{#_id} x` and `-{._c} x` keep their leading underscore, and none of them can be read
    // as an underline. Measured through the bundled engine: `-{_k} x` and `1.{_k} x` are
    // paragraphs, `-{k} x` is a list item.
    private const val ATTR_ITEM =
        """(?:[.#][A-Za-z_][\w-]*|[A-Za-z_][\w-]*""" +
            """=(?:"(?:[^"\\\n]|\\.)*"|'(?:[^'\\\n]|\\.)*'|[^\s"'{}]+)|[A-Za-z][\w-]*)"""
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
    private val LONE_PLUS = Regex("""^()(\+)(?=[ \t]*$)""")
    // A lone `+` right after a block opener on the same line (`- +`, `1. +`, `> +`, `: +`,
    // `[^a]: +`): the continuation marker, like a `+` at column 0 (markup-carve/carve#3421).
    private val PLUS_AFTER_MARKER = Regex("""^[ \t]+(\+)(?=[ \t]*$)""")
    private val PLUS_AFTER_QUOTE = Regex("""^(\+)(?=[ \t]*$)""")
    private val PLUS_AFTER_DEFINITION = Regex("""^[ \t]*(?::|\[\^[^\]\n]+\]:)[ \t]+(\+)(?=[ \t]*$)""")
    private val CONTINUATION_ROW = Regex("""^\s*\+.*(?<!\\)\|""")
    private val PIPE = Regex("""(?<!\\)\|""")
    // A table row opens AND closes with a pipe; trailing attributes may follow the closer.
    // `| a | b` and `a | b |` are paragraphs in carve-js, and so is prose like `choose a | b`.
    private val TABLE_ROW = Regex("""^\s*\|.*(?<!\\)\|[ \t]*(?:\{[^{}\n]*\})?[ \t]*$""")

    // A `>` marker per level, each followed by a space or the line end (`> > x`).
    private val QUOTE_PREFIX = Regex("""^[ \t]*>(?: |$)(?:>(?: |$))*""")
    private val COMMENT_FENCE = Regex("""^[ \t]*(%{3,})""")
    private val COMMENT_LINE = Regex("""^[ \t]*%%""")
    // Front matter: a bare or typed `---` opener on the first line, closed by a bare `---`.
    private val FRONT_MATTER_OPEN = Regex("""^---(?: ?[A-Za-z0-9]+)?[ \t]*$""")
    private val FRONT_MATTER_CLOSE = Regex("""^---[ \t]*$""")
    private val THEMATIC_BREAK = Regex("""^[ \t]*(?:-{3,}|\*{3,})[ \t]*$""")
    private val DESCRIPTION_TERM = Regex("""^::[ \t]+\S""")
    private val ATTRIBUTE_LINE = Regex("""^[ \t]*$ATTR_BLOCK[ \t]*$""")

    fun scan(text: String): List<Span> {
        val spans = ArrayList<Span>()
        val lines = splitKeepingOffsets(text)
        var inFence = false
        var fenceChar: Char? = null
        var fenceLen = 0
        var fenceDepth = 0
        // Index of the line that closes the open `%%%` block; -1 when none.
        var skipUntil = -1
        // Block context, so a marker that cannot interrupt a paragraph stays prose:
        // `para` then `- not a list` is one paragraph in carve-js.
        var paragraphOpen = false
        var inList = false
        var lastWasTable = false

        var startIndex = 0
        if (lines.isNotEmpty() && FRONT_MATTER_OPEN.matches(lines[0].first)) {
            val close = (1 until lines.size).firstOrNull { FRONT_MATTER_CLOSE.matches(lines[it].first) }
            if (close != null) startIndex = close + 1
        }

        for (index in startIndex until lines.size) {
            val (line, lineStart) = lines[index]
            if (index <= skipUntil) continue

            val prefix = QUOTE_PREFIX.find(line)
            val depth = prefix?.value?.count { it == '>' } ?: 0

            if (inFence) {
                if (depth < fenceDepth) {
                    // The quote holding the fence ended, and the fence with it.
                    inFence = false; fenceChar = null; fenceLen = 0; fenceDepth = 0
                } else {
                    val body = if (fenceDepth > 0) line.substring(quotePrefixEnd(line, fenceDepth)) else line
                    val bodyStart = line.length - body.length
                    if (fenceDepth > 0) QUOTE.find(line)?.let { spans += Span(range(lineStart, it.groups[1]!!.range), CarveColors.QUOTE_MARKER) }
                    val close = FENCE.find(body)
                    if (close != null && FENCE_CLOSE.matches(body)) {
                        val marker = close.groupValues[2]
                        if (marker[0] == fenceChar && marker.length >= fenceLen) {
                            // A bare same-char run at least as long as the opener closes the block; a
                            // shorter run, or one with trailing content, is code - not a closer.
                            spans += Span(range(lineStart + bodyStart, close.groups[2]!!.range), CarveColors.FENCE_MARKER)
                            inFence = false; fenceChar = null; fenceLen = 0; fenceDepth = 0
                            paragraphOpen = false; inList = false; lastWasTable = false
                        }
                    }
                    continue
                }
            }

            val body = if (prefix != null) line.substring(prefix.range.last + 1) else line
            val bodyStart = line.length - body.length

            val commentOpen = if (depth == 0) COMMENT_FENCE.find(line) else null
            if (commentOpen != null) {
                val len = commentOpen.groupValues[1].length
                val close = (index + 1 until lines.size).firstOrNull {
                    COMMENT_FENCE.find(lines[it].first)?.groupValues?.get(1)?.length == len
                }
                // Without a closer ahead the line is an ordinary `%%` comment.
                if (close != null) skipUntil = close
            }

            val fence = FENCE.find(body)
            if (fence != null && FENCE_OPEN_INFO.matches(body)) {
                // Only a well-formed opener starts a fenced block; a malformed one
                // (```js title="x") is prose and must not suppress markers below it.
                QUOTE.find(line)?.let { spans += Span(range(lineStart, it.groups[1]!!.range), CarveColors.QUOTE_MARKER) }
                val marker = fence.groupValues[2]
                spans += Span(range(lineStart + bodyStart, fence.groups[2]!!.range), CarveColors.FENCE_MARKER)
                inFence = true; fenceChar = marker[0]; fenceLen = marker.length; fenceDepth = depth
                continue
            }

            if (index <= skipUntil || COMMENT_LINE.containsMatchIn(line)) {
                paragraphOpen = false; lastWasTable = false
                continue
            }

            if (line.isBlank()) {
                paragraphOpen = false; lastWasTable = false
                continue
            }

            QUOTE.find(line)?.let { spans += Span(range(lineStart, it.groups[1]!!.range), CarveColors.QUOTE_MARKER) }
            val tableRow = TABLE_ROW.containsMatchIn(body) || (lastWasTable && CONTINUATION_ROW.containsMatchIn(body))
            if (tableRow) {
                // Never a `|` inside an inline `code` span - that pipe is cell content.
                for (m in PIPE.findAll(body)) {
                    if (insideInlineCode(body, m.range.first)) continue
                    val at = lineStart + bodyStart + m.range.first
                    spans += Span(TextRange(at, at + 1), CarveColors.TABLE_PIPE)
                }
                if (depth == 0) {
                    CONTINUATION.find(line)?.let { spans += Span(range(lineStart, it.groups[2]!!.range), CarveColors.CONTINUATION_MARKER) }
                }
                paragraphOpen = false; inList = false; lastWasTable = true
                continue
            }
            lastWasTable = false

            if (depth > 0) {
                PLUS_AFTER_QUOTE.find(body)?.let {
                    spans += Span(range(lineStart + bodyStart, it.groups[1]!!.range), CarveColors.CONTINUATION_MARKER)
                }
                // A quoted paragraph takes lazy lines, so a list marker below it is text too.
                paragraphOpen = body.isNotBlank() && HEADING.find(body) == null && DIV.find(body) == null
                inList = false
                continue
            }

            PLUS_AFTER_DEFINITION.find(line)?.let {
                spans += Span(range(lineStart, it.groups[1]!!.range), CarveColors.CONTINUATION_MARKER)
            }

            val heading = HEADING.find(line)
            val div = DIV.find(line)
            if (heading != null || div != null || THEMATIC_BREAK.matches(line) ||
                DESCRIPTION_TERM.containsMatchIn(line) || ATTRIBUTE_LINE.matches(line)
            ) {
                heading?.let { spans += Span(range(lineStart, it.groups[1]!!.range), CarveColors.HEADING_MARKER) }
                div?.let { spans += Span(range(lineStart, it.groups[1]!!.range), CarveColors.DIV_MARKER) }
                paragraphOpen = false
                if (!line[0].isWhitespace()) inList = false
                continue
            }

            // List markers and a lone `+` do not interrupt a paragraph; inside a list they
            // start a sibling or nested item, or continue the item.
            val markersAllowed = !paragraphOpen || inList
            val bullet = BULLET.find(line)
            val ordered = ORDERED.find(line)
            if (markersAllowed && (bullet != null || ordered != null)) {
                bullet?.let { m ->
                    plusAfterMarker(line, m.range.last + 1, lineStart)?.let { spans += it }
                    val chain = m.groups[2]!!.range
                    for (i in chain.first..chain.last) {
                        if (line[i] == '-' || line[i] == '*') {
                            spans += Span(TextRange(lineStart + i, lineStart + i + 1), CarveColors.LIST_MARKER)
                        }
                    }
                }
                ordered?.let {
                    spans += Span(range(lineStart, it.groups[2]!!.range), CarveColors.LIST_MARKER)
                    plusAfterMarker(line, it.range.last + 1, lineStart)?.let { plus -> spans += plus }
                }
                inList = true
                paragraphOpen = true
                continue
            }
            val continuation = if (markersAllowed) LONE_PLUS.find(line) else null
            if (continuation != null) {
                spans += Span(range(lineStart, continuation.groups[2]!!.range), CarveColors.CONTINUATION_MARKER)
                paragraphOpen = false
                continue
            }

            // Paragraph text. A flush-left line after a blank one ends the list.
            if (!paragraphOpen && !line[0].isWhitespace()) inList = false
            paragraphOpen = true
        }
        return spans
    }

    /** Offset just past the first [levels] `>` markers of a quoted line. */
    private fun quotePrefixEnd(line: String, levels: Int): Int {
        var i = 0
        var seen = 0
        while (i < line.length && (line[i] == ' ' || line[i] == '\t')) i++
        while (seen < levels && i < line.length && line[i] == '>') {
            seen++
            i++
            if (i < line.length && line[i] == ' ') i++
        }
        return i
    }

    private fun plusAfterMarker(line: String, from: Int, lineStart: Int): Span? {
        val m = PLUS_AFTER_MARKER.find(line.substring(from)) ?: return null
        val at = lineStart + from + m.groups[1]!!.range.first
        return Span(TextRange(at, at + 1), CarveColors.CONTINUATION_MARKER)
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
