package org.markupcarve.carve.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The preview page reaches nothing but the plugin's own files.
 *
 * Until 0.1.6 the scaffold pulled six assets from `cdn.jsdelivr.net` at render
 * time, so every preview made outbound requests from the user's IDE and every
 * preview without a network silently lost code colours, charts, math and
 * diagrams. Nothing failed when that happened - the page just rendered plainer -
 * which is precisely why it needs a test rather than a look.
 *
 * Two halves, and both are needed:
 *
 *  * **No remote URL in the generated page.** A test on the source file alone
 *    would pass a scaffold that built `"htt" + "ps://..."`, and a test that only
 *    asserted the vendored files exist would pass a page pointing at the wrong
 *    path.
 *  * **Every local URL resolves.** Each `src`/`href` is walked back to a real
 *    file under the extraction root, so a typo in an asset path fails here
 *    instead of showing up as an unstyled preview.
 *
 * What this cannot check is whether the page then RENDERS - that needs a
 * browser. `tools/preview-offline-probe.mjs` does it, driving Chromium with
 * every non-`file://` request aborted; see docs/development.md.
 */
class CarvePreviewHtmlTest {

    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder()

    /** Unpacked once per test: 5.2 MB, and several assertions build more than one page. */
    private val assetRoot: File by lazy { CarvePreviewAssets.extractTo(temp.newFolder("assets")) }

    private fun page(
        content: String = "",
        isDark: Boolean = false,
        userCss: String = "",
        copyBridge: String = "",
    ): String = CarvePreviewHtml.create(
        initialHtml = content,
        isDark = isDark,
        assetBase = CarvePreviewAssets.baseUrl(assetRoot),
        carveCss = "",
        userCss = userCss,
        copyBridge = copyBridge,
    )

