package org.markupcarve.carve.includes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * The containment root, which is a security boundary rather than a
 * convenience: it decides which files a document may read.
 *
 * Real directories, not string arithmetic. The root is canonicalized before it
 * is compared against anything, so a fixture of invented paths would agree with
 * an implementation that skipped canonicalization and disagree with the server,
 * which does not.
 */
class CarveIncludeRootTest {

    private fun project(): Path {
        val base = Files.createTempDirectory("carve-root").toRealPath()
        Files.createDirectories(base.resolve("proj/chapters"))
        Files.createDirectories(base.resolve("proj/shared"))
        Files.createDirectories(base.resolve("outside"))
        Files.writeString(base.resolve("proj/chapters/ch1.crv"), "# Chapter\n")
        return base
    }

    @Test
    fun usesTheProjectBaseDirectoryThatHoldsTheDocument() {
        val base = project()
        assertEquals(
            base.resolve("proj"),
            CarveIncludeRoot.of("", listOf(base.resolve("proj")), base.resolve("proj/chapters/ch1.crv")),
        )
    }

    @Test
    fun theProjectRootRatherThanTheDocumentsFolderIsWhatMakesASiblingFolderReachable() {
        // The root is what `{{ ../shared/glossary.crv }}` is measured against.
        // Rooting at the document's own folder would refuse a normal book
        // layout; the assertion is that the root is the ANCESTOR, not the
        // parent.
        val base = project()
        val root = CarveIncludeRoot.of("", listOf(base.resolve("proj")), base.resolve("proj/chapters/ch1.crv"))
        assertEquals(base.resolve("proj/shared"), root?.resolve("shared"))
    }

    @Test
    fun anExplicitSettingWinsOverTheProject() {
        val base = project()
        assertEquals(
            base.resolve("proj/chapters"),
            CarveIncludeRoot.of(
                base.resolve("proj/chapters").toString(),
                listOf(base.resolve("proj")),
                base.resolve("proj/chapters/ch1.crv"),
            ),
        )
    }

    @Test
    fun aRelativeSettingIsRefusedRatherThanResolved() {
        // Resolving "." means resolving it against the process working
        // directory, which spec PART 9 section 19 names as the one root a host
        // must never use. Refusing is the only reading that cannot land there.
        val base = project()
        assertNull(CarveIncludeRoot.of(".", listOf(base.resolve("proj")), base.resolve("proj/chapters/ch1.crv")))
        assertNull(CarveIncludeRoot.of("..", listOf(base.resolve("proj")), base.resolve("proj/chapters/ch1.crv")))
        assertNull(
            CarveIncludeRoot.of("shared", listOf(base.resolve("proj")), base.resolve("proj/chapters/ch1.crv")),
        )
    }

    @Test
    fun aConfiguredRootThatDoesNotHoldTheDocumentIsRefused() {
        // Such a root resolves nothing - the server resolves a relative target
        // against the document's own folder and then contains it - and it is
        // worse than inert downstream: an export laid out relative to the root
        // has no place for the document itself, so the document drops out of
        // its own bundle and the run still reports success.
        val base = project()
        assertNull(
            CarveIncludeRoot.of(
                base.resolve("outside").toString(),
                listOf(base.resolve("proj")),
                base.resolve("proj/chapters/ch1.crv"),
            ),
        )
    }

    @Test
    fun aBlankSettingMeansUnsetAndFallsThroughRatherThanBecomingTheWorkingDirectory() {
        val base = project()
        assertEquals(
            base.resolve("proj"),
            CarveIncludeRoot.of("   ", listOf(base.resolve("proj")), base.resolve("proj/chapters/ch1.crv")),
        )
    }

    @Test
    fun aDocumentWithNoProjectBehindItGetsItsOwnFolder() {
        val base = project()
        assertEquals(
            base.resolve("proj/chapters"),
            CarveIncludeRoot.of("", emptyList(), base.resolve("proj/chapters/ch1.crv")),
        )
    }

    @Test
    fun aBaseDirectoryThatDoesNotHoldTheDocumentIsNotTheRoot() {
        val base = project()
        assertEquals(
            base.resolve("proj/chapters"),
            CarveIncludeRoot.of("", listOf(base.resolve("outside")), base.resolve("proj/chapters/ch1.crv")),
        )
    }

    @Test
    fun theDeepestContainingBaseDirectoryWins() {
        // Several base directories can nest. A security boundary takes the
        // tighter reading, so a nested module roots its own documents rather
        // than inheriting the outer project's reach.
        val base = project()
        assertEquals(
            base.resolve("proj/chapters"),
            CarveIncludeRoot.of(
                "",
                listOf(base.resolve("proj"), base.resolve("proj/chapters")),
                base.resolve("proj/chapters/ch1.crv"),
            ),
        )
    }
}
