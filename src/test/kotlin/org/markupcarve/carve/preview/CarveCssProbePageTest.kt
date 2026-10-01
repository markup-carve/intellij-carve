package org.markupcarve.carve.preview

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Writes the page `tools/preview-css-probe.mjs` loads.
 *
 * Kept apart from [CarvePreviewHtmlTest]'s offline probe page because the two
 * ask different questions of the same browser: that one takes the network away
 * and checks the four things a CDN outage used to erase, this one reads computed
 * styles for the stylesheet gaps in #224. A rule that looks right and never
 * applies is invisible to any assertion on the CSS text, and the preview is a
 * JCEF browser, so a computed style is the measurement that matches it.
 *
 * The body is engine output, not hand-shaped HTML: a promoted block image is a
 * bare `<img>` with no wrapper, which is exactly why it needed a rule.
 */
class CarveCssProbePageTest {

    @Test
    fun `the css probe page is written for the browser check`() {
        val root = CarvePreviewAssets.extractTo(File("build/preview-css-probe/assets"))
        val out = File("build/preview-css-probe/index.html")
        out.parentFile.mkdirs()
        out.writeText(
            CarvePreviewHtml.create(
                initialHtml = PROBE_BODY,
                isDark = false,
                assetBase = CarvePreviewAssets.baseUrl(root),
                carveCss = File("src/main/resources/css/tokens.css").readText() + "\n" +
                    File("src/main/resources/css/recipes.css").readText(),
            ),
        )
        assertTrue(out.isFile)
    }

    private companion object {
        /** A 40x40 local image, so the probe can measure boxes without a request. */
        const val PIXEL =
            "data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='40' height='40'>" +
                "<rect width='40' height='40' fill='%23888'/></svg>"

        val PROBE_BODY = """
            <img src="$PIXEL" alt="A cat">
            <img src="$PIXEL" alt="A dog">
            <p id="inline-image">Beside <img src="$PIXEL" alt="A bird"> the text.</p>
            <div id="tiles" class="gallery">
              <figure><img src="$PIXEL" alt="A tile"><figcaption>A tile</figcaption></figure>
              <div class="deep"><div><img src="$PIXEL" alt="Nested deeper"></div></div>
            </div>
            <p id="highlight">A <mark>highlight with <a href="#x">a link</a></mark> in it.</p>
        """.trimIndent()
    }
}
