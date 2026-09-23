package org.markupcarve.carve

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CarveImportTest {

    @Test
    fun `markdown and html extensions are importable, case-insensitively`() {
        assertEquals(CarveImportFormat.MARKDOWN, CarveImportFormat.forExtension("md"))
        assertEquals(CarveImportFormat.MARKDOWN, CarveImportFormat.forExtension("MARKDOWN"))
        assertEquals(CarveImportFormat.HTML, CarveImportFormat.forExtension("html"))
        assertEquals(CarveImportFormat.HTML, CarveImportFormat.forExtension("Htm"))
    }

    @Test
    fun `other files are not importable`() {
        for (ext in listOf(null, "", "crv", "txt", "djot", "mdx")) {
            assertNull("'$ext' must not enable the import", CarveImportFormat.forExtension(ext))
        }
    }

    @Test
    fun `the target is a sibling crv with the same base name`() {
        assertEquals("README.crv", CarveImportFormat.targetName("README.md"))
        assertEquals("guide.v2.crv", CarveImportFormat.targetName("guide.v2.html"))
        assertEquals("notes.crv", CarveImportFormat.targetName("notes.MarkDown"))
    }

    @Test
    fun `no target for a file that is not importable`() {
        assertNull(CarveImportFormat.targetName("doc.crv"))
        assertNull(CarveImportFormat.targetName("Makefile"))
        assertNull(CarveImportFormat.targetName(".md"))
    }

    @Test
    fun `markdown converts through the bundled engine`() {
        val carve = CarveConverter.importToCarve("# T\n\n**b** _i_\n", CarveImportFormat.MARKDOWN).getOrThrow()
        assertEquals("# T\n\n*b* /i/\n", carve)
    }

    @Test
    fun `html converts through the bundled engine`() {
        val carve = CarveConverter.importToCarve(
            "<h1>T</h1><p><strong>b</strong> <em>i</em></p>",
            CarveImportFormat.HTML,
        ).getOrThrow()
        assertEquals("# T\n\n*b* /i/\n", carve)
    }

    @Test
    fun `a full markdown document imports as canonical carve`() {
        val markdown = """
            |# Title
            |
            |Some **bold**, *italic*, and `code` with a [link](https://example.com).
            |
            |## Lists
            |
            |- one
            |- two
            |  - nested
            |
            |1. first
            |2. second
            |
            |```js
            |const x = 1;
            |```
            |
            || Name | Value |
            ||------|-------|
            || a    | 1     |
            || b    | 2     |
            |
            |> quoted
            |
            |Line with ümlauts.
            |""".trimMargin()

        val carve = CarveConverter.importToCarve(markdown, CarveImportFormat.MARKDOWN).getOrThrow()

        assertEquals(
            """
            |# Title
            |
            |Some *bold*, /italic/, and `code` with a [link](https://example.com).
            |
            |## Lists
            |
            |- one
            |- two
            |  - nested
            |
            |1. first
            |2. second
            |
            |```js
            |const x = 1;
            |```
            |
            ||= Name |= Value |
            || a | 1 |
            || b | 2 |
            |
            |> quoted
            |
            |Line with ümlauts.
            |""".trimMargin(),
            carve,
        )
    }

    @Test
    fun `a full html document imports as canonical carve`() {
        val html = """
            <h1>Title</h1>
            <p>Some <strong>bold</strong>, <em>italic</em>, <code>code</code> and <a href="https://example.com">link</a>.</p>
            <ul><li>one</li><li>two</li></ul>
            <pre><code class="language-js">const x = 1;
            </code></pre>
            <table><thead><tr><th>Name</th><th>Value</th></tr></thead><tbody><tr><td>a</td><td>1</td></tr></tbody></table>
            <p>Ümlaut</p>
        """.trimIndent()

        val carve = CarveConverter.importToCarve(html, CarveImportFormat.HTML).getOrThrow()

        assertEquals(
            """
            |# Title
            |
            |Some *bold*, /italic/, `code` and [link](https://example.com).
            |
            |- one
            |- two
            |
            |```js
            |const x = 1;
            |```
            |
            ||= Name |= Value |
            || a | 1 |
            |
            |Ümlaut
            |""".trimMargin(),
            carve,
        )
    }

    @Test
    fun `the action is registered in the project view and tools menus`() {
        val pluginXml = File("src/main/resources/META-INF/plugin.xml").readText()
        val action = pluginXml.substringAfter("<action id=\"Carve.ImportAsCarve\"").substringBefore("</action>")
        assertTrue(action.contains("org.markupcarve.carve.actions.ImportAsCarveAction"))
        assertTrue(action.contains("group-id=\"ProjectViewPopupMenu\""))
        assertTrue(action.contains("group-id=\"ToolsMenu\""))
    }
}
