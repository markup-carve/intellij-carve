package org.markupcarve.carve

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.xml.parsers.DocumentBuilderFactory

/** Live templates expand to canonical Carve that renders as the construct they name. */
class CarveLiveTemplatesTest {

    private val templates: Map<String, String> by lazy {
        val stream = javaClass.getResourceAsStream("/liveTemplates/Carve.xml")!!
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream)
            .getElementsByTagName("template")
        (0 until nodes.length).associate { i ->
            val el = nodes.item(i) as org.w3c.dom.Element
            el.getAttribute("name") to el.getAttribute("value")
        }
    }

    private fun expand(name: String, vararg vars: Pair<String, String>): String {
        var text = templates.getValue(name).replace("\$END\$", "")
        for ((key, value) in vars) text = text.replace("\$$key\$", value)
        return text
    }

    @Test
    fun tableTemplateWritesANativeHeaderRow() {
        val source = expand("ctable", "H1" to "Name", "H2" to "Qty", "C1" to "Ada", "C2" to "3")
        assertEquals("|= Name |= Qty |\n| Ada | 3 |\n", source)
        val html = CarveConverter.toHtml(source)
        assertTrue(html, html.contains("<thead>"))
        assertTrue(html, html.contains("<th scope=\"col\">Name</th>"))
    }

    @Test
    fun ruleTemplateIsABreakEvenOnTheFirstLine() {
        // A `---` on line 1 opens frontmatter (carve#5016); `***` is the canonical break.
        val html = CarveConverter.toHtml(expand("chr") + "\ntext\n")
        assertTrue(html, html.contains("<hr>"))
    }

    @Test
    fun codeTemplateGluesTheLanguageToTheFence() {
        val source = expand("ccode", "LANG" to "kotlin", "BODY" to "val x = 1")
        assertTrue(source, source.startsWith("```kotlin\n"))
        val html = CarveConverter.toHtml(source)
        assertTrue(html, html.contains("class=\"language-kotlin\""))
    }
}
