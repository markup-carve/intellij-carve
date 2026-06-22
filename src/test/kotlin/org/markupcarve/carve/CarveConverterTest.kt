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
        // Per the Carve spec (grammar PART 9 section 10), a list marker does NOT
        // interrupt an open paragraph - a bullet needs a blank line before it; an
        // indented bullet right after a prose line folds in as lazy continuation.
        // A blank line is what actually opens the list.
        val foldedIn = CarveConverter.toHtml("intro\n- item")
        assertTrue("Bullet should fold into paragraph (no interrupt): $foldedIn", foldedIn.contains("<p>"))
        assertTrue("No list when bullet folds in: $foldedIn", !foldedIn.contains("<ul>"))

        val separated = CarveConverter.toHtml("intro\n\n- item")
        assertTrue("Paragraph should close before list: $separated", separated.contains("<p>intro</p>"))
        assertTrue("List should render after blank line: $separated", separated.contains("<li>item</li>"))
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

    @Test
    fun testListTableExtension() {
        // listTable extension: a `::: list-table` div renders as a real <table>.
        val carve = "::: list-table\n- - A\n  - B\n:::"
        val html = CarveConverter.toHtml(carve)
        assertTrue("list-table should render a table: $html", html.contains("<table"))
        assertTrue("list-table cell should render: $html", html.contains("A"))
    }

    @Test
    fun testMathBlockExtension() {
        // mathBlock extension: a ```math fence renders display math markup.
        val carve = "```math\nx^2\n```"
        val html = CarveConverter.toHtml(carve)
        assertTrue("math block should render math markup: $html", html.contains("math"))
    }

    @Test
    fun testDetailsExtension() {
        // details extension: a `::: details "Title"` div renders a disclosure widget.
        val carve = "::: details \"More\"\nhidden\n:::"
        val html = CarveConverter.toHtml(carve)
        assertTrue("details should render <details>: $html", html.contains("<details"))
        assertTrue("details should render <summary>: $html", html.contains("<summary"))
    }

    @Test
    fun testImplicitHeadingReference() {
        // The plugin's cwiki template documents `[[Heading]]` as an implicit
        // heading reference, so it must resolve to the local heading id (#id),
        // not render as an off-document wiki page link.
        val html = CarveConverter.toHtml("# Home\n\nSee [[Home]].")
        assertTrue("[[Heading]] should resolve to local anchor: $html", html.contains("href=\"#Home\""))
        assertTrue("[[Heading]] should not be a wiki page link: $html", !html.contains("class=\"wikilink\""))
    }

    @Test
    fun testBlockHeaderOnFence() {
        // #201 block header: ```lang "title" is core (no extension) and renders a title.
        val carve = "```js \"hello.js\"\nconst x = 1\n```"
        val html = CarveConverter.toHtml(carve)
        assertTrue("fence block header should render a title attribute: $html", html.contains("hello.js"))
    }
}
