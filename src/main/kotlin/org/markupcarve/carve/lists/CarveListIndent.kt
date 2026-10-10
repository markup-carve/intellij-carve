package org.markupcarve.carve.lists

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * The pure half of Tab / Shift+Tab on list items: which lines are list items,
 * what a `carve.listIndent` answer means, and when the key keeps its default.
 * Free of IDE and LSP4IJ types so it loads and tests without either.
 * Mirrors vscode-carve's `src/list-indent.ts`.
 */
object CarveListIndent {

    const val COMMAND = "carve.listIndent"

    enum class Direction(val wire: String) { INDENT("indent"), OUTDENT("outdent") }

    data class Position(val line: Int, val character: Int) : Comparable<Position> {
        override fun compareTo(other: Position): Int =
            compareValuesBy(this, other, Position::line, Position::character)
    }

    /** A caret selection; `start` is the anchor side, `end` the caret side, in either order. */
    data class Selection(val start: Position, val end: Position)

    data class TextEdit(val start: Position, val end: Position, val newText: String)

    private const val MARKER =
        """(?:[-*]|[0-9]+[.)]|[A-Za-z][.)]|[ivxlcdm]{2,}[.)]|[IVXLCDM]{2,}[.)]|\.)"""
    private const val TASK = """\[[ xX_>?-]\]"""

    // A marker followed by a space or the end of the line, so `-a` and `1.5` stay text.
    // `+` is the continuation marker, not a bullet.
    private val LIST_ITEM = Regex("""^[ \t]*(?:>[ \t]*)*$MARKER(?: |$)""")
    private val THEMATIC_BREAK = Regex("""^[ \t]*(?:>[ \t]*)*([-*_])(?:[ \t]*\1){2,}[ \t]*$""")

    // An opener may sit on a list or description marker line, a closer may not.
    private val FENCE =
        Regex("""^[ \t]*((?:>[ \t]*|:[ \t]+|$MARKER[ \t]+(?:$TASK[ \t]+)?)*)(`{3,}|~{3,})(.*)$""")
    private val CLOSER_PREFIX = Regex("""^[ \t>]*$""")
    private val LINE_BREAK = Regex("""\r\n|\r|\n""")

    fun isListItemLine(line: String): Boolean =
        LIST_ITEM.containsMatchIn(line) && !THEMATIC_BREAK.matches(line)

    /** For each line, whether it sits inside a backtick or tilde code fence. A line scan, not a parse. */
    fun codeFenceMask(lines: List<String>): BooleanArray {
        val mask = BooleanArray(lines.size)
        var open: String? = null
        lines.forEachIndexed { index, line ->
            mask[index] = open != null
            open = stepCodeFence(open, line)
        }
        return mask
    }

    private fun stepCodeFence(open: String?, line: String): String? {
        val match = FENCE.matchEntire(line) ?: return open
        val (prefix, run, rest) = match.destructured
        if (open == null) {
            // A fence character after the run makes it inline code: ```code```.
            return if (rest.contains(run[0])) null else run
        }
        val closes = CLOSER_PREFIX.matches(prefix) && run[0] == open[0] &&
            run.length >= open.length && rest.isBlank()
        return if (closes) null else open
    }

    /**
     * The list item lines the selections touch, outside code fences, ascending.
     * A selection ending at column 0 of a later line leaves that line out. Empty
     * when any selection does not start on a list item line: a mixed selection
     * keeps the default key for every caret rather than moving only some lines.
     * Non-item lines inside a selection are item content the server moves along.
     */
    fun selectedListLines(text: String, selections: List<Selection>): List<Int> {
        if (selections.isEmpty()) return emptyList()
        val lines = text.split(LINE_BREAK)
        val fenced by lazy { codeFenceMask(lines) }
        fun isItem(line: Int): Boolean =
            line in lines.indices && isListItemLine(lines[line]) && !fenced[line]

        val wanted = sortedSetOf<Int>()
        for ((start, end) in selections) {
            val from = minOf(start.line, end.line)
            var to = maxOf(start.line, end.line)
            val last = if (start.line > end.line) start else end
            if (to > from && last.character == 0) to--
            if (!isItem(from)) return emptyList()
            for (line in from..to) if (isItem(line)) wanted.add(line)
        }
        return wanted.toList()
    }

    /** The `executeCommand` arguments for one line. */
    fun arguments(uri: String, line: Int, direction: Direction): JsonObject = JsonObject().apply {
        addProperty("uri", uri)
        addProperty("line", line)
        addProperty("direction", direction.wire)
    }

