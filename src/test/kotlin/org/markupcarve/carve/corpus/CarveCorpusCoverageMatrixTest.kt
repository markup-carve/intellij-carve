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

    @Test
    fun everySkipHasAReason() {
        val blank = CarveCorpusCategories.SKIP.filterValues { it.isBlank() }.keys
        assertTrue("Every SKIP category needs a reason; blank: $blank", blank.isEmpty())
    }
}
