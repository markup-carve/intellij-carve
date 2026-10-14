package org.markupcarve.carve.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.markupcarve.carve.lsp.CarveEnterEdits.Range

class CarveEnterEditsTest {

    private fun check(text: String, caret: Int, edits: List<Range>, expected: String) {
        val after = CarveEnterEdits.applyTo(text, edits)!!
        val target = CarveEnterEdits.caretAfter(caret, edits)
        assertEquals(expected, after.substring(0, target) + "|" + after.substring(target))
    }

    @Test
    fun `a closer inserted at the caret lands after it`() {
        check("::: note\n", 9, listOf(Range(9, 9, "\n:::")), "::: note\n|\n:::")
    }

    @Test
    fun `a container lead goes before the caret and its closer after`() {
        check("> ::: note\n", 11, listOf(Range(11, 11, "> \n> :::")), "> ::: note\n> |\n> :::")
    }

    @Test
    fun `split edits at one offset keep their order`() {
        check("> ::: note\n", 11, listOf(Range(11, 11, "> "), Range(11, 11, "\n> :::")), "> ::: note\n> |\n> :::")
    }

    @Test
    fun `a closer on the next line leaves the caret alone`() {
        check("```js\n\nrest", 6, listOf(Range(7, 7, "```\n")), "```js\n|\n```\nrest")
    }

    @Test
    fun `a replacement ending at the caret moves it to the end of the new text`() {
        check("- a\n  ", 6, listOf(Range(4, 6, "- ")), "- a\n- |")
    }

    @Test
    fun `an edit before the caret shifts it`() {
        check("- \n", 3, listOf(Range(0, 2, "")), "\n|")
    }

    @Test
    fun `overlapping edits are refused`() {
        assertNull(CarveEnterEdits.applicationOrder(listOf(Range(0, 3, "x"), Range(2, 4, "y"))))
    }
}
