package org.markupcarve.carve.lsp

/**
 * Where the caret goes when Enter's on-type answer is applied. An insert at the caret
 * puts only its text before the first newline in front of the caret; the rest, a
 * `:::` or code fence closer, lands after it. LSP cannot say where the caret goes,
 * and LSP4IJ moves it past the whole insert, which parks it after the closer.
 */
object CarveEnterEdits {

    /** An edit as document offsets, all relative to the text before any edit. */
    data class Range(val start: Int, val end: Int, val text: String)

    /** The edits last offset first, inserts at one offset in reverse, so applying them in turn keeps the answer's order; null if two overlap. */
    fun applicationOrder(edits: List<Range>): List<Range>? {
        val ascending = edits.sortedBy { it.start }
        if (ascending.zipWithNext().any { (a, b) -> b.start < a.end }) return null
        return edits.withIndex()
            .sortedWith(compareByDescending<IndexedValue<Range>> { it.value.start }.thenByDescending { it.index })
            .map { it.value }
    }

    fun caretAfter(caret: Int, edits: List<Range>): Int {
        var target = caret
        var shift = 0
        for (edit in edits) {
            val delta = edit.text.length - (edit.end - edit.start)
            when {
                edit.start > caret -> Unit
                edit.start == caret && edit.end == caret -> shift += edit.text.substringBefore('\n').length
                edit.end <= caret -> shift += delta
                else -> {
                    // A replacement around the caret: it ends up after the new text.
                    target = edit.end
                    shift += delta
                }
            }
        }
        return target + shift
    }

    /** The document text after [edits], applied as [applicationOrder] orders them. */
    fun applyTo(text: String, edits: List<Range>): String? {
        val builder = StringBuilder(text)
        for (edit in applicationOrder(edits) ?: return null) builder.replace(edit.start, edit.end, edit.text)
        return builder.toString()
    }
}
