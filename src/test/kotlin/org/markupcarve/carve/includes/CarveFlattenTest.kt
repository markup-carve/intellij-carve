package org.markupcarve.carve.includes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.markupcarve.carve.CarveConverter
import java.awt.datatransfer.DataFlavor
import java.nio.file.Files
import java.nio.file.Path

/**
 * Flattening a document to one self-contained `.crv`, and what the two actions
 * that do it have to say afterwards.
 */
class CarveFlattenTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun write(relative: String, text: String): Path {
        val file = tmp.root.toPath().resolve(relative)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
        return file
    }

    private fun root(): Path = tmp.root.toPath().toRealPath()

    private fun flatten(document: Path) = CarveConverter.toFlattenedCarve(
        Files.readString(document),
        root(),
        document.toRealPath().toString(),
    )

    @Test
    fun theChildIsMergedInAndTheDirectiveIsGone() {
        write("shared/glossary.crv", "Glossary *body*.\n")
        val doc = write("chapters/ch1.crv", "# Chapter\n\n{{ ../shared/glossary.crv }}\n")

        val result = flatten(doc)
        assertNotNull("flatten returned nothing", result)
        assertTrue("the child is missing: ${result!!.carve}", result.carve.contains("Glossary *body*."))
        assertFalse("the directive survived: ${result.carve}", result.carve.contains("{{"))
    }

    /**
     * The output is SOURCE, not a render. A flattened file someone goes on
     * editing must not carry render-time enrichment, which is why the engine
     * routes this through `renderCarve` rather than the render seam.
     */
    @Test
    fun theOutputIsCarveSourceRatherThanRenderedOutput() {
        write("shared/glossary.crv", "Glossary *body*.\n")
        val doc = write("chapters/ch1.crv", "# Chapter\n\n{{ ../shared/glossary.crv }}\n")

        val carve = flatten(doc)!!.carve
        assertFalse("rendered HTML reached the flattened source: $carve", carve.contains("<p>"))
        assertTrue(carve, carve.contains("# Chapter"))
    }

    @Test
    fun aTargetItCouldNotReadIsNamedRatherThanDroppedQuietly() {
        val doc = write("chapters/ch1.crv", "{{ ../shared/absent.crv }}\n")

        val result = flatten(doc)!!
        assertEquals(listOf(false), result.dependencies.map { it.resolved })
        val summary = CarveFlattenReport.summary(result.warnings, result.dependencies)
        assertTrue(summary, summary.contains("Could not read 1 target(s)"))
    }

    @Test
    fun theSummarySaysFormattingIsNormalized() {
        write("shared/glossary.crv", "Glossary.\n")
        val doc = write("chapters/ch1.crv", "{{ ../shared/glossary.crv }}\n")

        val result = flatten(doc)!!
        val summary = CarveFlattenReport.summary(result.warnings, result.dependencies)
        assertTrue(summary, summary.contains("Merged 1 included file."))
        assertTrue(summary, summary.contains("Formatting is normalized, not preserved."))
    }

    /**
     * I5 renames a colliding explicit id, so a flattened document can carry
     * `intro-2`. Reported rather than left to be found in a published page.
     */
    @Test
    fun aRenamedCollisionIsCounted() {
        val renames = listOf(
            CarveIncludeExpansion.Warning("include-heading-id-rename", "renamed", 1, 1),
            CarveIncludeExpansion.Warning("include-footnote-rename", "renamed", 2, 1),
            CarveIncludeExpansion.Warning("include-unresolved", "not a rename", 3, 1),
        )
        assertEquals(2, CarveFlattenReport.renameCount(renames))
        assertTrue(
            CarveFlattenReport.summary(renames, emptyList())
                .contains("2 colliding id or footnote label renamed"),
        )
    }

    @Test
    fun aCleanFlattenSaysNothingAboutRenamesOrUnreadTargets() {
        val summary = CarveFlattenReport.summary(
            emptyList(),
            listOf(CarveIncludeExpansion.Dependency("/a.crv", true, null)),
        )
        assertFalse(summary, summary.contains("renamed"))
        assertFalse(summary, summary.contains("Could not read"))
    }

    /**
     * One copy, two destinations: a Carve editor takes the typed flavor, a web
     * box takes plain text, and neither has to be asked which it wanted.
     */
    @Test
    fun theClipboardCarriesTheCarveTypeAndPlainTextAtOnce() {
        val transferable = CarveTransferable("# Chapter\n")

        assertEquals(
            listOf("text/x-carve", DataFlavor.stringFlavor.mimeType.substringBefore(';')),
            transferable.transferDataFlavors.map { it.mimeType.substringBefore(';') },
        )
        assertEquals("# Chapter\n", transferable.getTransferData(CarveTransferable.CARVE_FLAVOR))
        assertEquals("# Chapter\n", transferable.getTransferData(DataFlavor.stringFlavor))
    }

    /** The settled spelling (markup-carve/carve#2050). */
    @Test
    fun theCarveFlavorIsTextXCarve() {
        assertEquals("text/x-carve", CarveTransferable.CARVE_FLAVOR.mimeType.substringBefore(';'))
        assertEquals(String::class.java, CarveTransferable.CARVE_FLAVOR.representationClass)
    }

    @Test
    fun anUnsupportedFlavorIsRefused() {
        val transferable = CarveTransferable("x")
        assertFalse(transferable.isDataFlavorSupported(DataFlavor.imageFlavor))
    }
}
