package org.markupcarve.carve

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the bundled carve.iife.js renders correctly on GraalJS - the same
 * path the preview and HTML export use. No external dependency required.
 */
class CarveConverterTest {

    @Test
    fun testBasicConversion() {
        val html = CarveConverter.toHtml("# Hello World\n\nThis is *bold* and /italic/.")
        assertTrue("Should contain h1: $html", html.contains("<h1>"))
        assertTrue("Should contain strong: $html", html.contains("<strong>bold</strong>"))
        assertTrue("Should contain em: $html", html.contains("<em>italic</em>"))
    }

    @Test
    fun testParagraphInterruption() {
        // Post-Markdown default: a bullet line interrupts the open paragraph.
        val html = CarveConverter.toHtml("intro\n- item")
        assertTrue("Paragraph should close before list: $html", html.contains("<p>intro</p>"))
        assertTrue("List should render: $html", html.contains("<li>item</li>"))
    }

    @Test
    fun testTableConversion() {
        val carve = """
            | Header 1 | Header 2 |
            |----------|----------|
            | Cell 1   | Cell 2   |
        """.trimIndent()
        val html = CarveConverter.toHtml(carve)
        assertTrue("Should contain table: $html", html.contains("<table"))
        assertTrue("Should contain td: $html", html.contains("<td"))
    }

    @Test
    fun testCodeBlock() {
        val carve = "``` kotlin\nfun main() {}\n```"
        val html = CarveConverter.toHtml(carve)
        assertTrue("Should contain pre: $html", html.contains("<pre"))
        assertTrue("Should contain code: $html", html.contains("<code"))
    }
}
