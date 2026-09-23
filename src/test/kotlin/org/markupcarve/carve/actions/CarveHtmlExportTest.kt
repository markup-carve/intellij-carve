package org.markupcarve.carve.actions

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.markupcarve.carve.preview.CarveCodeHighlight
import org.markupcarve.carve.preview.CarvePreviewAssets

class CarveHtmlExportTest {

    private val content = """<pre class="diff"><code class="language-js">-a
+b
</code></pre>"""

    private val page: String by lazy { CarveHtmlExport.page("sample", content) }

    /** Inline script bodies, cut where an HTML parser would cut them: at the first `</script`. */
    private fun inlineScripts(html: String): List<String> =
        Regex("""<script>(.*?)</script""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .findAll(html).map { it.groupValues[1] }.toList()

    private fun parses(js: String, name: String) {
        Context.newBuilder("js")
            .allowAllAccess(false)
            .option("engine.WarnInterpreterOnly", "false")
            .build()
            .use { it.parse(Source.newBuilder("js", js, name).build()) }
    }

    @Test
    fun `the page inlines highlight js`() {
        val core = CarvePreviewAssets.readText(CarvePreviewAssets.HIGHLIGHT_JS)
        assertTrue(page.contains(CarveHtmlExport.inlineScript(core)))
    }

    @Test
    fun `the page inlines the carve grammar with its crv alias`() {
        val grammar = CarvePreviewAssets.readText(CarvePreviewAssets.HIGHLIGHT_CARVE_JS)
        assertTrue(page.contains(CarveHtmlExport.inlineScript(grammar)))
        assertTrue(page.contains("aliases: ['carve', 'crv']"))
    }

    @Test
    fun `the grammar loads after the core it registers on`() {
        val core = page.indexOf(CarveHtmlExport.inlineScript(CarvePreviewAssets.readText(CarvePreviewAssets.HIGHLIGHT_JS)))
        val grammar = page.indexOf("root.hljs.registerLanguage('carve', carve)")
        val run = page.indexOf("carveHighlightCode(document);")
        assertTrue(core in 0 until grammar)
        assertTrue(grammar < run)
    }

    @Test
    fun `the page presents diff fences like the preview`() {
        assertTrue(page.contains("function renderLanguageDiff"))
        assertTrue(page.contains(CarveCodeHighlight.SCRIPT))
        assertTrue(page.contains(CarveCodeHighlight.DIFF_CSS))
        assertTrue(page.contains("carveHighlightCode(document);"))
    }

    @Test
    fun `the page inlines the light highlight theme`() {
        val theme = CarvePreviewAssets.readText(CarvePreviewAssets.HIGHLIGHT_LIGHT_CSS)
        assertTrue(page.contains(CarveHtmlExport.inlineStyle(theme)))
        assertTrue(page.contains("pre code, pre code.hljs { background: none; padding: 0; }"))
    }

    @Test
    fun `the page loads nothing from the plugin's asset directory`() {
        // The vendored payloads are inlined; strip them so only the scaffold's own
        // references are left to inspect.
        var scaffold = CarveHtmlExport.page("sample", "")
        for (asset in listOf(CarvePreviewAssets.HIGHLIGHT_JS, CarvePreviewAssets.HIGHLIGHT_CARVE_JS)) {
            scaffold = scaffold.replace(CarveHtmlExport.inlineScript(CarvePreviewAssets.readText(asset)), "")
        }
        val urls = Regex("""\b(?:src|href)\s*=\s*"([^"]*)"""").findAll(scaffold).map { it.groupValues[1] }.toList()
        assertEquals(listOf("https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js"), urls)
        assertFalse(scaffold.contains("file:"))
        assertFalse(scaffold.contains("preview-assets"))
    }

    @Test
    fun `every inline script parses`() {
        val scripts = inlineScripts(page)
        assertEquals("MathJax config, highlight.js, carve grammar, highlighter", 4, scripts.size)
        scripts.forEachIndexed { i, js ->
            parses(js, "inline-$i.js")
            // `<!--` then `<script` switches the HTML tokenizer to the double-escaped
            // state, where the real `</script>` no longer ends the element.
            val comment = js.indexOf("<!--")
            val opener = Regex("""<script[\t\n\u000C\r />]""", RegexOption.IGNORE_CASE)
            assertFalse(
                "inline script $i opens `<!--` and later contains a `<script` tag opener",
                comment >= 0 && opener.find(js, comment) != null,
            )
        }
    }

    @Test
    fun `a script end tag inside an inlined payload is escaped and still parses`() {
        val js = "var s = '</script><b>'; var t = '</SCRIPT>';"
        val inlined = CarveHtmlExport.inlineScript(js)
        assertFalse(inlined.contains("</script", ignoreCase = true))
        val scripts = inlineScripts("<script>$inlined</script>")
        assertEquals(listOf(inlined), scripts)
        parses(inlined, "escaped.js")
    }
}
