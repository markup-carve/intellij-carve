package org.markupcarve.carve.corpus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The complete 0.2 render population must reach the bundle and token tests. */
class CarveCorpusCoverageMatrixTest {
    @Test
    fun conformanceSourceIsCheckedOut() {
        assertNotNull(CarveCorpus.MISSING_MESSAGE, CarveCorpus.directory)
        assertTrue("0.2 render corpus is empty", CarveCorpus.examples().isNotEmpty())
    }

    @Test
    fun everyApplicableExampleHasOneTokenSnapshot() {
        val examples = CarveCorpus.examples()
        val ids = examples.map { it.id }
        assertEquals("Duplicate 0.2 example ID", ids.size, ids.toSet().size)
        val goldens = CarveCorpusSnapshotTest.goldensDirectory
        val actual = goldens.listFiles { file -> file.isFile && file.name.endsWith(".tokens") }
            .orEmpty().map { it.name.removeSuffix(".tokens") }.sorted()
        assertEquals("0.2 token snapshots differ from the pinned example population", ids.sorted(), actual)
    }

    @Test
    fun vendoredRenderExamplesMatchThePinnedSpec() {
        val process = ProcessBuilder("node", "tools/vendor-conformance.mjs", "spec", "--check")
            .redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        assertEquals("Vendored 0.2 examples differ from the pinned spec:\n$output", 0, process.waitFor())
    }
}
