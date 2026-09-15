package org.markupcarve.carve.includes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Path

/**
 * The bundle layout, driven with a walk built by hand.
 *
 * The walk itself is measured against the real server in
 * [CarveIncludeWalkTest]; what is asked here is what the bundle DOES with a
 * walk - which files it writes, where it puts them, and what it says about the
 * ones it could not read.
 */
class CarveBundleTest {

    private val root: Path = Path.of("/book")
    private val document: Path = Path.of("/book/chapters/ch1.crv")

    private fun bundleOf(
        documents: List<WalkedDocument> = emptyList(),
        dependencies: List<WalkedDependency> = emptyList(),
        source: String = "# Chapter\n",
    ): Bundle = CarveBundle.build(
        BundleInput(
            documentPath = document,
            source = source,
            includeRoot = root,
            walk = IncludeWalk(documents, dependencies),
        ),
    )

    @Test
    fun laysFilesOutRelativeToTheIncludeRootNotTheDocument() {
        // The document is in chapters/, the glossary is in shared/. Laid out
        // relative to the DOCUMENT the glossary would need a `..` and land
        // outside the bundle; relative to the root both keep the spelling the
        // directives already use, so `{{ ../shared/glossary.crv }}` still
        // resolves inside the copy.
        val bundle = bundleOf(documents = listOf(WalkedDocument("/book/shared/glossary.crv", "Glossary.\n")))
        assertEquals(
            listOf("chapters/ch1.crv", "shared/glossary.crv"),
            bundle.files.map { it.path },
        )
    }

    @Test
    fun putsTheDocumentItselfInTheBundle() {
        assertEquals(
            listOf(BundleFile("chapters/ch1.crv", "# Chapter\n")),
            bundleOf().files,
        )
    }

    @Test
    fun copiesEachChildSourceVerbatim() {
        val bundle = bundleOf(documents = listOf(WalkedDocument("/book/shared/glossary.crv", "Glossary.\n")))
        assertEquals("Glossary.\n", bundle.files.single { it.path == "shared/glossary.crv" }.source)
    }

    @Test
    fun namesWhatItCouldNotRead() {
        val bundle = bundleOf(
            dependencies = listOf(
                WalkedDependency("/book/shared/glossary.crv", true),
                WalkedDependency("missing.crv", false),
            ),
        )
        assertEquals(listOf("missing.crv"), bundle.missing)
    }

    @Test
    fun dropsATargetOutsideTheRootRatherThanWritingItOutOfTheBundle() {
        // The walk cannot hand one back - containment is the server's job. If
        // it ever did, a `..` in the relative path would write the file OUTSIDE
        // the bundle directory, which is a write primitive rather than a layout
        // bug.
        val bundle = bundleOf(documents = listOf(WalkedDocument("/etc/passwd", "root:x:0:0\n")))
        assertEquals(listOf("chapters/ch1.crv"), bundle.files.map { it.path })
    }

    @Test
    fun writesOneEntryForAFileReachedTwice() {
        val bundle = bundleOf(
            documents = listOf(
                WalkedDocument("/book/shared/glossary.crv", "Glossary.\n"),
                WalkedDocument("/book/shared/glossary.crv", "Glossary.\n"),
            ),
        )
        assertEquals(listOf("chapters/ch1.crv", "shared/glossary.crv"), bundle.files.map { it.path })
    }

    @Test
    fun theDocumentIsNotWrittenTwiceWhenItIsAlsoIncluded() {
        val bundle = bundleOf(documents = listOf(WalkedDocument("/book/chapters/ch1.crv", "# Chapter\n")))
        assertEquals(1, bundle.files.size)
    }

    @Test
    fun bundlePathRefusesATargetAboveTheRoot() {
        assertNull(CarveBundle.bundlePath(root, Path.of("/etc/passwd")))
        assertNull(CarveBundle.bundlePath(root, Path.of("/book")))
    }

    @Test
    fun bundlePathUsesForwardSlashesWhateverTheHostSeparatorIs() {
        assertEquals("shared/glossary.crv", CarveBundle.bundlePath(root, Path.of("/book/shared/glossary.crv")))
    }

    @Test
    fun takesTheFirstFreeDirectoryBesideTheDocument() {
        assertEquals(
            Path.of("/book/chapters/ch1.bundle"),
            CarveBundle.directoryFor(document) { true },
        )
    }

    @Test
    fun numbersTheDirectoryWhenTheFirstNameIsTaken() {
        assertEquals(
            Path.of("/book/chapters/ch1.bundle-2"),
            CarveBundle.directoryFor(document) { it.fileName.toString() != "ch1.bundle" },
        )
    }

    @Test
    fun givesUpRatherThanOverwritingWhenEveryNameIsTaken() {
        assertNull(CarveBundle.directoryFor(document) { false })
    }

    @Test
    fun claimsTheDirectoryOnceRatherThanTestingAndThenCreatingIt() {
        // The predicate is the CLAIM, so a name it hands back was taken in the
        // same step it was offered. Counting the calls is what shows the
        // candidate loop asks to reserve rather than asking whether the path
        // is free: a claim that fails moves on, it does not retry.
        val claimed = mutableListOf<Path>()
        val directory = CarveBundle.directoryFor(document) { candidate ->
            claimed.add(candidate)
            claimed.size == 2
        }
        assertEquals(Path.of("/book/chapters/ch1.bundle-2"), directory)
        assertEquals(2, claimed.size)
    }

    @Test
    fun theSummaryCountsFilesAndStaysSilentWhenNothingWasMissed() {
        assertEquals(
            "Bundled 2 files into ch1.bundle.",
            CarveBundle.summary(
                Bundle(listOf(BundleFile("a", ""), BundleFile("b", "")), emptyList()),
                "ch1.bundle",
            ),
        )
    }

    @Test
    fun theSummaryNamesWhatCouldNotBeRead() {
        assertEquals(
            "Bundled 1 file into ch1.bundle. Could not read: a.crv, b.crv, c.crv and 1 more.",
            CarveBundle.summary(
                Bundle(listOf(BundleFile("a", "")), listOf("a.crv", "b.crv", "c.crv", "d.crv")),
                "ch1.bundle",
            ),
        )
    }
}
