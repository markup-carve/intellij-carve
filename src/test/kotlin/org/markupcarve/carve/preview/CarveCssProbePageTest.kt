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
                carveCss = listOf("tokens", "recipes", "contrast", "extensions")
                    .joinToString("\n") { File("src/main/resources/css/$it.css").readText() },
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
            <div class="tabs" id="t3" role="group" aria-label="Tabs">
              <input type="radio" name="tabset-a" id="t3-tab-1" class="tabs-radio" checked>
              <label for="t3-tab-1" class="tabs-label">Alpha</label>
              <input type="radio" name="tabset-a" id="t3-tab-2" class="tabs-radio">
              <label for="t3-tab-2" class="tabs-label">Beta</label>
              <input type="radio" name="tabset-a" id="t3-tab-3" class="tabs-radio">
              <label for="t3-tab-3" class="tabs-label">Gamma</label>
              <div class="tabs-panel" id="t3-panel-1" role="group" aria-label="Alpha"><p>Alpha</p></div>
              <div class="tabs-panel" id="t3-panel-2" role="group" aria-label="Beta"><p>Beta</p></div>
              <div class="tabs-panel" id="t3-panel-3" role="group" aria-label="Gamma"><p>Gamma</p></div>
            </div>
            <div class="tabs" id="t13" role="group" aria-label="Tabs">
              <input type="radio" name="tabset-b" id="t13-tab-1" class="tabs-radio" checked>
              <label for="t13-tab-1" class="tabs-label">L1</label>
              <input type="radio" name="tabset-b" id="t13-tab-2" class="tabs-radio">
              <label for="t13-tab-2" class="tabs-label">L2</label>
              <input type="radio" name="tabset-b" id="t13-tab-3" class="tabs-radio">
              <label for="t13-tab-3" class="tabs-label">L3</label>
              <input type="radio" name="tabset-b" id="t13-tab-4" class="tabs-radio">
              <label for="t13-tab-4" class="tabs-label">L4</label>
              <input type="radio" name="tabset-b" id="t13-tab-5" class="tabs-radio">
              <label for="t13-tab-5" class="tabs-label">L5</label>
              <input type="radio" name="tabset-b" id="t13-tab-6" class="tabs-radio">
              <label for="t13-tab-6" class="tabs-label">L6</label>
              <input type="radio" name="tabset-b" id="t13-tab-7" class="tabs-radio">
              <label for="t13-tab-7" class="tabs-label">L7</label>
              <input type="radio" name="tabset-b" id="t13-tab-8" class="tabs-radio">
              <label for="t13-tab-8" class="tabs-label">L8</label>
              <input type="radio" name="tabset-b" id="t13-tab-9" class="tabs-radio">
              <label for="t13-tab-9" class="tabs-label">L9</label>
              <input type="radio" name="tabset-b" id="t13-tab-10" class="tabs-radio">
              <label for="t13-tab-10" class="tabs-label">L10</label>
              <input type="radio" name="tabset-b" id="t13-tab-11" class="tabs-radio">
              <label for="t13-tab-11" class="tabs-label">L11</label>
              <input type="radio" name="tabset-b" id="t13-tab-12" class="tabs-radio">
              <label for="t13-tab-12" class="tabs-label">L12</label>
              <input type="radio" name="tabset-b" id="t13-tab-13" class="tabs-radio">
              <label for="t13-tab-13" class="tabs-label">L13</label>
              <div class="tabs-panel" id="t13-panel-1" role="group" aria-label="L1"><p>L1</p></div>
              <div class="tabs-panel" id="t13-panel-2" role="group" aria-label="L2"><p>L2</p></div>
              <div class="tabs-panel" id="t13-panel-3" role="group" aria-label="L3"><p>L3</p></div>
              <div class="tabs-panel" id="t13-panel-4" role="group" aria-label="L4"><p>L4</p></div>
              <div class="tabs-panel" id="t13-panel-5" role="group" aria-label="L5"><p>L5</p></div>
              <div class="tabs-panel" id="t13-panel-6" role="group" aria-label="L6"><p>L6</p></div>
              <div class="tabs-panel" id="t13-panel-7" role="group" aria-label="L7"><p>L7</p></div>
              <div class="tabs-panel" id="t13-panel-8" role="group" aria-label="L8"><p>L8</p></div>
              <div class="tabs-panel" id="t13-panel-9" role="group" aria-label="L9"><p>L9</p></div>
              <div class="tabs-panel" id="t13-panel-10" role="group" aria-label="L10"><p>L10</p></div>
              <div class="tabs-panel" id="t13-panel-11" role="group" aria-label="L11"><p>L11</p></div>
              <div class="tabs-panel" id="t13-panel-12" role="group" aria-label="L12"><p>L12</p></div>
              <div class="tabs-panel" id="t13-panel-13" role="group" aria-label="L13"><p>L13</p></div>
            </div>
            <div class="tabs" id="tout" role="group" aria-label="Tabs">
              <input type="radio" name="tabset-out" id="tout-tab-1" class="tabs-radio" checked>
              <label for="tout-tab-1" class="tabs-label">Outer1</label>
              <input type="radio" name="tabset-out" id="tout-tab-2" class="tabs-radio">
              <label for="tout-tab-2" class="tabs-label">Outer2</label>
              <div class="tabs-panel" id="tout-panel-1" role="group" aria-label="Outer1">
              <div class="tabs" id="tin" role="group" aria-label="Tabs">
                <input type="radio" name="tabset-inner" id="tin-tab-1" class="tabs-radio" checked>
                <label for="tin-tab-1" class="tabs-label">Inner1</label>
                <input type="radio" name="tabset-inner" id="tin-tab-2" class="tabs-radio">
                <label for="tin-tab-2" class="tabs-label">Inner2</label>
                <div class="tabs-panel" id="tin-panel-1" role="group" aria-label="Inner1"><p>Inner1</p></div>
                <div class="tabs-panel" id="tin-panel-2" role="group" aria-label="Inner2"><p>Inner2</p></div>
              </div>
              </div>
              <div class="tabs-panel" id="tout-panel-2" role="group" aria-label="Outer2"><p>outer two</p></div>
            </div>
            <p id="inline-spoiler">A <span class="spoiler">hidden word</span> inline.</p>
            <details class="spoiler" id="block-spoiler"><summary>Hidden</summary><p>secret text</p></details>
        """.trimIndent()
    }
}
