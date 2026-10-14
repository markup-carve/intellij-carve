package org.markupcarve.carve.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.markupcarve.carve.preview.CarvePreviewNavigation.Decision
import org.markupcarve.carve.settings.CarveSettings

class CarvePreviewNavigationTest {

    private val page = "file:///home/me/docs/preview.html"

    @Test
    fun `the shell itself and its fragments stay`() {
        assertEquals(Decision.ALLOW, CarvePreviewNavigation.decide(page, page, true, false))
        assertEquals(Decision.ALLOW, CarvePreviewNavigation.decide("$page#intro", page, true, true))
    }

    @Test
    fun `a web link opens in the system browser on a click only`() {
        assertEquals(Decision.OPEN_EXTERNALLY, CarvePreviewNavigation.decide("https://example.com/", page, true, true))
        assertEquals(Decision.BLOCK, CarvePreviewNavigation.decide("https://example.com/", page, true, false))
    }

    @Test
    fun `a meta refresh or a local file never replaces the shell`() {
        assertEquals(Decision.BLOCK, CarvePreviewNavigation.decide("file:///tmp/evil.html", page, true, false))
        assertEquals(Decision.BLOCK, CarvePreviewNavigation.decide("file:///tmp/evil.html", page, true, true))
        assertEquals(Decision.BLOCK, CarvePreviewNavigation.decide("javascript:alert(1)", page, true, true))
    }

    @Test
    fun `no sub frame loads`() {
        assertEquals(Decision.BLOCK, CarvePreviewNavigation.decide(page, page, false, true))
    }

    @Test
    fun `raw HTML needs the setting and a trusted project`() {
        assertFalse(CarveSettings.previewAllowsRawHtml(setting = false, projectTrusted = true))
        assertFalse(CarveSettings.previewAllowsRawHtml(setting = true, projectTrusted = false))
        assertTrue(CarveSettings.previewAllowsRawHtml(setting = true, projectTrusted = true))
    }

    @Test
    fun `raw HTML is off by default`() {
        assertFalse(CarveSettings.State().previewRawHtml)
    }
}
