package org.markupcarve.carve.includes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.markupcarve.carve.settings.CarveIncludeMode
import java.nio.file.Files
import java.nio.file.Path

class CarvePreviewIncludesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun document(): Path {
        val file = tmp.root.toPath().resolve("chapters/ch1.crv")
        Files.createDirectories(file.parent)
        Files.writeString(file, "# Chapter\n")
        return file
    }

    private fun setup(
        mode: CarveIncludeMode = CarveIncludeMode.AUTO,
        trusted: Boolean = true,
        settingRoot: String = "",
    ) = CarvePreviewIncludes.setupFor(
        mode = mode,
        workspaceTrusted = trusted,
        settingRoot = settingRoot,
        baseDirectories = listOf(tmp.root.toPath()),
        documentPath = document(),
    )

    @Test
    fun aTrustedProjectResolvesUnderTheProjectRoot() {
        val result = setup()
        assertNotNull(result)
        assertEquals(tmp.root.toPath().toRealPath(), result!!.root)
        assertEquals(document().toRealPath().toString(), result.documentId)
    }

    /**
     * Section 19 makes resolution opt-in, and `auto` is the server's own
     * default. A preview that expanded in an untrusted project would read files
     * the rest of the IDE has been told not to touch.
     */
    @Test
    fun anUntrustedProjectRendersTheDocumentAsWritten() {
        assertNull(setup(trusted = false))
    }

    @Test
    fun offRefusesEvenInATrustedProject() {
        assertNull(setup(mode = CarveIncludeMode.OFF))
    }

    @Test
    fun onResolvesEvenInAnUntrustedProject() {
        assertNotNull(setup(mode = CarveIncludeMode.ON, trusted = false))
    }

    /**
     * The one root section 19 names as never usable. A relative setting can only
     * be resolved against the process working directory, so it is refused rather
     * than resolved, and the preview then renders the document as written.
     */
    @Test
    fun aRelativeConfiguredRootLeavesThePreviewUnexpanded() {
        assertNull(setup(settingRoot = "."))
    }

    @Test
    fun aChangedResolvedTargetInvalidatesTheRender() {
        val child = tmp.root.toPath().resolve("shared/glossary.crv")
        Files.createDirectories(child.parent)
        Files.writeString(child, "Glossary.\n")
        val dependencies = listOf(CarveIncludeExpansion.Dependency(child.toRealPath().toString(), true, null))

        assertTrue(CarvePreviewIncludes.dependsOn(dependencies, child))
        assertFalse(
            CarvePreviewIncludes.dependsOn(dependencies, tmp.root.toPath().resolve("shared/other.crv")),
        )
    }

    /**
     * I11 names a missing target by where it WOULD be. Creating that file has to
     * invalidate, or the preview stays broken after the user fixes it.
     */
    @Test
    fun creatingAPreviouslyMissingTargetInvalidatesTheRender() {
        val absent = tmp.root.toPath().resolve("shared/absent.crv").normalize()
        val dependencies = listOf(CarveIncludeExpansion.Dependency(absent.toString(), false, "not-found"))

        assertTrue(CarvePreviewIncludes.dependsOn(dependencies, absent))
    }

    @Test
    fun aCleanExpansionAddsNothingAboveTheDocument() {
        assertEquals("", CarvePreviewIncludes.warningsHtml(emptyList(), 0))
    }

    /**
     * An unresolved target otherwise reaches the reader as ordinary prose: the
     * directive renders as the literal text it is.
     */
    @Test
    fun everyWarningIsShownWithItsRuleAndPosition() {
        val html = CarvePreviewIncludes.warningsHtml(
            listOf(
                CarveIncludeExpansion.Warning(
                    rule = "include-unresolved",
                    message = "Include \"../shared/absent.crv\" could not be resolved.",
                    line = 3,
                    column = 1,
                ),
            ),
            suppressed = 0,
        )
        assertTrue(html, html.contains("include-unresolved"))
        assertTrue(html, html.contains("line 3, column 1"))
        assertTrue(html, html.contains("../shared/absent.crv"))
        assertFalse("a suppressed count appeared with nothing suppressed: $html", html.contains("further"))
    }

    @Test
    fun aSuppressedRemainderIsReportedRatherThanDropped() {
        val html = CarvePreviewIncludes.warningsHtml(
            listOf(CarveIncludeExpansion.Warning("include-cycle", "Include cycle.", 1, 1)),
            suppressed = 7,
        )
        assertTrue(html, html.contains("7 further include warning(s) not shown."))
    }

    /** A warning carries a path the author wrote, so it reaches the page as text. */
    @Test
    fun aWarningMessageIsEscapedBeforeItReachesThePage() {
        val html = CarvePreviewIncludes.warningsHtml(
            listOf(CarveIncludeExpansion.Warning("include-unresolved", "Include \"<img src=x>\".", 1, 1)),
            suppressed = 0,
        )
        assertFalse("markup from a message reached the page raw: $html", html.contains("<img"))
        assertTrue(html, html.contains("&lt;img src=x&gt;"))
    }
}
