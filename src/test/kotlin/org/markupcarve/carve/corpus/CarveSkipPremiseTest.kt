package org.markupcarve.carve.corpus

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Checks the premise every SKIP reason rests on.
 *
 * A SKIP says the category produces no scope that is not already snapshotted
 * somewhere else, and until now nothing re-asked that. A reason no check reads
 * goes stale silently: a sibling repo found four of sixteen false when someone
 * finally measured them.
 *
 * So the claim is measured. Tokenize every SKIP category and require each scope
 * it produces to appear in a COVERED corpus document or a committed fixture.
 * Fixtures count because they are the hand-authored home for a construct the
 * corpus cannot spell in one document - no corpus file carries both a single-
 * and a double-quoted attribute value.
 */
class CarveSkipPremiseTest {

    @Test
    fun noScopeLivesOnlyInASkippedCategory() {
        assumeTrue(CarveCorpus.MISSING_MESSAGE, CarveCorpus.directory != null)

        val snapshotted = HashSet<String>()
        for (file in CarveCorpus.crvFiles()) {
            if (CarveCorpusCategories.categoryOf(file.name) in CarveCorpusCategories.COVERED) {
                snapshotted += scopesOf(file)
            }
        }
        val fixtures = File(CarveCorpusSnapshotTest.goldensDirectory.parentFile, "fixtures")
        assertTrue("Fixture directory missing: ${fixtures.path}", fixtures.isDirectory)
        for (file in fixtures.listFiles { f: File -> f.name.endsWith(".crv") }.orEmpty()) {
            snapshotted += scopesOf(file)
        }

        val byCategory = CarveCorpus.crvFiles().groupBy { CarveCorpusCategories.categoryOf(it.name) }
        val orphans = CarveCorpusCategories.SKIP.keys.mapNotNull { category ->
            val only = byCategory[category].orEmpty()
                .flatMap { scopesOf(it) }
                .filter { it !in snapshotted }
                .distinct()
                .sorted()
            if (only.isEmpty()) null else "$category: ${only.joinToString(", ")}"
        }

        assertTrue(
            "These SKIP categories produce scopes that no COVERED document and no fixture " +
                "produces, so the scope lives in the grammar and in no snapshot. Either cover " +
                "the category or add a fixture that exercises the scope:\n" +
                orphans.joinToString("\n") { "  - $it" },
            orphans.isEmpty(),
        )
    }

    private fun scopesOf(file: File): Set<String> =
        CarveTextMateTokenizer.tokenize(file.readText())
            .flatMap { it.scope.split(' ') }
            .filter { it.isNotBlank() && it != CarveTextMateTokenizer.ROOT_SCOPE }
            .map { normalize(it) }
            .toSet()

    /**
     * Folds a fenced body's embedded-language scope down to its family.
     *
     * The question this test asks is whether a SKIP category produces a KIND of
     * scope nothing snapshots. Which language a corpus author happened to write
     * after the fence is not such a kind: the covered goldens already hold seven
     * members of `meta.embedded.block.*`, and `two-blank-lines-detach-a-caption`
     * was flagged only because one of its documents fences `lua`. Covering that
     * whole category to snapshot a language name would pin nothing about the
     * caption rule the category exists for.
     *
     * The narrowing does not blunt the check: it still rejected the two entries
     * promoted in [CarveCorpusCategories] for producing genuinely unsnapshotted
     * Carve scopes.
     */
    private fun normalize(scope: String): String =
        if (scope.startsWith("meta.embedded.block.")) "meta.embedded.block.*" else scope
}
