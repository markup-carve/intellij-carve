package org.markupcarve.carve.corpus

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Enforces the coverage matrix (item #4): every shared-corpus category must be
 * explicitly classified as COVERED or SKIP. A new spec category - added upstream
 * in markup-carve/carve and pulled in via a submodule bump - fails this test
 * until someone makes a deliberate decision, so highlighter coverage can never
 * silently drift behind the spec.
 */
class CarveCorpusCoverageMatrixTest {

    @Test
    fun corpusIsCheckedOut() {
        assertNotNull(CarveCorpus.MISSING_MESSAGE, CarveCorpus.directory)
        assertTrue("Corpus directory is empty: ${CarveCorpus.MISSING_MESSAGE}", CarveCorpus.crvFiles().isNotEmpty())
    }

    @Test
    fun everyCorpusCategoryIsClassified() {
        val classified = CarveCorpusCategories.COVERED + CarveCorpusCategories.SKIP.keys
        val unclassified = CarveCorpus.categories().filter { it !in classified }
        if (unclassified.isNotEmpty()) {
            fail(
                "Unclassified shared-corpus categories (decide COVERED vs SKIP in CarveCorpusCategories):\n" +
                    unclassified.joinToString("\n") { "  - $it" },
            )
        }
    }

    @Test
    fun classificationDoesNotReferenceMissingCategories() {
        val live = CarveCorpus.categories().toSet()
        val classified = CarveCorpusCategories.COVERED + CarveCorpusCategories.SKIP.keys
        val stale = classified.filter { it !in live }
        if (stale.isNotEmpty()) {
            fail(
                "Categories classified in CarveCorpusCategories but absent from the live corpus " +
                    "(remove or rename after a submodule bump):\n" +
                    stale.joinToString("\n") { "  - $it" },
            )
        }
    }

    @Test
    fun coveredAndSkipAreDisjoint() {
        val overlap = CarveCorpusCategories.COVERED.intersect(CarveCorpusCategories.SKIP.keys)
        assertTrue("A category must be either COVERED or SKIP, not both: $overlap", overlap.isEmpty())
    }

    /**
     * A golden with no corpus file behind it is dead weight: nothing reads it,
     * so nothing notices when the construct it pinned stops being highlighted.
     * Three were left behind by the bump that renamed `emoji` to `symbols` and
     * `link-destination-stops-at-the-first-parenthesis` to
     * `link-destination-parentheses-balance` - the snapshot test only fails on
     * goldens it LOOKS for, so a renamed category silently orphans its own.
     */
    @Test
    fun noGoldenIsOrphaned() {
        val live = CarveCorpus.crvFiles().map { CarveCorpusCategories.slugOf(it.name) }.toSet()
        val goldens = CarveCorpusSnapshotTest.goldensDirectory
        if (!goldens.isDirectory) return
        val orphaned = goldens.listFiles { f: java.io.File -> f.name.endsWith(".tokens") }
            .orEmpty()
            .map { it.name.removeSuffix(".tokens") }
            .filter { it !in live }
            .sorted()
        if (orphaned.isNotEmpty()) {
            fail(
                "Golden token files with no corpus file behind them (delete after a submodule " +
                    "bump renames or removes a category):\n" +
                    orphaned.joinToString("\n") { "  - $it.tokens" },
            )
        }
    }

    @Test
    fun everySkipHasAReason() {
        val blank = CarveCorpusCategories.SKIP.filterValues { it.isBlank() }.keys
        assertTrue("Every SKIP category needs a reason; blank: $blank", blank.isEmpty())
    }
}
