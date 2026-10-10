package org.markupcarve.carve.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.markupcarve.carve.preview.CarvePreviewHold.codeFenceMask
import org.markupcarve.carve.preview.CarvePreviewHold.isBareListMarker
import org.markupcarve.carve.preview.CarvePreviewHold.isInsideCodeFence
import org.markupcarve.carve.preview.CarvePreviewHold.shouldHoldRender

/** Ported case for case from vscode-carve's `preview-hold.test.ts`. */
class CarvePreviewHoldTest {

    private val bare = listOf(
        "-", "- ", "*", "* ", "-\t",
        "1.", "2. ", "10)", "a.", "B)", "i.", "iv) ", "XII.", ".", ". ",
        "- [ ]", "- [ ] ", "* [x]", "- [X]",
        "  - ", "    1. ", "> - ", "> > 1.",
    )

    private val notBare = listOf(
        "", " ", "+", "+ ", "- a", "1. one", "- [ ] todo", "-a", "--", "---", "* * *", "- - -",
        "ab.", "1", "a", "-[ ]", "text -", "> ", "- [y]",
    )

    @Test
    fun `bare markers`() {
        for (line in bare) assertTrue("bare marker: \"$line\"", isBareListMarker(line))
    }

    @Test
    fun `not bare markers`() {
        for (line in notBare) assertFalse("not a bare marker: \"$line\"", isBareListMarker(line))
    }

    @Test
    fun `a fence opener puts the following lines inside the fence until its closer`() {
        val lines = listOf("- a", "```", "- ", "```", "- ")
        assertFalse(isInsideCodeFence(lines, 0))
        assertTrue(isInsideCodeFence(lines, 2))
        assertFalse(isInsideCodeFence(lines, 4))
    }

    @Test
    fun `a fence closes only on the same character, at least as long, with nothing after it`() {
        val lines = listOf("````", "~~~~", "``` js", "```", "-")
        assertTrue(isInsideCodeFence(lines, 4))
        assertFalse(isInsideCodeFence(listOf("````", "`````", "-"), 2))
    }

    @Test
    fun `a fence opened on a list-item line still counts`() {
        assertFalse(shouldHoldRender("- ```\n  - \n  ```\n", 1))
        assertFalse(shouldHoldRender("1. [ ] ~~~\n  1.\n", 1))
        assertFalse(isInsideCodeFence(listOf("- ```", "  x", "  ```", "- "), 3))
        assertFalse(shouldHoldRender("- - ```\n    - \n    ```\n", 1))
        assertFalse(shouldHoldRender("> - ```\n> - \n", 1))
    }

    @Test
    fun `a fence opened on a description body line still counts`() {
        assertFalse(shouldHoldRender(":: term\n: ```\n  - \n  ```\n", 2))
    }

    @Test
    fun `an inline code span is not a fence opener`() {
        assertTrue(shouldHoldRender("```code```\n\n- a\n- ", 3))
        assertFalse(isInsideCodeFence(listOf("~~~ a~b", "-"), 1))
    }

    @Test
    fun `a marker line is not a fence closer`() {
        assertTrue(isInsideCodeFence(listOf("```", "- ```", "-"), 2))
    }

    @Test
    fun `an unclosed fence runs to the end`() {
        assertTrue(isInsideCodeFence(listOf("~~~", "x", "y", "-"), 3))
    }

    @Test
    fun `the render holds on a bare marker typed under a list`() {
        val text = "- first\n- second\n- "
        assertTrue(shouldHoldRender(text, 2))
        assertFalse(shouldHoldRender(text, 1))
        assertFalse(shouldHoldRender("- first\n- second\n- t", 2))
    }

    @Test
    fun `the render does not hold on a marker inside a code fence`() {
        assertFalse(shouldHoldRender("```\n- \n```\n", 1))
    }

    @Test
    fun `the render does not hold for an out-of-range line`() {
        assertFalse(shouldHoldRender("- ", 3))
        assertFalse(shouldHoldRender("- ", -1))
    }

    @Test
    fun `CRLF line endings split like LF`() {
        assertTrue(shouldHoldRender("- a\r\n- \r\n", 1))
    }

    @Test
    fun `the fence mask agrees with the per-line check`() {
        val lines = listOf("- a", "```", "- b", "```", "~~~", "x", "~~~~", "- c", "````", "`````", "- d")
        val mask = codeFenceMask(lines)
        lines.indices.forEach { line -> assertEquals("line $line", isInsideCodeFence(lines, line), mask[line]) }
    }

    // Exponential in V8 with overlapping alpha/roman branches; a regex scan in Java
    // overflowed the stack on the long line instead.
    @Test(timeout = 2000)
    fun `a long run of markers scans in linear time without overflowing`() {
        for (line in listOf("i. ".repeat(60) + "x", "i. ".repeat(50_000) + "x", "> ".repeat(50_000) + "-")) {
            assertFalse(isInsideCodeFence(listOf(line, "-"), 1))
        }
        assertTrue(isInsideCodeFence(listOf("- ".repeat(50_000) + "```", "-"), 1))
        assertFalse(isBareListMarker("i. ".repeat(50_000) + "x"))
        assertTrue(isBareListMarker("> ".repeat(50_000) + "-"))
    }
}
