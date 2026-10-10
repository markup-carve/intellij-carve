package org.markupcarve.carve.preview

/**
 * Whether the preview should keep its last render while the author types a
 * new list item.
 *
 * A marker with nothing after it is paragraph text, so `- ` typed under a list
 * folds into the item above for one keystroke and the preview flickers through
 * a reading the author never meant. Free of IDE types so it can be unit tested;
 * a port of vscode-carve's `preview-hold.ts`.
 */
object CarvePreviewHold {

    private val LINE_BREAK = Regex("\r\n|\r|\n")

    /** Whether a line holds only a list marker (and optionally a task box). */
    fun isBareListMarker(line: String): Boolean {
        var pos = skipBlanks(line, 0)
        while (pos < line.length && line[pos] == '>') pos = skipBlanks(line, pos + 1)
        pos = marker(line, pos)
        if (pos < 0) return false
        val afterBlanks = skipBlanks(line, pos)
        if (afterBlanks > pos) {
            val afterTask = task(line, afterBlanks)
            pos = if (afterTask >= 0) skipBlanks(line, afterTask) else afterBlanks
        }
        return pos == line.length
    }

    // Hand-written rather than a regex: java.util.regex recurses once per loop
    // iteration, so a long marker-like line overflowed the stack.

    /**
     * End of the marker at [pos], or -1. Same set as the grammar's container
     * rules: `-`, `*`, `N.`/`N)`, one letter, a roman run of two or more in one
     * case, or a bare `.`. `+` is the continuation marker, not a bullet. One
     * marker only: `* * *` is a thematic break.
     */
    private fun marker(line: String, pos: Int): Int {
        if (pos >= line.length) return -1
        val c = line[pos]
        if (c == '-' || c == '*' || c == '.') return pos + 1
        val run = when {
            c in '0'..'9' -> runOf(line, pos) { it in '0'..'9' }
            c in ROMAN_LOWER -> maxOf(1, runOf(line, pos) { it in ROMAN_LOWER })
            c in ROMAN_UPPER -> maxOf(1, runOf(line, pos) { it in ROMAN_UPPER })
            c in 'a'..'z' || c in 'A'..'Z' -> 1
            else -> return -1
        }
        val end = pos + run
        return if (end < line.length && (line[end] == '.' || line[end] == ')')) end + 1 else -1
    }

    /** End of a task box (`[ ]`, `[x]`, `[X]`, `[_]`, `[>]`, `[?]`, `[-]`) at [pos], or -1. */
    private fun task(line: String, pos: Int): Int =
        if (pos + 2 < line.length && line[pos] == '[' && line[pos + 1] in TASK_STATES && line[pos + 2] == ']') {
            pos + 3
        } else {
            -1
        }

    /**
     * End of the container prefix a fence opener may sit behind: quote markers,
     * a description body `: `, list markers with an optional task box.
     */
    private fun fencePrefix(line: String, from: Int): Int {
        var pos = from
        while (pos < line.length) {
            val c = line[pos]
            if (c == '>') {
                pos = skipBlanks(line, pos + 1)
                continue
            }
            if (c == ':') {
                val next = skipBlanks(line, pos + 1)
                if (next == pos + 1) break
                pos = next
                continue
            }
            val end = marker(line, pos)
            if (end < 0) break
            val next = skipBlanks(line, end)
            if (next == end) break
            pos = next
            val afterTask = task(line, pos)
            if (afterTask >= 0) {
                val afterBlanks = skipBlanks(line, afterTask)
                if (afterBlanks > afterTask) pos = afterBlanks
            }
        }
        return pos
    }

    private inline fun runOf(line: String, from: Int, accept: (Char) -> Boolean): Int {
        var end = from
        while (end < line.length && accept(line[end])) end++
        return end - from
    }

    private fun skipBlanks(line: String, from: Int): Int = from + runOf(line, from) { it == ' ' || it == '\t' }

    private const val ROMAN_LOWER = "ivxlcdm"
    private const val ROMAN_UPPER = "IVXLCDM"
    private const val TASK_STATES = " xX_>?-"

    /**
     * Whether `line` (0-based) sits inside a backtick or tilde code fence.
     *
     * A line scan, not a parse: it ignores container rules (an indented fence at
     * the document level counts as a fence), so an unusual shape can be misread.
     * A misread costs one flicker or one deferred render, never the output.
     */
    fun isInsideCodeFence(lines: List<String>, line: Int): Boolean {
        var open: String? = null
        var i = 0
        while (i < line && i < lines.size) {
            open = stepCodeFence(open, lines[i])
            i++
        }
        return open != null
    }

    /** For each line, whether it sits inside a code fence; one pass over the document. */
    fun codeFenceMask(lines: List<String>): List<Boolean> {
        val mask = ArrayList<Boolean>(lines.size)
        var open: String? = null
        for (line in lines) {
            mask += open != null
            open = stepCodeFence(open, line)
        }
        return mask
    }

    /** The open fence run after `line`, given the one open before it. */
    private fun stepCodeFence(open: String?, line: String): String? {
        val lead = skipBlanks(line, 0)
        val start = fencePrefix(line, lead)
        if (start >= line.length || (line[start] != '`' && line[start] != '~')) return open
        val runLength = runOf(line, start) { it == line[start] }
        if (runLength < 3) return open
        val run = line.substring(start, start + runLength)
        val rest = line.substring(start + runLength)
        if (open == null) {
            // A fence character after the run makes it inline code: ```code```.
            return if (rest.contains(run[0])) null else run
        }
        val prefix = line.substring(lead, start)
        if (prefix.all { it == ' ' || it == '\t' || it == '>' } && run[0] == open[0] &&
            run.length >= open.length && rest.isBlank()
        ) {
            return null
        }
        return open
    }

    /** Whether a render should wait while the caret is on `line` of `text`. */
    fun shouldHoldRender(text: String, line: Int): Boolean {
        val lines = text.split(LINE_BREAK)
        if (line < 0 || line >= lines.size) return false
        return isBareListMarker(lines[line]) && !isInsideCodeFence(lines, line)
    }
}