    /**
     * The edits for [uri] in a `carve.listIndent` answer, or none. Accepts a
     * `TextEdit[]` or a `WorkspaceEdit`; anything else, including `null`, is no edit.
     */
    fun parseEdits(result: Any?, uri: String): List<TextEdit> {
        val json = when (result) {
            null -> return emptyList()
            is JsonElement -> result
            else -> Gson().toJsonTree(result)
        }
        if (json.isJsonArray) return json.asJsonArray.mapNotNull(::toTextEdit)
        if (!json.isJsonObject) return emptyList()
        val edit = json.asJsonObject
        val changes = edit.get("changes")
        if (changes != null && changes.isJsonObject) {
            return changes.asJsonObject.entrySet()
                .filter { (key, edits) -> sameUri(key, uri) && edits.isJsonArray }
                .flatMap { (_, edits) -> edits.asJsonArray.mapNotNull(::toTextEdit) }
        }
        val documentChanges = edit.get("documentChanges")
        if (documentChanges != null && documentChanges.isJsonArray) {
            return documentChanges.asJsonArray
                .filter { it.isJsonObject }
                .map { it.asJsonObject }
                .filter { change ->
                    val docUri = change.getAsJsonObject("textDocument")?.get("uri")
                    docUri != null && docUri.isJsonPrimitive && docUri.asJsonPrimitive.isString &&
                        sameUri(docUri.asString, uri) && change.get("edits")?.isJsonArray == true
                }
                .flatMap { change -> change.getAsJsonArray("edits").mapNotNull(::toTextEdit) }
        }
        return emptyList()
    }

    private fun toTextEdit(value: JsonElement): TextEdit? {
        if (!value.isJsonObject) return null
        val edit = value.asJsonObject
        val newText = edit.get("newText")
        if (newText == null || !newText.isJsonPrimitive || !newText.asJsonPrimitive.isString) return null
        val range = edit.get("range")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val start = toPosition(range.get("start")) ?: return null
        val end = toPosition(range.get("end")) ?: return null
        return TextEdit(start, end, newText.asString)
    }

    private fun toPosition(value: JsonElement?): Position? {
        if (value == null || !value.isJsonObject) return null
        val line = value.asJsonObject.get("line")
        val character = value.asJsonObject.get("character")
        if (line == null || character == null) return null
        if (!line.isJsonPrimitive || !line.asJsonPrimitive.isNumber) return null
        if (!character.isJsonPrimitive || !character.asJsonPrimitive.isNumber) return null
        return Position(line.asInt, character.asInt)
    }

    /** URI equality across percent-encoding and Windows drive-letter case. */
    private fun sameUri(a: String, b: String): Boolean = a == b || normalizeUri(a) == normalizeUri(b)

    private val DRIVE = Regex("""^file:///([A-Za-z]):""")

    private fun normalizeUri(uri: String): String {
        val decoded = try {
            URLDecoder.decode(uri.replace("+", "%2B"), StandardCharsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            uri
        }
        return DRIVE.replace(decoded) { "file:///${it.groupValues[1].lowercase()}:" }
    }

    /**
     * One edit from the per-line answers. Each answer was computed against the
     * same text, so an answer that overlaps an earlier one (a child line the
     * parent's move already carries) is dropped whole rather than applied twice.
     */
    fun mergeEdits(answers: List<List<TextEdit>>): List<TextEdit> {
        val taken = mutableListOf<TextEdit>()
        for (answer in answers) {
            if (answer.any { edit -> taken.any { overlaps(edit, it) } }) continue
            taken += answer
        }
        return taken
    }

    private fun overlaps(a: TextEdit, b: TextEdit): Boolean {
        // Two insertions at one point conflict too: their order would be a guess.
        if (a.start == b.start) return true
        return a.start < b.end && b.start < a.end
    }

    /** Whether the server advertises the command (carve-lsp 0.1.10 does not). */
    fun serverHasCommand(commands: List<String>?): Boolean = commands?.contains(COMMAND) == true

    /** What a key press does once the server has answered (or failed to). */
    enum class Outcome { APPLY, DEFAULT_KEY, NOTHING }

    /**
     * [stale]: the document or the carets changed while the server answered, so
     * the press no longer belongs to what is on screen and neither the edit nor
     * the default key applies.
     */
    fun outcome(edits: List<TextEdit>, stale: Boolean): Outcome = when {
        stale -> Outcome.NOTHING
        edits.isEmpty() -> Outcome.DEFAULT_KEY
        else -> Outcome.APPLY
    }

    /** Edits as (startOffset, endOffset, text), last first, so applying in order keeps offsets valid. */
    fun toOffsets(
        edits: List<TextEdit>,
        lineStart: (Int) -> Int,
        lineEnd: (Int) -> Int,
        lineCount: Int,
        textLength: Int,
    ): List<Triple<Int, Int, String>>? {
        fun offset(position: Position): Int? {
            if (position.line < 0 || position.character < 0) return null
            if (position.line >= lineCount) return textLength
            // A character past the end of the line means the end of that line, as LSP defines it.
            return minOf(lineStart(position.line) + position.character, lineEnd(position.line))
        }
        val ranges = edits.map { edit ->
            val start = offset(edit.start) ?: return null
            val end = offset(edit.end) ?: return null
            if (end < start) return null
            Triple(start, end, edit.newText)
        }
        return ranges.sortedWith(compareByDescending<Triple<Int, Int, String>> { it.first }.thenByDescending { it.second })
    }
}