    /** `src="..."` / `href="..."` values, however the attribute is quoted. */
    private fun assetUrls(html: String): List<String> =
        Regex("""\b(?:src|href)\s*=\s*"([^"]*)"""").findAll(html).map { it.groupValues[1] }.toList()

    @Test
    fun `the scaffold references no remote URL`() {
        for (dark in listOf(false, true)) {
            val html = page(isDark = dark)
            for (marker in listOf("https://", "http://", "cdn.", "//cdn", "jsdelivr", "unpkg", "cdnjs")) {
                assertFalse(
                    "the preview scaffold (dark=$dark) contains '$marker'. Every browser asset is " +
                        "vendored under resources/preview-assets and referenced by file:// URL; a " +
                        "remote reference makes the preview fail offline and phone home from the " +
                        "user's IDE. See tools/vendor-preview-assets.sh.",
                    html.contains(marker),
                )
            }
        }
    }

    /**
     * The content is the user's, not ours - it may legitimately contain
     * `https://` links - so the guard above is scoped to the scaffold. This pins
     * that scoping, so nobody later "fixes" the test by scanning the whole page
     * and then has to weaken it.
     */
    @Test
    fun `a remote link in the document is left alone`() {
        val html = page(content = """<p><a href="https://example.com">x</a></p>""")
        assertTrue(html.contains("""<a href="https://example.com">x</a>"""))
    }

    @Test
    fun `every asset the page loads resolves to a file that exists`() {
        val root = assetRoot
        val base = CarvePreviewAssets.baseUrl(root)
        val html = CarvePreviewHtml.create("", false, base)

        val urls = assetUrls(html)
        assertTrue("the scaffold loads no assets at all - it should load six", urls.isNotEmpty())
        for (url in urls) {
            assertTrue("asset URL is not a file:// URL: $url", url.startsWith("file:"))
            val file = File(java.net.URI(url))
            assertTrue("the preview points at $url, which does not exist", file.isFile)
            assertTrue("the preview points at an empty file: $url", file.length() > 0)
        }
        assertEquals(
            "the page no longer loads exactly the assets CarvePreviewAssets.REFERENCED lists",
            CarvePreviewAssets.REFERENCED.sorted(),
            urls.map { it.removePrefix(base) }.sorted(),
        )
    }

    @Test
    fun `the highlight theme follows the editor theme`() {
        val light = page(isDark = false)
        assertTrue(light.contains("""${CarvePreviewAssets.HIGHLIGHT_DARK_CSS}" disabled"""))
        assertFalse(light.contains("""${CarvePreviewAssets.HIGHLIGHT_LIGHT_CSS}" disabled"""))

        val dark = page(isDark = true)
        assertTrue(dark.contains("""${CarvePreviewAssets.HIGHLIGHT_LIGHT_CSS}" disabled"""))
        assertTrue(dark.contains("""<html data-theme="dark">"""))
    }

    /**
     * The palette is the tokens', full stop. Two palettes in one document was
     * the actual defect: `tokens.css` was injected and referenced zero times
     * while the scaffold hardcoded its own colours and restated them in a
     * parallel `body.dark` block that had to be maintained by hand.
     */
    @Test
    fun `the base styles are written in tokens, not in a second palette`() {
        val html = page()
        for (token in listOf(
            "--carve-surface",
            "--carve-sunk",
            "--carve-ink",
            "--carve-ink-soft",
            "--carve-rule",
            "--carve-accent",
        )) {
            assertTrue("the scaffold never uses $token", html.contains("var($token)"))
        }
        for (dead in listOf("#2c3e50", "#3498db", "#f4f4f4", "#bdc3c7", "#34495e", "#7f8c8d", "body.dark")) {
            assertFalse(
                "the scaffold still carries the old hardcoded palette ($dead). Dark mode comes " +
                    "from data-theme on the root element, which is what tokens.css keys off.",
                html.contains(dead),
            )
        }
    }

    /**
     * The heaviness the code blocks were reported for was two stacked surfaces:
     * the scaffold painted the `<pre>` and the highlight.js theme painted
     * `pre code.hljs` a slightly different shade with 1em of its own padding,
     * leaving an inset frame. Both halves of the fix are pinned here, because
     * either one alone brings it back.
     */
    @Test
    fun `a code block is one flat surface`() {
        val html = page()
        val themeAt = html.indexOf(CarvePreviewAssets.HIGHLIGHT_LIGHT_CSS)
        val resetAt = html.indexOf("pre code, pre code.hljs")
        assertTrue("the hljs theme link is missing", themeAt >= 0)
        assertTrue("the hljs background reset is missing", resetAt >= 0)
        assertTrue(
            "the highlight.js theme is loaded AFTER the scaffold's own rules, so its " +
                "`pre code.hljs { background; padding: 1em }` wins again and the inset frame is back",
            themeAt < resetAt,
        )
        assertFalse("the scaffold re-paints hljs code backgrounds", html.contains("pre code.hljs { background: #"))
    }

    @Test
    fun `code blocks get a copy button wired to the clipboard`() {
        val html = page()
        assertTrue("no copy button is created", html.contains("class = 'carve-copy'") || html.contains("'carve-copy'"))
        assertTrue("the copy button is never styled", html.contains(".carve-copy"))
        // All three routes, in order. Dropping the fallbacks would leave the
        // exported/bridge-less case with a button that does nothing.
        assertTrue("no IDE bridge call", html.contains("window.carveCopyText"))
        assertTrue("no navigator.clipboard path", html.contains("navigator.clipboard"))
        assertTrue("no execCommand fallback", html.contains("document.execCommand('copy')"))
        assertTrue("the button never reports failure", html.contains("'failed'"))
    }

    @Test
    fun `the copy bridge is only injected when there is one`() {
        assertFalse(page().contains("window.carveCopyText = function"))
        val bridged = page(copyBridge = "window.cefQuery_test({request: text});")
        assertTrue(bridged.contains("window.carveCopyText = function"))
        assertTrue(bridged.contains("window.cefQuery_test({request: text});"))
    }

    @Test
    fun `user CSS is injected last so it can override`() {
        val html = page(userCss = ".mine { color: red; }")
        val carveAt = html.indexOf("""<style id="carve-css">""")
        val userAt = html.indexOf("""<style id="carve-user-css">""")
        assertTrue("user CSS is not injected", userAt > 0)
        assertTrue("user CSS is injected before the built-in layers", carveAt < userAt)
        assertFalse("an empty custom stylesheet still emits a tag", page().contains("carve-user-css"))
    }

    /**
     * Writes the real page next to the build output so
     * `tools/preview-offline-probe.mjs` can load the very bytes the plugin
     * produces, rather than a hand-written approximation of them. A probe
     * against a copy would prove nothing about this scaffold.
     */
    @Test
    fun `the probe page is written for the browser check`() {
        val root = CarvePreviewAssets.extractTo(File("build/preview-probe/assets"))
        val out = File("build/preview-probe/index.html")
        out.parentFile.mkdirs()
        out.writeText(
            CarvePreviewHtml.create(
                initialHtml = PROBE_DOCUMENT,
                isDark = false,
                assetBase = CarvePreviewAssets.baseUrl(root),
                carveCss = File("src/main/resources/css/tokens.css").readText() + "\n" +
                    File("src/main/resources/css/recipes.css").readText(),
            ),
        )
        assertTrue(out.isFile)
    }

    /**
     * The generated-page check above cannot see a URL on a branch it does not
     * take, and the point of this change is that the preview never reaches the
     * network on ANY path. So the sources are scanned too. `ExportHtmlAction` is
     * deliberately out of scope: it writes a standalone file meant to be opened
     * and shared anywhere, where a CDN reference is the right call.
     */
    @Test
    fun `no preview source reintroduces a CDN reference`() {
        val sources = File("src/main/kotlin/org/markupcarve/carve/preview")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        assertTrue("no preview sources found - did the package move?", sources.isNotEmpty())
        for (source in sources) {
            // Comment-only lines are dropped, so the class docs may keep naming the
            // CDN this change removed. Stripping every `//` instead would strip the
            // `//` out of `https://` too and gut the check - the exact shape of dead
            // check catalogued in markup-carve/carve#755.
            val text = source.readLines()
                .filterNot { line ->
                    val t = line.trimStart()
                    t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
                }
                .joinToString("\n")
            for (marker in listOf("https://", "http://", "cdn.jsdelivr", "unpkg.com", "cdnjs.")) {
                assertFalse(
                    "${'$'}{source.name} references '${'$'}marker'. The preview loads only assets vendored " +
                        "under resources/preview-assets; add it there with tools/vendor-preview-assets.sh " +
                        "instead of reaching for a CDN.",
                    text.contains(marker),
                )
            }
        }
    }

    companion object {
        /**
         * One of each construct that used to need the network: a highlighted
         * code block, a chart, inline and display math, and a diagram.
         */
        val PROBE_DOCUMENT: String = """
            <h1 id="probe">Offline probe</h1>
            <p>Inline math \(x^2 + \sqrt{y}\) and display math:</p>
            <p>\[\int_0^1 x^2\,dx = \frac{1}{3}\]</p>
            <pre title="app.js"><code class="language-javascript">const answer = 42; // the comment
            function add(a, b) { return a + b; }</code></pre>
            <pre class="mermaid">graph TD; A[Start] --&gt; B[End];</pre>
            <div class="chart"><script type="application/json">
            {"type":"bar","data":{"labels":["a","b"],"datasets":[{"label":"n","data":[1,2]}]},
             "options":{"animation":false,"responsive":false}}
            </script></div>
        """.trimIndent()
    }
}
