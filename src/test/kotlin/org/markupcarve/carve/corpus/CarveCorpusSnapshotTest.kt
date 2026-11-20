package org.markupcarve.carve.corpus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/** Token snapshots for every option-free render example in the pinned 0.2 spec. */
@RunWith(Parameterized::class)
class CarveCorpusSnapshotTest(
    private val id: String,
    private val input: String,
) {
    @Test
    fun tokenStreamMatchesGolden() {
        val actual = CarveTextMateTokenizer.snapshot(input)
        val golden = File(goldensDirectory, "$id.tokens")
        if (updateGoldens) {
            golden.parentFile.mkdirs()
            golden.writeText(actual)
            return
        }
        assertTrue("Missing 0.2 token snapshot for $id at ${golden.path}", golden.isFile)
        assertEquals("Token stream changed for 0.2 example $id", golden.readText(), actual)
    }

    companion object {
        private val updateGoldens = System.getProperty("carve.updateGoldens", "false").toBoolean()
        val goldensDirectory: File get() = File("src/test/resources/conformance-tokens")

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): Collection<Array<Any>> = CarveCorpus.examples()
            .map { arrayOf<Any>(it.id, it.input) }
    }
}
