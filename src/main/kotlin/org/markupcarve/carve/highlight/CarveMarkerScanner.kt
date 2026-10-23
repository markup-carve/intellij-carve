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
    // A line that starts a block of its own and so cannot be a lazy paragraph line.
    private val LAZY_BREAKER = Regex("""^(?:[-*+>#:|]|[0-9]+[.)]|`{3,}|~{3,}|\{)""")
    private val LONE_PLUS = Regex("""^[ \t]*(\+)[ \t]*$""")
    // A lone `+` after one or more openers on its own line (`- +`, `1. - +`, `- > +`, `> > +`,
    // `: +`, `[^a]: +`, `- [ ] +`) is a first-block continuation marker (markup-carve/carve#3445).
    // Each `>` needs its space: `>+`, `>>+`, `> >+` and `>> +` are text (markup-carve/carve#3421).
    // An ordered item has no task marker, so `1. [ ] +` stays text.
    private const val ORDERED_MARK = """(?:[0-9]+|[A-Za-z]|[ivxlcdm]+|[IVXLCDM]+)[.)]"""
    private val FIRST_BLOCK_PLUS = Regex(
        """^[ \t]*(?:(?:[-*][ \t]+(?:\[[ xX_>?-]\][ \t]+)?|$ORDERED_MARK[ \t]+|> |:[ \t]+|\[\^[^\]\n]+\]:[ \t]+))+(\+)[ \t]*$""",
    )
    private val OPENER_STEP = Regex("""(?:([-*])[ \t]+(?:\[[ xX_>?-]\][ \t]+)?|($ORDERED_MARK)[ \t]+|(>)(?: |$)|(:|\[\^[^\]\n]+\]:)[ \t]+)""")
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
    private val DESCRIPTION_TERM = Regex("""^[ \t]*::[ \t]+\S""")
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
        // Open containers by marker column: one stack for unquoted lines, one for the body of
        // the current quote depth. A separate-line `+` is a marker only at one of these columns
        // (markup-carve/carve#3445, CARVE-P9-031).
        val outer = ArrayList<Box>()
        val quoted = ArrayList<Box>()
        var prevDepth = 0
        var prevQuoteCols: Set<Int> = emptySet()
        var fenceCol = 0

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

            if (inFence && depth == fenceDepth) {
                val inner = if (fenceDepth > 0) line.substring(quotePrefixEnd(line, fenceDepth)) else line
                val plus = LONE_PLUS.find(inner)
                if (plus != null && column(inner, plus.groups[1]!!.range.first) < fenceCol) {
                    // Left of the fence's column the fence's container has ended (CARVE-P9-031).
                    inFence = false; fenceChar = null; fenceLen = 0; fenceDepth = 0
                }
            }
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
                fenceCol = column(body, fence.groups[2]!!.range.first)
                // A fence left of an open item's content column ends that item.
                val owners = if (depth == 0) outer else quoted
                while (owners.isNotEmpty() && owners.last().content > fenceCol) owners.removeAt(owners.size - 1)
                prevDepth = depth; prevQuoteCols = emptySet()
                continue
            }

            if (index <= skipUntil || COMMENT_LINE.containsMatchIn(line)) {
                paragraphOpen = false; lastWasTable = false
                continue
            }

            if (line.isBlank()) {
                paragraphOpen = false; lastWasTable = false
                prevDepth = 0; prevQuoteCols = emptySet()
                continue
            }

            val lonePlus = LONE_PLUS.find(body)
            if (lonePlus != null && (depth == 0 || depth <= prevDepth)) {
                val at = lonePlus.groups[1]!!.range.first
                val col = column(body, at)
                val owner = (if (depth == 0) outer else quoted).lastOrNull { it.marker == col }
                val open = if (depth == 0) owner != null || col in prevQuoteCols
                else owner != null || (col == 0 && deeperQuoteOpen(lines, index, depth))
                // After description or footnote content a `+` also attaches a list.
                val marker = open && followerAttaches(lines, index + 1, depth, col, owner?.list == false, owner != null || deeperQuoteOpen(lines, index, depth))
                if (marker) {
                    QUOTE.find(line)?.let { spans += Span(range(lineStart, it.groups[1]!!.range), CarveColors.QUOTE_MARKER) }
                    spans += Span(TextRange(lineStart + bodyStart + at, lineStart + bodyStart + at + 1), CarveColors.CONTINUATION_MARKER)
                    paragraphOpen = false; lastWasTable = false
                    prevDepth = depth; prevQuoteCols = emptySet()
                    continue
                }
            }

            firstBlockPlus(line, depth, prevDepth, outer.isEmpty(), paragraphOpen && !inList)
                ?.takeIf { firstBlockFollower(lines, index + 1, line, minOf(depth, prevDepth), it) }?.let {
                spans += Span(TextRange(lineStart + it, lineStart + it + 1), CarveColors.CONTINUATION_MARKER)
            }
            val quoteCols = HashSet<Int>()
            if (depth == 0) {
                track(outer, line, paragraphOpen, !paragraphOpen || inList, quoteCols)
            } else {
                val quoteAt = column(line, line.indexOf('>'))
                while (outer.isNotEmpty() && outer.last().content > quoteAt) outer.removeAt(outer.size - 1)
                if (depth != prevDepth) quoted.clear()
                track(quoted, body, paragraphOpen && depth == prevDepth, true, HashSet())
            }
            if (depth > 0) quoteCols += column(line, line.indexOf('>'))

            prevDepth = depth; prevQuoteCols = quoteCols

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
                // A quoted paragraph takes lazy lines, so a list marker below it is text too.
                paragraphOpen = body.isNotBlank() && HEADING.find(body) == null && DIV.find(body) == null
                inList = false
                continue
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
                    val chain = m.groups[2]!!.range
                    for (i in chain.first..chain.last) {
                        if (line[i] == '-' || line[i] == '*') {
                            spans += Span(TextRange(lineStart + i, lineStart + i + 1), CarveColors.LIST_MARKER)
                        }
                    }
                }
                ordered?.let { spans += Span(range(lineStart, it.groups[2]!!.range), CarveColors.LIST_MARKER) }
                inList = true
                paragraphOpen = true
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

    /** An open container: the column of its marker and the column its content starts at. */
    private data class Box(val marker: Int, val content: Int, val list: Boolean = true)

    /** Column of [index] in [text], tabs expanded to the next multiple of four (PART 7). */
    private fun column(text: String, index: Int): Int {
        var col = 0
        for (i in 0 until index) col = if (text[i] == '\t') (col / 4 + 1) * 4 else col + 1
        return col
    }

    /**
     * Offset of a first-block `+` that closes a run of openers on [line], or null. A quote prefix
     * that only continues the quote above is not an opener; that `+` is a separate-line marker.
     */
    private fun firstBlockPlus(line: String, depth: Int, prevDepth: Int, noContainer: Boolean, inParagraph: Boolean): Int? {
        val m = FIRST_BLOCK_PLUS.find(line) ?: return null
        val plus = m.groups[1]!!.range.first
        val head = line.substring(0, plus)
        val lead = head.indexOfFirst { it != ' ' && it != '\t' }
        val onlyQuotes = head.all { it == ' ' || it == '\t' || it == '>' }
        if (onlyQuotes && depth in 1..prevDepth) return null
        // An indented `>` with nothing open is paragraph text, not a quote.
        if (head[lead] == '>' && lead > 0 && noContainer) return null
        // A list marker cannot interrupt a paragraph.
        if (inParagraph && head[lead] != '>' && head[lead] != ':' && head[lead] != '[') return null
        if (Regex("""^[ \t]*$ORDERED_MARK[ \t]+\[""").containsMatchIn(head)) return null
        return plus
    }

    /**
     * Applies [text]'s openers to [stack]; collects the columns of its `>` markers into [quotes].
     * Without [opens] a list marker cannot interrupt the open paragraph, so the line is text.
     */
    private fun track(stack: ArrayList<Box>, text: String, lazy: Boolean, opens: Boolean, quotes: MutableSet<Int>) {
        var i = 0
        while (i < text.length && (text[i] == ' ' || text[i] == '\t')) i++
        if (i >= text.length) return
        val indent = column(text, i)
        var first = true
        while (opens && i < text.length) {
            val m = OPENER_STEP.matchAt(text, i) ?: break
            val at = column(text, i)
            if (m.groups[3] != null) {
                quotes += at
            } else {
                if (first) while (stack.isNotEmpty() && stack.last().marker >= at) stack.removeAt(stack.size - 1)
                stack += Box(at, column(text, m.range.last + 1), m.groups[4] == null)
            }
            first = false
            i = m.range.last + 1
            while (i < text.length && (text[i] == ' ' || text[i] == '\t')) i++
        }
        if (first) {
            val top = stack.lastOrNull() ?: return
            if (lazy && indent < top.content) return
            while (stack.isNotEmpty() && stack.last().content > indent) stack.removeAt(stack.size - 1)
        }
    }

    /** True when a quote deeper than [depth] is still open above [index], through lazy lines. */
    private fun deeperQuoteOpen(lines: List<Pair<String, Int>>, index: Int, depth: Int): Boolean {
        var j = index - 1
        while (j >= 0 && lines[j].first.isNotBlank()) {
            val line = lines[j].first
            val d = QUOTE_PREFIX.find(line)?.value?.count { it == '>' } ?: 0
            if (d > depth) return true
            val body = line.substring(quotePrefixEnd(line, d)).trimStart()
            if (body.isEmpty() || LAZY_BREAKER.containsMatchIn(body)) return false
            j--
        }
        return false
    }

    /** True when line [next] carries a block that starts at [col] under the same quote depth. */
    private fun followerAttaches(
        lines: List<Pair<String, Int>>, next: Int, depth: Int, col: Int, listOk: Boolean = false, deeperOk: Boolean = false,
    ): Boolean {
        if (next >= lines.size) return false
        val line = lines[next].first
        if (line.isBlank()) return false
        val prefix = QUOTE_PREFIX.find(line)
        val d = prefix?.value?.count { it == '>' } ?: 0
        val text = if (depth > 0) {
            // A deeper quote line at the marker's column is a quote the `+` attaches.
            if (d < depth || (d > depth && !deeperOk)) return false
            line.substring(quotePrefixEnd(line, depth))
        } else line
        val start = text.indexOfFirst { it != ' ' && it != '\t' }
        if (start < 0 || column(text, start) != col) return false
        // A list item at the marker's column is a sibling; the `+` attaches nothing.
        return listOk || (BULLET.find(text) == null && ORDERED.find(text) == null)
    }

    /**
     * Whether a first-block `+` at [plus] on [line] stays a marker. After a list item or a
     * description it always does (a refused follower leaves the item empty). After a quote it needs
     * a non-list block at column 0, the line's own column or the innermost quote's column when the
     * run starts with an item; after a
     * footnote label, a block other than a column-0 list. Otherwise it is text.
     */
    private fun firstBlockFollower(lines: List<Pair<String, Int>>, next: Int, line: String, hostDepth: Int, plus: Int): Boolean {
        val depth = hostDepth
        val host = if (depth > 0) quotePrefixEnd(line, depth) else 0
        val head = line.substring(host, plus)
        var i = head.indexOfFirst { it != ' ' && it != '\t' }
        if (i < 0) return false
        val lead = column(head, i)
        var quoteCol = -1
        var lastKind = 0
        var firstKind = 0
        var footnote = false
        while (i < head.length) {
            val m = OPENER_STEP.matchAt(head, i) ?: break
            val kind = (1..4).first { m.groups[it] != null }
            if (firstKind == 0) firstKind = kind
            // The innermost quote's column counts only when the run starts with an item or description.
            if (kind == 3 && firstKind != 3) quoteCol = column(head, i)
            lastKind = kind
            footnote = lastKind == 4 && m.groups[4]!!.value.startsWith("[^")
            i = m.range.last + 1
            while (i < head.length && (head[i] == ' ' || head[i] == '\t')) i++
        }
        if (lastKind != 3 && !footnote) return true
        if (next >= lines.size) return false
        val follow = lines[next].first
        if (follow.isBlank()) return false
        val prefix = QUOTE_PREFIX.find(follow)
        val d = prefix?.value?.count { it == '>' } ?: 0
        val text: String
        if (d >= depth) {
            text = if (depth > 0) follow.substring(quotePrefixEnd(follow, depth)) else follow
        } else {
            // Inside a host quote, an unprefixed block at document column 0 still attaches.
            if (follow[0] == ' ' || follow[0] == '\t') return false
            text = follow
        }
        val start = text.indexOfFirst { it != ' ' && it != '\t' }
        if (start < 0) return false
        val col = column(text, start)
        val isList = BULLET.find(text) != null || ORDERED.find(text) != null
        if (footnote) return !(isList && col == 0)
        return !isList && (col == 0 || col == lead || col == quoteCol)
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
